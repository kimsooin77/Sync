# Day 12 Bulk lookup measurements

- Baseline/source commit: `3d3691af8c2fa8aa640ef35e3a495ac6a43e6507 (working tree modified)`
- Java: `21.0.6+7-LTS`
- PostgreSQL: `PostgreSQL 17.11 (Debian 17.11-1.pgdg13+2) on x86_64-pc-linux-gnu, compiled by gcc (Debian 14.2.0-19) 14.2.0, 64-bit`
- Conditions: PostgreSQL 17.11 Testcontainers, same Spring context, worker disabled, one warm-up plus three measured runs per scenario and implementation. Run order alternates.
- Timed interval: service invocation through SyncJob completion transaction; fixture setup, truncation, and verification queries are excluded.
- SQL: Hibernate prepared-statement statistics; StatementInspector counts Employee lookups by `employee_no = ?` and `employee_no IN (...)`.

| Scenario | Samples (ms) | Median (ms) | Prepared statements | Employee individual SELECT | Employee bulk SELECT | INSERT | UPDATE | SKIP | FAILED | SyncItem | AuditLog | IntegrationTask |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
|A|[19665.616, 17414.587, 18332.157]|18332.157|10018|0|10|0|0|10000|0|10000|0|0|
|B|[22522.670, 21988.009, 20734.657]|21988.009|14018|1000|10|0|1000|9000|0|10000|1000|1000|
