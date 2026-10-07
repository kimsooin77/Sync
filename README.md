# Employee Lifecycle Sync

사내 인사 시스템의 직원 정보를 동기화하기 위한 Spring Boot 애플리케이션입니다. HR API에서 전체 직원 스냅샷을 가져와 정규화하고 기존 직원 정보와 비교해 동기화 실행 및 행별 결과를 PostgreSQL에 기록합니다.

## 요구 사항

- JDK 21
- Docker Compose 지원 Docker 엔진
- 별도 Gradle 설치는 필요하지 않습니다. 프로젝트에 포함된 Gradle Wrapper를 사용합니다.

Docker 실행, production profile, 세션 cookie, health check, Swagger, 환경변수와 CI 구성은 [배포 안내](docs/deployment.md)를 참고하세요. Day 13에서 추가한 GitHub Actions workflow는 실제 저장소 push 후 실행 결과를 확인해야 합니다.

## 데이터베이스 시작

먼저 `.env.example`을 `.env`로 복사하고 `POSTGRES_PASSWORD`와 `DB_PASSWORD`에 같은 로컬 값을 지정합니다. `.env`는 Git에서 제외됩니다. 전체 앱·DB 실행 방법은 [배포 안내](docs/deployment.md)를 참고하세요.

```powershell
docker compose up -d db
```

애플리케이션은 기본적으로 `localhost:5432`의 `employee_lifecycle_sync` 데이터베이스에 연결합니다. 접속 정보는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 환경 변수로 바꿀 수 있습니다.

데이터베이스를 중지하려면 다음을 실행합니다.

```powershell
docker compose down
```

## 빌드와 실행

Windows PowerShell:

```powershell
.\gradlew.bat clean build
```

macOS 또는 Linux:

```sh
./gradlew clean build
```

일반 Gradle 빌드는 frontend dependency 설치·테스트·production build를 수행하고 React 파일을 실행 가능한 JAR에 포함합니다. `clean build`에는 backend 테스트도 포함되며 PostgreSQL 17.11 Testcontainers를 사용하므로 Docker 엔진이 실행 중이어야 합니다. 로컬 실행과 관리자 환경변수 설정은 [배포 안내](docs/deployment.md)를 참고하세요.

## HR 동기화 API

`POST /api/sync-jobs`는 먼저 `RUNNING` 동기화 작업을 저장한 뒤 `HR_BASE_URL`의 `GET /mock/hr/employees`를 호출합니다. 직원 처리가 끝나면 작업별 건수와 상태를 반환합니다. 응답은 `201 Created`이며 본문에는 작업 ID, 상태, 처리 건수와 시작·종료 시각이 포함됩니다. 응답의 `Location` 경로인 `GET /api/sync-jobs/{id}`에서 같은 작업 결과를 조회할 수 있으며, 작업이 없으면 `404 Not Found`를 반환합니다. 작업 생성 자체가 실패하면 서버 오류를 반환합니다.

외부 HR JSON은 `employee_no`, `employee_name`, `email`, `department_code`, `employment_status` 필드를 사용합니다. HR HTTP 호출은 DB 트랜잭션 밖에서 수행합니다. 연결 오류, 시간 초과, HTTP 오류 또는 전체 응답 해석 실패는 직원 행을 만들지 않고 `FAILED` 작업으로 기록하며, 응답에는 안전한 오류 코드와 일반 메시지만 포함합니다. 한 직원의 잘못된 재직 상태는 해당 행만 실패 처리하고 나머지 행은 계속 처리합니다.

애플리케이션 인스턴스 하나 안에서는 HR Sync를 한 번에 하나만 실행합니다. 실행 중 두 번째 `POST /api/sync-jobs`는 즉시 `409 SYNC_ALREADY_RUNNING`을 받고 SyncJob을 만들지 않습니다. guard는 Job 생성과 HR 호출 전에 획득하며 성공·실패 모두 종료 시 해제합니다. 이는 단일 인스턴스의 메모리 안에서만 동작하므로 여러 애플리케이션 인스턴스나 애플리케이션 밖에서 Employee를 직접 수정하는 경우의 동시성은 보장하지 않습니다.

반복 동기화에서는 먼저 정상화와 중복 검증을 마친 사번을 1,000개씩 조회해 Employee Snapshot Map을 만듭니다. 변경이 없는 직원은 Snapshot을 기준으로 SKIP하고 개별 Employee SELECT를 생략합니다. INSERT 또는 변경 후보는 기존 직원별 `REQUIRES_NEW` 트랜잭션 안에서 Employee를 다시 조회해 최신 상태로 최종 판정합니다. 1,000개는 10,000건에서 큰 단일 IN 조건을 피하면서 조회를 약 10회로 제한하는 실용적인 초기 chunk 값입니다. 직원별 트랜잭션과 SyncItem 저장은 계속 수행합니다.

### Day 12 성능 측정

10,000건 동기화의 직원별 조회 경로와 Bulk Snapshot 경로를 하나의 `performanceTest` 실행에서 같은 Spring Context 및 PostgreSQL 17.11 Testcontainers 조건으로 비교했습니다. 기준 구현은 Day 11 직원별 처리 루프를 테스트 전용 `LegacyEmployeeSyncRunner`로 보존합니다. 시나리오별 1회 워밍업 뒤 각 구현을 3회 측정하고, 측정 순서를 번갈아 실행했습니다. fixture 생성, DB 초기화, 결과 검증 쿼리는 처리 시간과 SQL 집계에서 제외했습니다.

| Scenario | 처리 결과 | Before median | After median | Prepared SQL | Employee 조회 SQL |
|---|---|---:|---:|---:|---|
| A: 기존 10,000명 전체 동일 | SKIP 10,000, SyncItem 10,000 | 27,172.516 ms | 18,332.157 ms | 20,007 → 10,018 | 개별 10,000 → 0, bulk 0 → 10 |
| B: 10번째마다 부서 변경 | UPDATE 1,000, SKIP 9,000, SyncItem 10,000, AuditLog/Task 각 1,000 | 29,770.774 ms | 21,988.009 ms | 23,007 → 14,018 | 개별 10,000 → 1,000, bulk 0 → 10 |

이 한 번의 로컬 paired 실행에서 중앙 처리 시간은 Scenario A 약 32.5%, B 약 26.1% 낮았습니다. 이 수치는 해당 개발 환경에서 얻은 관측값이며 다른 환경의 개선 폭을 보장하지 않습니다. 직원별 SyncItem 저장과 트랜잭션은 그대로 수행합니다. 최적화는 변경 후보만 직원 트랜잭션 안에서 다시 읽고, 동일 직원은 bulk 조회 결과로 SKIP하도록 조회 SQL을 줄입니다.

원자료는 [`before JSON`](docs/performance/day12-before.json), [`after JSON`](docs/performance/day12-after.json), 구현별 표와 SQL 정의는 [`Before report`](docs/performance/day12-before.md), [`After report`](docs/performance/day12-after.md), 비교는 [`comparison`](docs/performance/day12-comparison.md)에 보관합니다. 실행 때마다 생성하는 `build/reports/performance/` 결과는 빌드 산출물로 무시하며, 이 테스트는 과거 산출물 파일을 읽지 않습니다.

```powershell
.\gradlew.bat performanceTest
```
로컬에서 예제 HR API를 사용하려면 애플리케이션을 시작할 때 다음 환경 변수를 설정합니다. Mock HR API는 기본적으로 비활성화되어 있습니다.

```powershell
$env:MOCK_HR_ENABLED = 'true'
$env:HR_BASE_URL = 'http://localhost:8080'
.\gradlew.bat bootRun
```

다른 터미널에서 `Invoke-RestMethod -Method Post http://localhost:8080/api/sync-jobs`를 호출합니다. 기본 `initial` 시나리오는 직원 3명을 반환합니다. 같은 요청을 다시 보내면 동일 스냅샷이므로 세 행이 `SKIPPED`됩니다. `changed` 시나리오로 바꾸면 부서 변경과 퇴사 상태를 확인할 수 있으며, `partial-invalid` 시나리오는 알 수 없는 재직 상태를 가진 한 행만 실패 처리합니다.

Mock HR가 활성화되면 관리자 화면에서 서버를 재시작하지 않고 시나리오를 바꿀 수 있습니다.

```text
GET /mock/hr/scenario
PUT /mock/hr/scenario
{"scenario":"initial" | "changed" | "partial-invalid"}
```

조회와 변경 모두 관리자 세션이 필요하며 PUT은 CSRF 보호를 받습니다. 시작 시 `MOCK_HR_SCENARIO` 값이 메모리의 초기 시나리오가 됩니다. 앱을 재시작하면 현재 선택은 이 시작 설정으로 돌아갑니다. 실제 HR 클라이언트와 직원 동기화 규칙은 이 데모 API의 영향을 받지 않습니다.

### 화면 실행과 대표 데모

애플리케이션을 실행하기 전에 `ADMIN_USERNAME`, 유효한 BCrypt `ADMIN_PASSWORD_HASH`, `MOCK_HR_ENABLED=true`, `MOCK_GROUPWARE_ENABLED=true`, `INTEGRATION_WORKER_ENABLED=true`를 설정합니다. 기본 DB를 비운 전용 데모 환경에서 아래 흐름을 권장합니다.

```powershell
$env:ADMIN_USERNAME = 'admin'
$env:ADMIN_PASSWORD_HASH = '<valid BCrypt hash>'
$env:MOCK_HR_ENABLED = 'true'
$env:MOCK_HR_SCENARIO = 'initial'
$env:MOCK_GROUPWARE_ENABLED = 'true'
$env:INTEGRATION_WORKER_ENABLED = 'true'
$env:HR_BASE_URL = 'http://localhost:8080'
.\gradlew.bat bootRun
```

다른 터미널에서 관리자 화면을 실행합니다.

```powershell
cd frontend
npm ci
npm run dev
```

브라우저에서 `http://127.0.0.1:5173`을 열고 설정한 관리자 계정으로 로그인합니다. Vite는 `/api`와 `/mock` 요청을 Spring Boot의 `localhost:8080`으로 전달하므로 개발 중에도 브라우저와 API가 같은 origin처럼 동작합니다. `ADMIN_PASSWORD_HASH`는 반드시 본인이 사용할 비밀번호의 BCrypt 해시로 설정합니다. 관리자 설정이 빠지거나 유효하지 않으면 서버가 시작되지 않습니다.

화면에서 다음 순서로 확인합니다.

1. 대시보드 시나리오를 `initial`로 둡니다. 아직 HR Sync를 실행하지 않은 상태에서 외부 연계 화면을 열고 E1002를 `HTTP 500`, E1003을 `FAIL_ONCE_THEN_SUCCESS`로 설정합니다.
2. 대시보드에서 HR 동기화를 실행합니다. E1001은 성공하고 E1002는 자동 재시도 후 FAILED가 되며, E1003은 첫 호출 실패 후 다음 Attempt에서 성공합니다.
3. E1002 규칙을 `NORMAL`로 바꾸고 수동 재처리를 요청합니다. 버튼 안내는 Worker 대기 상태임을 표시하며 외부 호출 성공으로 오인하지 않습니다. 성공 후 세 직원의 Groupware 계정이 존재합니다.
4. 대시보드에서 시나리오를 `changed`로 바꾸고 다시 HR 동기화를 실행합니다. E1001은 SKIP, E1002는 부서 변경과 `UPDATE_ACCOUNT`, E1003은 퇴사와 `DISABLE_ACCOUNT`로 처리됩니다. 기존 Groupware 계정 상태에 반영됩니다.
5. 같은 `changed` 시나리오를 다시 실행하면 세 직원 모두 SKIP되고 새 IntegrationTask는 만들어지지 않습니다.

외부 연계 화면은 기본적으로 수동 새로고침입니다. 사용자가 3초 자동 새로고침을 켜면 이 화면에 있는 동안 Task 목록과 선택된 진행 중 Task/Attempt만 갱신합니다. 선택한 Task가 `SUCCESS` 또는 `FAILED`가 되면 상세 polling을 멈추며 장애 시뮬레이션 규칙은 자동 조회하지 않습니다. 화면을 떠나면 polling timer와 진행 중 조회를 정리합니다.

## 현재 범위

직원 엔티티와 저장소, V1~V6 스키마, PostgreSQL 기반 통합 테스트, HR 응답 조회, 모의 HR API, 정규화와 동기화 결과 기록, Groupware HTTP 호출과 Worker, 자동 재시도, 외부 호출 Attempt 이력, Mock Groupware 멱등 처리와 오래된 PROCESSING Task 복구, 직원별 장애 시뮬레이션, FAILED Task 수동 재시도를 포함합니다.

## 직원 스냅샷 규칙

HR에서 받은 각 직원 행은 전체 스냅샷입니다. 사번은 앞뒤 공백을 제거하고 영문자를 대문자로 표준화합니다. 따라서 앞뒤 공백이나 영문 대소문자만 다른 사번은 같은 직원 식별자로 취급하며, 배치 안에서도 중복으로 판정합니다.

이름은 앞뒤 공백만 제거하고 이름 내부 공백은 유지합니다. 재직 상태는 앞뒤 공백을 제거한 뒤 대소문자를 구분하지 않고 `ACTIVE`, `ON_LEAVE`, `TERMINATED` 중 하나로 변환합니다. 회사 이메일은 앞뒤 공백을 제거하고 빈 값이면 `null`로 처리하며, 값이 있으면 영문자를 소문자로 표준화한 후 형식을 검증합니다. 부서 코드는 앞뒤 공백만 제거하고 대소문자는 유지합니다.

회사 이메일과 부서 코드는 선택 정보이므로 `null`로 정규화된 스냅샷은 기존 값도 `null`로 갱신합니다. 이전 배정을 유지하는 대신 미배정 상태가 됩니다.

## 동기화 처리

제공된 HR 행 목록을 한 번의 동기화 실행으로 처리합니다. `SyncJob`은 전체 입력 행 수, INSERT·UPDATE·SKIP·FAILED 집계, 실행 상태와 시작·종료 시각을 기록합니다. `SyncItem`은 각 입력 행의 결과를 기록하며 정규화 실패도 포함합니다.

정규화한 사번으로 기존 직원을 조회하고 이름, 이메일, 부서 코드, 재직 상태를 비교합니다. 직원이 없으면 INSERT, 비교 필드가 달라지면 UPDATE, 모두 같으면 SKIP합니다. INSERTED, UPDATED, SKIPPED 결과는 해당 Employee에 연결하고, FAILED 결과는 원칙적으로 Employee에 연결하지 않습니다. 행 하나의 정규화 오류나 데이터베이스 제약 오류는 해당 행의 FAILED 결과로 기록하고 다음 행을 계속 처리합니다.

INSERT와 UPDATE는 Employee, SyncItem, AuditLog, 해당되는 IntegrationTask를 직원별 트랜잭션 하나에서 저장합니다. 하나라도 저장에 실패하면 그 직원의 변경과 성공 기록은 함께 롤백되고 FAILED SyncItem만 별도 기록됩니다. SKIP은 Employee에 연결된 SyncItem만 기록하며 AuditLog나 IntegrationTask를 만들지 않습니다. 정규화 실패도 FAILED SyncItem만 기록합니다.

AuditLog는 생성 시점의 직원 스냅샷 전체를 기록하고, 수정 시에는 Day3에서 계산한 변경 필드와 이전·이후 값만 기록합니다. IntegrationTask의 payload도 생성 시점의 Employee 스냅샷이며 이후 직원 정보가 바뀌어도 달라지지 않습니다. AuditLog 변경 내용과 Task payload는 JSON 문자열을 TEXT로 저장하고, Task의 멱등 키는 UUID입니다.

최종 재직 상태가 `TERMINATED`인 INSERT와 UPDATE는 `DISABLE_ACCOUNT` Task를 만듭니다. ACTIVE 또는 ON_LEAVE 신규 직원은 `CREATE_ACCOUNT`, 기존 직원의 ACTIVE 또는 ON_LEAVE 수정은 `UPDATE_ACCOUNT`를 만듭니다. 따라서 최초 TERMINATED 직원도 비활성화 작업을 기록하고, 이후 ACTIVE 또는 ON_LEAVE으로 바뀌면 UPDATE_ACCOUNT가 생성됩니다. SKIP과 FAILED 행에는 Task가 없습니다. Task는 `PENDING`, retryCount 0, maxRetryCount 3으로 생성됩니다.

## Groupware 연계 Worker

Worker는 기본적으로 비활성화되어 있습니다. 실행하려면 `INTEGRATION_WORKER_ENABLED=true`로 설정합니다. 기본 batch size는 10이며 `INTEGRATION_WORKER_BATCH_SIZE`로 조정할 수 있습니다. `INTEGRATION_WORKER_FIXED_DELAY`는 기본 3초입니다. Groupware 주소와 timeout은 각각 `GROUPWARE_BASE_URL`, `GROUPWARE_CONNECT_TIMEOUT`, `GROUPWARE_READ_TIMEOUT`으로 설정합니다.

Worker는 기본 60초 이상 PROCESSING 상태에 머문 Task를 먼저 복구한 뒤, 생성 시각과 ID 순서로 PENDING Task와 재시도 시각이 지난 RETRY_WAIT Task를 조회합니다. 각 Task를 PROCESSING으로 커밋한 뒤 Groupware HTTP 요청을 보냅니다. PROCESSING 시작 시각은 전용 `processing_started_at`에 저장합니다. HTTP 요청 중에는 DB 트랜잭션을 열지 않습니다. 결과까지 확인해 저장할 수 있었던 실제 HTTP 호출만 `IntegrationAttempt`로 기록하고 Attempt와 Task 결과를 같은 별도 트랜잭션에서 저장합니다. payload 해석이나 검증이 HTTP 전에 실패하면 Task를 FAILED 처리하고 Attempt는 만들지 않습니다.

`retryCount`는 자동 재실행 예산에 사용합니다. 최초 실행은 0이며 RETRY_WAIT 또는 stale PROCESSING에서 다시 PROCESSING으로 넘어갈 때 1 증가합니다. 정상 Retry에서는 저장된 Attempt 번호와 자연스럽게 대응하지만, PROCESSING 복구에서는 `attemptNo = retryCount + 1`을 강제하지 않습니다. Attempt 번호는 기존 저장 이력 중 가장 큰 번호 다음 값을 사용합니다. 중단 시점에 실제 HTTP 요청이 전송됐는지 알 수 없으므로 가짜 Attempt를 남기지 않습니다. 연결 실패, timeout, HTTP 429·500·502·503·504는 각각 5초, 15초, 30초 대기 후 자동 재시도합니다. 네 번째 자동 재실행이 끝난 작업은 FAILED가 됩니다. 그 외 4xx, 계약 또는 응답 해석 오류는 자동 재시도하지 않습니다. Worker는 다음 재시도 시각까지 대기하지 않고 다른 실행 가능한 Task를 처리합니다.

Groupware API 계약은 `POST /mock/groupware/accounts`(신규 생성), `PUT /mock/groupware/accounts/{employeeNo}`(계정 생성 또는 활성 상태로 갱신), `PATCH /mock/groupware/accounts/{employeeNo}/disable`(비활성 상태 보장)입니다. POST는 기존 계정에 409를 반환하고, PUT은 계정이 없으면 새로 만듭니다. DISABLE은 이미 비활성인 계정과 없는 계정에 성공합니다. 계정 없음 응답은 HTTP 404와 `code=ACCOUNT_NOT_FOUND`를 반환하며, GroupwareClient는 DISABLE 요청의 이 정확한 오류 코드만 업무상 성공으로 처리합니다. 다른 404는 실패로 기록합니다. DTO는 `employee_no`, `name`, `email`, `department_code`, `employment_status` 필드를 사용합니다. Task의 UUID 멱등 키는 `Idempotency-Key` 헤더로 전달되며 자동 재시도에서도 유지됩니다.

Mock Groupware는 기본적으로 비활성화되어 있습니다. 앱 내부 Mock API를 사용하려면 `MOCK_GROUPWARE_ENABLED=true`로 설정합니다. UUID 형식 `Idempotency-Key` 헤더는 필수입니다. 같은 키와 같은 요청에는 최초의 업무 성공 응답을 재사용하며 계정 업무를 반복하지 않습니다. 같은 키를 다른 Action이나 요청 본문에 쓰면 `409 IDEMPOTENCY_KEY_CONFLICT`를 반환합니다. 계정과 멱등 응답은 메모리에 저장되므로 애플리케이션을 재시작하면 모두 사라집니다. 실제 Groupware는 멱등 결과를 충분한 기간 동안 영속 보관해야 합니다.

### Mock Groupware 장애 시뮬레이션

Mock Groupware가 활성화된 경우 다음 API로 기본 설정과 직원별 장애 규칙을 조회·변경할 수 있습니다.

```text
GET    /mock/groupware/failure-simulation
PUT    /mock/groupware/failure-simulation/{employeeNo}
DELETE /mock/groupware/failure-simulation/{employeeNo}
```

PUT 본문은 다음과 같습니다.

```json
{"mode":"FAIL_ONCE_THEN_SUCCESS"}
```

지원 모드는 `NORMAL`, `DELAY`, `TIMEOUT`, `HTTP_500`, `FAIL_ONCE_THEN_SUCCESS`입니다. `DELAY`는 `{"mode":"DELAY","delayMs":1000}`처럼 설정합니다. DELAY가 아닌 모드에 `delayMs`를 주거나 설정 가능한 상한(기본 30초)을 넘으면 400을 반환합니다. 기본 규칙은 `MOCK_GROUPWARE_DEFAULT_FAILURE_MODE`, `MOCK_GROUPWARE_DEFAULT_DELAY_MS`, `MOCK_GROUPWARE_TIMEOUT_DELAY_MS`, `MOCK_GROUPWARE_MAX_DELAY_MS`로 설정합니다. 직원 사번은 앞뒤 공백을 제거하고 대문자로 표준화합니다.

멱등 key별로 최초 요청의 Action, 사번, 본문 fingerprint를 기억합니다. HTTP 성공 응답이 저장된 key는 현재 장애 규칙보다 먼저 재생합니다. 동일 key가 다른 요청에 쓰이면 성공 여부와 무관하게 `409 IDEMPOTENCY_KEY_CONFLICT`를 반환합니다. `FAIL_ONCE_THEN_SUCCESS`의 최초 500도 key와 요청의 연결 및 실패 소진 상태를 보존합니다. 따라서 동일 요청은 다음 호출에서 업무를 실행할 수 있고 다른 요청은 충돌합니다. 직원별 규칙을 바꾸거나 삭제해도 key 상태는 초기화되지 않습니다. 이 상태와 성공 응답은 인메모리이며 애플리케이션 재시작 시 사라집니다.

### FAILED 작업 수동 재시도

`POST /api/integration-tasks/{id}/retry`는 FAILED 작업만 `PENDING`으로 되돌립니다. Groupware를 호출하지 않으며 Worker가 다음 처리 주기에 작업을 수행합니다. `GROUPWARE_HTTP_ERROR`, `GROUPWARE_CONNECTION_ERROR`, `GROUPWARE_TIMEOUT`, `GROUPWARE_RESPONSE_INVALID`, `PROCESSING_RECOVERY_EXHAUSTED`만 재시도할 수 있습니다. 나머지 오류 코드, 오류 코드 누락, FAILED가 아닌 상태는 409로 거부합니다. 존재하지 않는 작업은 404입니다.

수동 재시도는 `retryCount`만 0으로 초기화하고 오류와 처리 시각을 지웁니다. payload, 멱등 key, maxRetryCount, 기존 Attempt는 유지합니다. 동시에 두 요청이 들어오면 작업 행 잠금으로 하나만 승인됩니다. 이미 저장된 Attempt가 있으면 이후 실제 HTTP 호출의 Attempt 번호는 기존 최댓값 다음 번호를 사용합니다.

## 관리자 API와 로그인

관리자 API는 세션 로그인과 CSRF 보호를 사용합니다. 애플리케이션을 시작하기 전에 관리자 한 명의 ADMIN_USERNAME과 BCrypt 형식의 ADMIN_PASSWORD_HASH를 환경변수로 지정해야 합니다. 값이 없거나 해시 형식이 잘못되면 시작이 실패하며 기본 계정은 생성하지 않습니다. 계정은 메모리에만 있으므로 데이터베이스 테이블이나 migration은 없습니다.

세션은 기본 30분 동안 유지됩니다. 로그인은 Spring Security의 인증 관리자와 세션 고정 보호를 사용합니다. 로그인 전에는 GET /api/auth/csrf에서 CSRF token을 받고, token과 함께 POST /api/auth/login을 호출합니다. 로그인 성공 후 새 CSRF token을 다시 받아 상태 변경 요청마다 X-CSRF-TOKEN 헤더에 넣습니다. 로그아웃은 POST /api/auth/logout입니다. GET /api/auth/me는 현재 로그인한 관리자 이름을 반환합니다.

관리자 데이터 조회 경로는 다음과 같습니다.

```text
GET /api/employees?keyword=&employmentStatus=&page=0&size=20
GET /api/employees/{id}
GET /api/employees/{id}/audit-logs?page=0&size=20
GET /api/sync-jobs?status=&page=0&size=20
GET /api/sync-jobs/{syncJobId}
GET /api/integration-tasks?status=&action=&employeeNo=&page=0&size=20
GET /api/integration-tasks/{id}
GET /api/integration-tasks/{id}/attempts?page=0&size=20
```

목록 응답은 content, page, size, totalElements, totalPages를 포함합니다. page는 0부터 시작하고 size는 1부터 100까지 허용합니다. 상세 응답에서 저장된 Task payload나 AuditLog changes가 깨진 JSON이면 원문을 노출하지 않고 해당 값은 null, 파싱 오류 플래그는 true로 반환합니다.

미인증 관리자 API는 JSON 401, 인증 후 CSRF token이 빠지거나 잘못된 상태 변경 요청은 JSON 403을 반환합니다. 개발 중 React와 API는 Vite proxy를 통해 같은 origin으로 연결하는 구성을 사용합니다. 별도의 cross-origin 쿠키나 CORS 설정은 구성하지 않습니다. 운영 환경도 same-origin으로 배포해야 합니다.

`INTEGRATION_WORKER_RECOVERY_THRESHOLD`로 stale 기준을 바꿀 수 있으며 기본값은 60초입니다. PROCESSING 복구는 기존 최대 3회의 자동 재실행 예산에 포함됩니다. retryCount가 3인 stale Task는 추가 HTTP 호출 없이 `FAILED / PROCESSING_RECOVERY_EXHAUSTED`가 됩니다. 이 상태는 Groupware 업무 실패가 확인됐다는 뜻이 아니라 자동 처리 안에서 최종 결과를 확정할 수 없다는 뜻입니다. Mock 멱등 기록은 프로세스 재시작 뒤 사라지므로, 이 복구 시나리오는 Mock 상태를 유지한 채 Worker 프로세스 중단을 재현해야 검증할 수 있습니다. lease, 다중 Worker 경쟁 제어, 수동 재시도는 범위에 포함하지 않습니다.

동기화 실행이 FAILED이면 실행 자체가 정상적으로 완료되지 못했다는 뜻입니다. 직원별 처리는 각각 커밋되므로 시스템 오류 전에 완료된 직원과 결과는 남을 수 있으며, FAILED가 전체 직원 처리를 원자적으로 롤백했다는 뜻은 아닙니다.

실제 HR 주소는 `HR_BASE_URL`, 연결 제한 시간은 `HR_CONNECT_TIMEOUT`, 응답 제한 시간은 `HR_READ_TIMEOUT`으로 설정합니다. HR 호출 재시도는 아직 수행하지 않습니다.

Flyway migration이 데이터베이스 스키마의 유일한 기준이며, Hibernate는 `validate` 모드로 매핑과 스키마를 확인합니다.

## 현재 환경 검증

이 작업 환경에서 일반 작업 경로의 기존 Gradle 산출물을 삭제하는 clean 작업이 실패한 적이 있습니다. 원인은 확정하지 않았습니다. 아래 PowerShell 명령은 Gradle 산출물만 임시 디렉터리로 보내 전체 빌드와 테스트를 수행하며 저장소 설정은 바꾸지 않습니다.

```powershell
$employeeLifecycleSyncRunId = [guid]::NewGuid().ToString('N')
$employeeLifecycleSyncInit = Join-Path $env:TEMP ('employee-lifecycle-sync-init-' + $employeeLifecycleSyncRunId + '.gradle')
$employeeLifecycleSyncOutput = 'employee-lifecycle-sync-build-' + $employeeLifecycleSyncRunId
$employeeLifecycleSyncScript = @"
allprojects {
    layout.buildDirectory.set(new File(System.getProperty('java.io.tmpdir'), '$employeeLifecycleSyncOutput'))
}
"@
[System.IO.File]::WriteAllText($employeeLifecycleSyncInit, $employeeLifecycleSyncScript, (New-Object System.Text.UTF8Encoding($false)))
try {
    .\gradlew.bat --no-daemon --init-script $employeeLifecycleSyncInit clean build
} finally {
    Remove-Item -LiteralPath $employeeLifecycleSyncInit -Force -ErrorAction SilentlyContinue
}
```
