# Employee Lifecycle Sync

사내 인사 시스템의 직원 정보를 동기화하기 위한 Spring Boot 애플리케이션입니다. 현재 단계에서는 HR 직원 행을 내부 표현으로 정규화하고 직원 정보 저장을 위한 PostgreSQL 스키마와 JPA 매핑을 구성합니다.

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

직원 엔티티, 저장소, 초기 스키마, PostgreSQL 기반 통합 테스트, HR 직원 행의 단건 및 배치 정규화를 포함합니다. HR API 호출, 정규화한 직원을 DB와 비교하는 동기화 흐름, 감사 기록, 계정 연계 작업 및 재시도는 아직 구현하지 않았습니다.

## 직원 스냅샷 규칙

HR에서 받은 각 직원 행은 전체 스냅샷입니다. 사번은 앞뒤 공백을 제거하고 영문자를 대문자로 표준화합니다. 따라서 앞뒤 공백이나 영문 대소문자만 다른 사번은 같은 직원 식별자로 취급하며, 배치 안에서도 중복으로 판정합니다.

이름은 앞뒤 공백만 제거하고 이름 내부 공백은 유지합니다. 재직 상태는 앞뒤 공백을 제거한 뒤 대소문자를 구분하지 않고 `ACTIVE`, `ON_LEAVE`, `TERMINATED` 중 하나로 변환합니다. 회사 이메일은 앞뒤 공백을 제거하고 빈 값이면 `null`로 처리하며, 값이 있으면 영문자를 소문자로 표준화한 후 형식을 검증합니다. 부서 코드는 앞뒤 공백만 제거하고 대소문자는 유지합니다.

회사 이메일과 부서 코드는 선택 정보이므로 `null`로 정규화된 스냅샷은 기존 값도 `null`로 갱신합니다. 이전 배정을 유지하는 대신 미배정 상태가 됩니다.

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
