# 기존 직원 개별 조회 측정

- Baseline/source commit: `3d3691af8c2fa8aa640ef35e3a495ac6a43e6507`
- Java: `21.0.6+7-LTS`
- PostgreSQL: `PostgreSQL 17.11 (Debian 17.11-1.pgdg13+2) on x86_64-pc-linux-gnu, compiled by gcc (Debian 14.2.0-19) 14.2.0, 64-bit`
- Conditions: PostgreSQL 17.11 Testcontainers, same Spring context, worker disabled, one warm-up plus three measured runs per scenario and implementation. Run order alternates.
- Timed interval: service invocation through SyncJob completion transaction; fixture setup, truncation, and verification queries are excluded.
- SQL: Hibernate prepared-statement statistics; StatementInspector counts Employee lookups by `employee_no = ?` and `employee_no IN (...)`.

| 처리 데이터 | Samples (ms) | Median (ms) | PreparedStatement 수 | Employee 개별 SELECT | Employee 일괄 SELECT | INSERT | UPDATE | SKIP | FAILED | SyncItem | AuditLog | IntegrationTask |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
|10,000건 전체 SKIP|[29571.733, 27172.516, 26522.986]|27172.516|20007|10000|0|0|0|10000|0|10000|0|0|
|1,000 UPDATE + 9,000 SKIP|[29770.774, 30504.213, 28344.288]|29770.774|23007|10000|0|0|1000|9000|0|10000|1000|1000|
