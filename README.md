# Employee Lifecycle Sync

사내 인사 시스템의 직원 정보를 동기화하기 위한 Spring Boot 애플리케이션입니다. 현재 단계에서는 제공된 HR 직원 행을 정규화하고 기존 직원 정보와 비교해 동기화 실행 및 행별 결과를 PostgreSQL에 기록합니다.

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

## 현재 범위

직원 엔티티와 저장소, V1·V3 스키마, PostgreSQL 기반 통합 테스트, HR 직원 행의 정규화와 동기화 결과 기록을 포함합니다. Groupware HTTP 호출과 Worker 실행은 아직 구현하지 않았습니다.

## 직원 스냅샷 규칙

HR에서 받은 각 직원 행은 전체 스냅샷입니다. 사번은 앞뒤 공백을 제거하고 영문자를 대문자로 표준화합니다. 따라서 앞뒤 공백이나 영문 대소문자만 다른 사번은 같은 직원 식별자로 취급하며, 배치 안에서도 중복으로 판정합니다.

이름은 앞뒤 공백만 제거하고 이름 내부 공백은 유지합니다. 재직 상태는 앞뒤 공백을 제거한 뒤 대소문자를 구분하지 않고 `ACTIVE`, `ON_LEAVE`, `TERMINATED` 중 하나로 변환합니다. 회사 이메일은 앞뒤 공백을 제거하고 빈 값이면 `null`로 처리하며, 값이 있으면 영문자를 소문자로 표준화한 후 형식을 검증합니다. 부서 코드는 앞뒤 공백만 제거하고 대소문자는 유지합니다.

회사 이메일과 부서 코드는 선택 정보이므로 `null`로 정규화된 스냅샷은 기존 값도 `null`로 갱신합니다. 이전 배정을 유지하는 대신 미배정 상태가 됩니다.

## 동기화 처리

제공된 HR 행 목록을 한 번의 동기화 실행으로 처리합니다. `SyncJob`은 전체 입력 행 수, INSERT·UPDATE·SKIP·FAILED 집계, 실행 상태와 시작·종료 시각을 기록합니다. `SyncItem`은 각 입력 행의 결과를 기록하며 정규화 실패도 포함합니다.

정규화한 사번으로 기존 직원을 조회하고 이름, 이메일, 부서 코드, 재직 상태를 비교합니다. 직원이 없으면 INSERT, 비교 필드가 달라지면 UPDATE, 모두 같으면 SKIP합니다. INSERTED, UPDATED, SKIPPED 결과는 해당 Employee에 연결하고, FAILED 결과는 원칙적으로 Employee에 연결하지 않습니다. 행 하나의 정규화 오류나 데이터베이스 제약 오류는 해당 행의 FAILED 결과로 기록하고 다음 행을 계속 처리합니다.

INSERT와 UPDATE는 Employee, SyncItem, AuditLog, 해당되는 IntegrationTask를 직원별 트랜잭션 하나에서 저장합니다. 하나라도 저장에 실패하면 그 직원의 변경과 성공 기록은 함께 롤백되고 FAILED SyncItem만 별도 기록됩니다. SKIP은 Employee에 연결된 SyncItem만 기록하며 AuditLog나 IntegrationTask를 만들지 않습니다. 정규화 실패도 FAILED SyncItem만 기록합니다.

AuditLog는 생성 시점의 직원 스냅샷 전체를 기록하고, 수정 시에는 Day3에서 계산한 변경 필드와 이전·이후 값만 기록합니다. IntegrationTask의 payload도 생성 시점의 Employee 스냅샷이며 이후 직원 정보가 바뀌어도 달라지지 않습니다. AuditLog 변경 내용과 Task payload는 JSON 문자열을 TEXT로 저장하고, Task의 멱등 키는 UUID입니다.

신규 ACTIVE 또는 ON_LEAVE 직원은 `CREATE_ACCOUNT` Task를 만듭니다. 신규 TERMINATED 직원은 Task를 만들지 않습니다. 기존 직원이 TERMINATED로 바뀌면 `DISABLE_ACCOUNT`, 그 외 수정은 `UPDATE_ACCOUNT`를 사용합니다. 퇴사 후 재입사는 `UPDATE_ACCOUNT`입니다. Task는 `PENDING`, retryCount 0, maxRetryCount 3으로 생성됩니다. 현재는 Groupware HTTP 호출, Worker, 재시도를 실행하지 않습니다.

동기화 실행이 FAILED이면 실행 자체가 정상적으로 완료되지 못했다는 뜻입니다. 직원별 처리는 각각 커밋되므로 시스템 오류 전에 완료된 직원과 결과는 남을 수 있으며, FAILED가 전체 직원 처리를 원자적으로 롤백했다는 뜻은 아닙니다.

현재는 HR HTTP API나 Groupware HTTP API를 호출하지 않습니다. Worker, 실제 외부 계정 연계와 재시도는 이후 단계 범위입니다.

Flyway migration이 데이터베이스 스키마의 유일한 기준이며, Hibernate는 `validate` 모드로 매핑과 스키마를 확인합니다.

## 현재 환경 검증

이 작업 환경에서 일반 작업 경로의 기존 Gradle 산출물을 삭제하는 clean 작업이 실패한 적이 있습니다. 원인은 확정하지 않았습니다. 아래 PowerShell 명령은 Gradle 산출물만 임시 디렉터리로 보내 전체 빌드와 테스트를 수행하며 저장소 설정은 바꾸지 않습니다.

```powershell
$employeeLifecycleSyncInit = Join-Path $env:TEMP ('employee-lifecycle-sync-init-' + [guid]::NewGuid().ToString('N') + '.gradle')
$employeeLifecycleSyncScript = @'
allprojects {
    layout.buildDirectory.set(new File(System.getProperty('java.io.tmpdir'), 'employee-lifecycle-sync-build-output'))
}
'@
[System.IO.File]::WriteAllText($employeeLifecycleSyncInit, $employeeLifecycleSyncScript, (New-Object System.Text.UTF8Encoding($false)))
try {
    .\gradlew.bat --no-daemon --init-script $employeeLifecycleSyncInit clean build
} finally {
    Remove-Item -LiteralPath $employeeLifecycleSyncInit -Force -ErrorAction SilentlyContinue
}
```
