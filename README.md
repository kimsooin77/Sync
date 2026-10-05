# Employee Lifecycle Sync

사내 인사 시스템의 직원 정보를 동기화하기 위한 Spring Boot 애플리케이션입니다. HR API에서 전체 직원 스냅샷을 가져와 정규화하고 기존 직원 정보와 비교해 동기화 실행 및 행별 결과를 PostgreSQL에 기록합니다.

## 요구 사항

- JDK 21
- Docker Compose 지원 Docker 엔진
- 별도 Gradle 설치는 필요하지 않습니다. 프로젝트에 포함된 Gradle Wrapper를 사용합니다.

## 데이터베이스 시작

```powershell
docker compose up -d db
```

애플리케이션은 기본적으로 `localhost:5432`의 `employee_lifecycle_sync` 데이터베이스에 연결합니다. 접속 정보는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 환경 변수로 바꿀 수 있습니다. Compose 기본 계정은 로컬 개발용입니다.

데이터베이스를 중지하려면 다음을 실행합니다.

```powershell
docker compose down
```

## 빌드와 실행

Windows PowerShell:

```powershell
.\gradlew.bat clean build
.\gradlew.bat test
.\gradlew.bat bootRun
```

macOS 또는 Linux:

```sh
./gradlew clean build
./gradlew test
./gradlew bootRun
```

`bootRun`은 기본 포트 8080에서 애플리케이션을 시작합니다. `test`는 PostgreSQL 17.11 Testcontainers를 사용하므로 Docker 엔진이 실행 중이어야 합니다.

## HR 동기화 API

`POST /api/sync-jobs`는 먼저 `RUNNING` 동기화 작업을 저장한 뒤 `HR_BASE_URL`의 `GET /mock/hr/employees`를 호출합니다. 직원 처리가 끝나면 작업별 건수와 상태를 반환합니다. 응답은 `201 Created`이며 본문에는 작업 ID, 상태, 처리 건수와 시작·종료 시각이 포함됩니다. 응답의 `Location` 경로인 `GET /api/sync-jobs/{id}`에서 같은 작업 결과를 조회할 수 있으며, 작업이 없으면 `404 Not Found`를 반환합니다. 작업 생성 자체가 실패하면 서버 오류를 반환합니다.

외부 HR JSON은 `employee_no`, `employee_name`, `email`, `department_code`, `employment_status` 필드를 사용합니다. HR HTTP 호출은 DB 트랜잭션 밖에서 수행합니다. 연결 오류, 시간 초과, HTTP 오류 또는 전체 응답 해석 실패는 직원 행을 만들지 않고 `FAILED` 작업으로 기록하며, 응답에는 안전한 오류 코드와 일반 메시지만 포함합니다. 한 직원의 잘못된 재직 상태는 해당 행만 실패 처리하고 나머지 행은 계속 처리합니다.

로컬에서 예제 HR API를 사용하려면 애플리케이션을 시작할 때 다음 환경 변수를 설정합니다. Mock HR API는 기본적으로 비활성화되어 있습니다.

```powershell
$env:MOCK_HR_ENABLED = 'true'
$env:HR_BASE_URL = 'http://localhost:8080'
.\gradlew.bat bootRun
```

다른 터미널에서 `Invoke-RestMethod -Method Post http://localhost:8080/api/sync-jobs`를 호출합니다. 기본 `initial` 시나리오는 직원 3명을 반환합니다. 같은 요청을 다시 보내면 동일 스냅샷이므로 세 행이 `SKIPPED`됩니다. `MOCK_HR_SCENARIO=changed`로 설정하고 애플리케이션을 다시 시작하면 부서 변경과 퇴사 상태 변경을 확인할 수 있습니다. `partial-invalid` 시나리오는 한 행의 재직 상태를 알 수 없는 값으로 보내 행 단위 오류 격리를 확인합니다.

## 현재 범위

직원 엔티티와 저장소, V1~V4 스키마, PostgreSQL 기반 통합 테스트, HR 응답 조회, 모의 HR API, 정규화와 동기화 결과 기록, Groupware HTTP 호출과 Worker를 포함합니다. 자동 재시도와 PROCESSING Task 복구는 아직 구현하지 않았습니다.

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

Worker는 생성 시각과 ID 순서로 PENDING Task를 조회하고 각 Task를 PROCESSING으로 커밋한 뒤 Groupware HTTP 요청을 보냅니다. HTTP 요청 중에는 DB 트랜잭션을 열지 않습니다. 각 요청이 끝나면 SUCCESS 또는 FAILED를 별도 트랜잭션으로 기록합니다. Day 6에서는 자동 재시도를 수행하지 않으며 연계 실패는 FAILED로 남습니다.

Groupware API 계약은 `POST /mock/groupware/accounts`(신규 생성), `PUT /mock/groupware/accounts/{employeeNo}`(계정 생성 또는 활성 상태로 갱신), `PATCH /mock/groupware/accounts/{employeeNo}/disable`(비활성 상태 보장)입니다. POST는 기존 계정에 409를 반환하고, PUT은 계정이 없으면 새로 만듭니다. DISABLE은 이미 비활성인 계정과 없는 계정에 성공하며, 외부 Groupware의 404도 성공으로 취급합니다. DTO는 `employee_no`, `name`, `email`, `department_code`, `employment_status` 필드를 사용합니다. Task의 UUID 멱등 키는 `Idempotency-Key` 헤더로 전달됩니다.

Mock Groupware는 기본적으로 비활성화되어 있습니다. 앱 내부 Mock API를 사용하려면 `MOCK_GROUPWARE_ENABLED=true`로 설정합니다. 계정은 메모리에만 저장되므로 애플리케이션을 재시작하면 사라집니다.

Task가 PROCESSING으로 커밋된 직후 애플리케이션이 종료되면 해당 Task는 PROCESSING에 남아 Day 6 Worker가 다시 처리하지 않습니다. lease 및 복구는 Day 8 멱등성·복구 단계에서 다룹니다.

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
