# Employee Lifecycle Sync

직원 인사 정보와 계정 동기화를 위한 Spring Boot 프로젝트입니다. 현재는 기본 웹 애플리케이션 시작과 빌드 검증을 위한 뼈대만 포함합니다.

## 요구 사항

- JDK 21
- 별도 Gradle 설치는 필요하지 않습니다. 프로젝트에 포함된 Gradle Wrapper를 사용합니다.
- 현재 단계에서는 Docker나 데이터베이스가 필요하지 않습니다.

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

bootRun은 기본 포트 8080에서 웹 애플리케이션을 시작합니다.

## 현재 범위

Java 21과 Spring Boot 4.1.1을 사용하는 MVC 애플리케이션 뼈대입니다. HR 연계, 직원 처리, 데이터베이스, 인증 기능은 아직 포함하지 않습니다.

## 현재 환경 검증

이 작업 환경에서는 일반 작업 경로에서 clean build가 기존 빌드 산출물 삭제에 실패했습니다. 원인은 확정하지 않았습니다. 아래 PowerShell 명령은 Gradle 산출물만 사용자 임시 디렉터리로 옮겨 검증합니다. 저장소 설정은 바꾸지 않습니다.

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
