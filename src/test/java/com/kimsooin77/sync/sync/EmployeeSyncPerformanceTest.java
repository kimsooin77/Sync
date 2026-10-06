package com.kimsooin77.sync.sync;

import com.kimsooin77.sync.audit.AuditLogRepository;
import com.kimsooin77.sync.employee.EmploymentStatus;
import com.kimsooin77.sync.employee.PostgreSqlTestConfiguration;
import com.kimsooin77.sync.integration.IntegrationTaskRepository;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("performance")
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.kimsooin77.sync.sync.PerformanceSqlInspector",
        "app.integration.worker.enabled=false",
        "logging.level.org.hibernate.SQL=OFF"
})
@Import(PostgreSqlTestConfiguration.class)
class EmployeeSyncPerformanceTest {

    private static final int EMPLOYEE_COUNT = 10_000;
    private static final int REPETITIONS = 3;
    private static final String LEGACY_BASELINE_COMMIT = "3d3691af8c2fa8aa640ef35e3a495ac6a43e6507";
    private static final Pattern INDIVIDUAL_EMPLOYEE_LOOKUP = Pattern.compile(
            "(?is)\\bfrom\\s+employee\\b.*\\bwhere\\b.*\\bemployee_no\\s*=\\s*\\?");
    private static final Pattern BULK_EMPLOYEE_LOOKUP = Pattern.compile(
            "(?is)\\bfrom\\s+employee\\b.*\\bwhere\\b.*\\bemployee_no\\s+in\\s*\\(");

    @Autowired private EmployeeSyncService employeeSyncService;
    @Autowired private EmployeeBatchNormalizer normalizer;
    @Autowired private EmployeeSyncTransactionService employeeTransactions;
    @Autowired private SyncJobTransactionService jobTransactions;
    @Autowired private SyncExecutionGuard guard;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private SyncItemRepository syncItemRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private IntegrationTaskRepository integrationTaskRepository;
    @Autowired private jakarta.persistence.EntityManagerFactory entityManagerFactory;

    @Test
    void benchmarksFrozenLegacyAndBulkImplementationsTogetherAndWritesAllReports() throws IOException {
        LegacyEmployeeSyncRunner legacy = new LegacyEmployeeSyncRunner(normalizer, employeeTransactions, jobTransactions, guard);
        List<ScenarioResult> before = new ArrayList<>();
        List<ScenarioResult> after = new ArrayList<>();

        for (String scenario : List.of("A", "B")) {
            runScenario(scenario, legacy::synchronize, false); // warm-up, excluded
            runScenario(scenario, employeeSyncService::synchronize, true); // warm-up, excluded
            List<Sample> beforeSamples = new ArrayList<>();
            List<Sample> afterSamples = new ArrayList<>();
            for (int repetition = 1; repetition <= REPETITIONS; repetition++) {
                // Alternate measured order to reduce systematic ordering bias.
                if (repetition % 2 == 1) {
                    beforeSamples.add(runScenario(scenario, legacy::synchronize, false));
                    afterSamples.add(runScenario(scenario, employeeSyncService::synchronize, true));
                } else {
                    afterSamples.add(runScenario(scenario, employeeSyncService::synchronize, true));
                    beforeSamples.add(runScenario(scenario, legacy::synchronize, false));
                }
            }
            before.add(scenarioResult(scenario, beforeSamples));
            after.add(scenarioResult(scenario, afterSamples));
        }
        writeReports(before, after);
    }

    private Sample runScenario(String scenario, Runner runner, boolean bulk) {
        resetDatabase();
        seedExistingEmployees();
        List<HrEmployeeResponse> rows = scenarioRows(scenario);
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        PerformanceSqlInspector.clear();

        long started = System.nanoTime();
        SyncJobResult result = runner.run(rows);
        double elapsedMillis = (System.nanoTime() - started) / 1_000_000.0;

        long statementCount = statistics.getPrepareStatementCount();
        List<String> statements = PerformanceSqlInspector.snapshot();
        long individualEmployeeSelects = statements.stream()
                .filter(sql -> INDIVIDUAL_EMPLOYEE_LOOKUP.matcher(sql).find()).count();
        long bulkEmployeeSelects = statements.stream()
                .filter(sql -> BULK_EMPLOYEE_LOOKUP.matcher(sql).find()).count();
        long syncItemCount = syncItemRepository.count();
        long auditLogCount = auditLogRepository.count();
        long taskCount = integrationTaskRepository.count();

        assertThat(result.status()).isEqualTo(SyncJobStatus.COMPLETED);
        assertThat(result.totalCount()).isEqualTo(EMPLOYEE_COUNT);
        assertThat(result.insertedCount()).isZero();
        assertThat(result.failedCount()).isZero();
        assertThat(result.updatedCount()).isEqualTo(scenario.equals("B") ? 1_000 : 0);
        assertThat(result.skippedCount()).isEqualTo(scenario.equals("A") ? 10_000 : 9_000);
        assertThat(syncItemCount).isEqualTo(10_000);
        assertThat(auditLogCount).isEqualTo(scenario.equals("B") ? 1_000 : 0);
        assertThat(taskCount).isEqualTo(scenario.equals("B") ? 1_000 : 0);
        return new Sample(elapsedMillis, statementCount, individualEmployeeSelects, bulkEmployeeSelects,
                result.insertedCount(), result.updatedCount(), result.skippedCount(), result.failedCount(),
                syncItemCount, auditLogCount, taskCount);
    }

    private List<HrEmployeeResponse> scenarioRows(String scenario) {
        List<HrEmployeeResponse> rows = new ArrayList<>(EMPLOYEE_COUNT);
        for (int index = 1; index <= EMPLOYEE_COUNT; index++) {
            String employeeNo = employeeNo(index);
            String department = scenario.equals("B") && index % 10 == 0 ? "D0099" : "D0001";
            rows.add(new HrEmployeeResponse(employeeNo, "Employee " + String.format(Locale.ROOT, "%05d", index),
                    String.format(Locale.ROOT, "employee%05d@example.com", index), department, "ACTIVE"));
        }
        return rows;
    }

    private void resetDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE integration_attempt, integration_task, audit_log, sync_item, employee, sync_job RESTART IDENTITY CASCADE");
    }

    private void seedExistingEmployees() {
        Instant now = Instant.now();
        jdbcTemplate.batchUpdate("""
                INSERT INTO employee (employee_no, name, company_email, department_code, employment_status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, Stream.iterate(1, index -> index + 1).limit(EMPLOYEE_COUNT).toList(), 500,
                (PreparedStatement statement, Integer index) -> {
                    statement.setString(1, employeeNo(index));
                    statement.setString(2, "Employee " + String.format(Locale.ROOT, "%05d", index));
                    statement.setString(3, String.format(Locale.ROOT, "employee%05d@example.com", index));
                    statement.setString(4, "D0001");
                    statement.setString(5, EmploymentStatus.ACTIVE.name());
                    statement.setTimestamp(6, Timestamp.from(now));
                    statement.setTimestamp(7, Timestamp.from(now));
                });
    }

    private void writeReports(List<ScenarioResult> before, List<ScenarioResult> after) throws IOException {
        Path output = Path.of(System.getProperty("day12.performance.output-dir", "build/reports/performance"));
        Files.createDirectories(output);
        String javaVersion = Runtime.version().toString();
        String postgresVersion = jdbcTemplate.queryForObject("SELECT version()", String.class);
        String currentCommit = gitHead();
        Files.writeString(output.resolve("before.json"), json("frozen-legacy-before", LEGACY_BASELINE_COMMIT,
                javaVersion, postgresVersion, before), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("after.json"), json("bulk-after", currentCommit,
                javaVersion, postgresVersion, after), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("before.md"), markdown("Frozen legacy baseline", LEGACY_BASELINE_COMMIT,
                javaVersion, postgresVersion, before), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("after.md"), markdown("Bulk lookup", currentCommit,
                javaVersion, postgresVersion, after), StandardCharsets.UTF_8);
        Files.writeString(output.resolve("comparison.md"), comparison(before, after), StandardCharsets.UTF_8);
        System.out.println("Paired Day 12 performance results written to " + output.toAbsolutePath());
    }

    private static ScenarioResult scenarioResult(String name, List<Sample> samples) {
        double[] elapsed = samples.stream().mapToDouble(Sample::elapsedMillis).sorted().toArray();
        return new ScenarioResult(name, samples, elapsed[elapsed.length / 2]);
    }

    private static String json(String label, String commit, String javaVersion, String postgresVersion,
                               List<ScenarioResult> results) {
        StringBuilder json = new StringBuilder("{\n  \"implementation\": \"").append(label)
                .append("\",\n  \"gitCommit\": \"").append(commit)
                .append("\",\n  \"javaVersion\": \"").append(escape(javaVersion))
                .append("\",\n  \"postgresVersion\": \"").append(escape(postgresVersion))
                .append("\",\n  \"protocol\": \"same performanceTest invocation/context; one warm-up per scenario/implementation; three measured repetitions; scenario fixture/reset outside timed interval; order alternated per repetition\"")
                .append(",\n  \"scenarios\": [\n");
        for (int i = 0; i < results.size(); i++) {
            ScenarioResult scenario = results.get(i);
            json.append("    {\"scenario\": \"").append(scenario.name()).append("\", \"medianMillis\": ")
                    .append(format(scenario.medianMillis())).append(", \"samples\": [");
            for (int j = 0; j < scenario.samples().size(); j++) {
                Sample sample = scenario.samples().get(j);
                if (j > 0) json.append(", ");
                json.append("{\"elapsedMillis\": ").append(format(sample.elapsedMillis()))
                        .append(", \"preparedStatements\": ").append(sample.preparedStatements())
                        .append(", \"employeeIndividualSelects\": ").append(sample.employeeIndividualSelects())
                        .append(", \"employeeBulkSelects\": ").append(sample.employeeBulkSelects())
                        .append(", \"inserted\": ").append(sample.inserted())
                        .append(", \"updated\": ").append(sample.updated())
                        .append(", \"skipped\": ").append(sample.skipped())
                        .append(", \"failed\": ").append(sample.failed())
                        .append(", \"syncItems\": ").append(sample.syncItems())
                        .append(", \"auditLogs\": ").append(sample.auditLogs())
                        .append(", \"integrationTasks\": ").append(sample.integrationTasks()).append('}');
            }
            json.append("]}").append(i + 1 == results.size() ? "\n" : ",\n");
        }
        return json.append("  ]\n}\n").toString();
    }

    private static String markdown(String heading, String commit, String javaVersion, String postgresVersion,
                                  List<ScenarioResult> results) {
        StringBuilder md = new StringBuilder("# Day 12 ").append(heading).append(" measurements\n\n")
                .append("- Baseline/source commit: `").append(commit).append("`\n")
                .append("- Java: `").append(javaVersion).append("`\n")
                .append("- PostgreSQL: `").append(postgresVersion).append("`\n")
                .append("- Conditions: PostgreSQL 17.11 Testcontainers, same Spring context, worker disabled, one warm-up plus three measured runs per scenario and implementation. Run order alternates.\n")
                .append("- Timed interval: service invocation through SyncJob completion transaction; fixture setup, truncation, and verification queries are excluded.\n")
                .append("- SQL: Hibernate prepared-statement statistics; StatementInspector counts Employee lookups by `employee_no = ?` and `employee_no IN (...)`.\n\n")
                .append("| Scenario | Samples (ms) | Median (ms) | Prepared statements | Employee individual SELECT | Employee bulk SELECT | INSERT | UPDATE | SKIP | FAILED | SyncItem | AuditLog | IntegrationTask |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (ScenarioResult scenario : results) {
            md.append('|').append(scenario.name()).append('|')
                    .append(scenario.samples().stream().map(s -> format(s.elapsedMillis())).toList()).append('|')
                    .append(format(scenario.medianMillis())).append('|')
                    .append(avg(scenario, Sample::preparedStatements)).append('|')
                    .append(avg(scenario, Sample::employeeIndividualSelects)).append('|')
                    .append(avg(scenario, Sample::employeeBulkSelects)).append('|')
                    .append(avg(scenario, Sample::inserted)).append('|').append(avg(scenario, Sample::updated)).append('|')
                    .append(avg(scenario, Sample::skipped)).append('|').append(avg(scenario, Sample::failed)).append('|')
                    .append(avg(scenario, Sample::syncItems)).append('|').append(avg(scenario, Sample::auditLogs)).append('|')
                    .append(avg(scenario, Sample::integrationTasks)).append("|\n");
        }
        return md.toString();
    }

    private static String comparison(List<ScenarioResult> before, List<ScenarioResult> after) {
        StringBuilder md = new StringBuilder("# Day 12 Before / After comparison\n\n")
                .append("The frozen Day 11 per-employee path and production bulk-lookup path ran in the same `performanceTest` invocation, Spring context, PostgreSQL 17.11 Testcontainer, and fixture protocol. Raw samples are in `before.json` and `after.json`; this report is computed from those in-memory samples and does not read any prior output.\n\n")
                .append("| Scenario | Before median (ms) | After median (ms) | Time change | Before prepared SQL | After prepared SQL | Before Employee SELECT (individual / bulk) | After Employee SELECT (individual / bulk) |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|---:|\n");
        for (int index = 0; index < before.size(); index++) {
            ScenarioResult old = before.get(index);
            ScenarioResult current = after.get(index);
            double timeChange = (old.medianMillis() - current.medianMillis()) * 100.0 / old.medianMillis();
            md.append('|').append(old.name()).append('|').append(format(old.medianMillis())).append('|')
                    .append(format(current.medianMillis())).append('|').append(format(timeChange)).append("%|")
                    .append(avg(old, Sample::preparedStatements)).append('|')
                    .append(avg(current, Sample::preparedStatements)).append('|')
                    .append(avg(old, Sample::employeeIndividualSelects)).append(" / ")
                    .append(avg(old, Sample::employeeBulkSelects)).append('|')
                    .append(avg(current, Sample::employeeIndividualSelects)).append(" / ")
                    .append(avg(current, Sample::employeeBulkSelects)).append("|\n");
        }
        md.append("\nPositive time change means the measured median was lower after bulk lookup. Timing is descriptive only; no performance threshold is asserted.\n");
        return md.toString();
    }

    private static String gitHead() {
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "HEAD").redirectErrorStream(true).start();
            String value = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            if (process.waitFor() != 0) return "unavailable";
            Process status = new ProcessBuilder("git", "status", "--porcelain").redirectErrorStream(true).start();
            String changes = new String(status.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            if (status.waitFor() != 0) return value + " (working tree state unavailable)";
            return changes.isEmpty() ? value + " (clean)" : value + " (working tree modified)";
        } catch (IOException | InterruptedException failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            return "unavailable";
        }
    }

    private static String employeeNo(int index) {
        return "P" + String.format(Locale.ROOT, "%05d", index);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", " ");
    }

    private static String format(double value) { return String.format(Locale.ROOT, "%.3f", value); }

    private static long avg(ScenarioResult scenario, java.util.function.ToLongFunction<Sample> extractor) {
        return Math.round(scenario.samples().stream().mapToLong(extractor).average().orElse(0));
    }

    @FunctionalInterface
    private interface Runner { SyncJobResult run(List<HrEmployeeResponse> rows); }

    private record Sample(double elapsedMillis, long preparedStatements, long employeeIndividualSelects,
                          long employeeBulkSelects, long inserted, long updated, long skipped, long failed,
                          long syncItems, long auditLogs, long integrationTasks) { }
    private record ScenarioResult(String name, List<Sample> samples, double medianMillis) { }
}
