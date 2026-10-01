# Employee Lifecycle Sync

사내 인사 시스템의 직원 정보를 동기화하기 위한 Spring Boot 애플리케이션입니다. 현재 단계에서는 직원 정보 저장을 위한 PostgreSQL 스키마와 JPA 매핑을 구성합니다.

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

직원 엔티티, 저장소, 초기 스키마 및 PostgreSQL 기반 통합 테스트를 포함합니다. HR API 연계, 스냅샷 정규화 호출 흐름, 변경 감지와 감사 기록, 계정 연계 작업 및 재시도는 아직 구현하지 않았습니다.

## 직원 스냅샷 규칙

HR에서 받은 각 직원 행은 전체 스냅샷입니다. 회사 이메일과 부서 코드는 앞뒤 공백을 제거한 뒤 빈 값이면 `null`로 저장하며, 기존 직원의 값도 `null`로 갱신합니다. 즉, 선택 필드가 비어 있으면 이전 배정을 유지하는 대신 미배정 상태가 됩니다.

Flyway migration이 데이터베이스 스키마의 유일한 기준이며, Hibernate는 `validate` 모드로 매핑과 스키마를 확인합니다.

## 현재 환경 검증

이 작업 환경에서 일반 작업 경로의 기존 Gradle 산출물을 삭제하는 clean 작업이 실패한 적이 있습니다. 원인은 확정하지 않았습니다. 아래 PowerShell 명령은 Gradle 산출물만 임시 디렉터리로 보내 검증하며 저장소 설정은 바꾸지 않습니다. 이 방법으로 테스트를 실행해 4개 테스트가 통과했습니다.

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
