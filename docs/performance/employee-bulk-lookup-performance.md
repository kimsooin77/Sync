# 직원 일괄 조회 성능 비교

## 개선 배경

기존 동기화 경로는 직원마다 `findByEmployeeNo()`를 실행해 10,000건 처리에서 Employee 개별 조회 10,000회가 발생했습니다. 반복 동기화의 조회 비용을 줄이기 위해 사번을 일정 단위로 일괄 조회한 뒤 Map으로 구성해 비교하도록 변경했습니다.

## 변경 내용

**Before** — 직원마다 `findByEmployeeNo(employeeNo)`를 호출합니다.

**After** — employeeNo를 최대 1,000개 단위로 조회해 `Map<employeeNo, Employee>`를 만들고 메모리에서 비교합니다. SKIP은 일괄 조회 결과를 사용합니다. INSERT/UPDATE 후보는 직원 단위 트랜잭션 안에서 최신 Employee를 다시 조회합니다.

UPDATE 후보는 직원별 `REQUIRES_NEW` 트랜잭션 안에서 최신 Employee를 기준으로 비교·저장하므로 1,000건의 개별 조회가 남습니다.

## 측정 조건

- Java 21.0.6
- PostgreSQL 17.11 Testcontainers
- 동일한 `performanceTest` 실행과 Spring Context
- 동일 fixture와 초기화 절차
- Before/After 실행 순서를 반복마다 교차
- 각 구현·시나리오 조합에서 워밍업 1회, 측정 3회
- fixture 구성 및 초기화는 측정 시간에서 제외
- 결과는 측정 3회의 중앙값

## 결과

| 시나리오 | Before (ms) | After (ms) | 감소율 |
|---|---:|---:|---:|
| 10,000건 전체 SKIP | 27,172.516 | 18,332.157 | 32.53% |
| 1,000 UPDATE + 9,000 SKIP | 29,770.774 | 21,988.009 | 26.14% |

### Employee 조회 횟수

| 시나리오 | Before 개별 | Before 일괄 | After 개별 | After 일괄 |
|---|---:|---:|---:|---:|
| 10,000건 전체 SKIP | 10,000 | 0 | 0 | 10 |
| 1,000 UPDATE + 9,000 SKIP | 10,000 | 0 | 1,000 | 10 |

PreparedStatement 수는 Hibernate Statistics의 `getPrepareStatementCount()`로 측정했습니다.

| 처리 데이터 | 기존 방식 | 일괄 조회 |
|---|---:|---:|
| 10,000건 전체 SKIP | 20,007 | 10,018 |
| 1,000 UPDATE + 9,000 SKIP | 23,007 | 14,018 |

원자료: [기존 방식 JSON](baseline.json) · [일괄 조회 JSON](bulk-lookup.json)

상세 측정 기록: [기존 방식 측정](baseline-measurements.md) · [일괄 조회 측정](bulk-lookup-measurements.md)

## 측정 범위

이번 변경은 Employee 조회 구조를 줄이는 데 초점을 맞췄습니다. SyncItem 저장, 직원별 `REQUIRES_NEW` transaction, INSERT/UPDATE 후보의 최신 Employee 재조회는 유지합니다. 전체 batch를 bulk write로 변경한 것은 아닙니다.

측정값은 Testcontainers 기반 조건에서 얻은 결과이며 실제 운영 환경의 처리 시간을 보장하지 않습니다.
