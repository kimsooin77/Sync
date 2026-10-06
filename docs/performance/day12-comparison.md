# Day 12 Before / After comparison

The frozen Day 11 per-employee path and production bulk-lookup path ran in the same `performanceTest` invocation, Spring context, PostgreSQL 17.11 Testcontainer, and fixture protocol. Raw samples are in `day12-before.json` and `day12-after.json`; this report is computed from those in-memory samples and does not read any prior output.

| Scenario | Before median (ms) | After median (ms) | Time change | Before prepared SQL | After prepared SQL | Before Employee SELECT (individual / bulk) | After Employee SELECT (individual / bulk) |
|---|---:|---:|---:|---:|---:|---:|---:|
|A|27172.516|18332.157|32.534%|20007|10018|10000 / 0|0 / 10|
|B|29770.774|21988.009|26.142%|23007|14018|10000 / 0|1000 / 10|

Positive time change means the measured median was lower after bulk lookup. Timing is descriptive only; no performance threshold is asserted.
