# 로컬 배포와 운영 설정

이 배포는 React 파일을 Spring Boot JAR에서 제공하는 same-origin 단일 애플리케이션과 PostgreSQL 17.11로 구성됩니다. 애플리케이션은 단일 인스턴스로 운영합니다. Session, HR Sync 실행 guard, Mock Groupware 계정과 멱등 기록은 애플리케이션 메모리에 있으므로 다중 인스턴스 구성은 지원하지 않습니다.

## 로컬 Docker Compose

1. `.env.example`을 `.env`로 복사합니다.
2. `POSTGRES_PASSWORD`와 `DB_PASSWORD`에 같은 임의의 로컬 값을 넣습니다.
3. `ADMIN_USERNAME`과 `ADMIN_PASSWORD_HASH`를 직접 설정합니다. 해시는 본인이 선택한 로컬 데모 비밀번호로 생성한 BCrypt 값이어야 합니다.
4. 실행합니다.

```powershell
docker compose up --build -d
docker compose ps
```

로컬 Compose는 HTTP이므로 앱 컨테이너에만 `SERVER_SERVLET_SESSION_COOKIE_SECURE=false`를 설정합니다. 운영 기본값은 Secure=true입니다. 포트는 로컬 루프백에만 바인딩됩니다. DB 데이터는 `employee-lifecycle-sync-postgres` named volume에 보관됩니다.

관리 화면은 `http://localhost:8080/`에서 엽니다. `/login`, `/employees`, `/integrations`도 직접 열거나 새로고침할 수 있습니다. `/actuator/health`는 외부에서 `UP` 또는 `DOWN`만 반환합니다.

OpenAPI는 기본 비활성화입니다. 데모에서 문서를 켜려면 `.env`에 `OPENAPI_ENABLED=true`를 설정하고 컨테이너를 재생성합니다. Swagger와 `/v3/api-docs`는 관리자 로그인 없이 접근할 수 없습니다. CSRF를 해제하거나 인증을 우회하지 않습니다.

정지할 때는 volume을 보존합니다.

```powershell
docker compose down
```

`down -v`는 실행하지 마세요. named volume을 삭제합니다.

## Compose의 BCrypt 값

`.env`의 `ADMIN_PASSWORD_HASH` 값은 Compose env file에서 작은따옴표로 감쌉니다. 예를 들어 실제 해시를 작은따옴표 안에 그대로 넣습니다. Compose가 이를 컨테이너에 전달할 때 `$` 문자를 보존하는지 로컬 검증에서 확인한 뒤 이 사용법을 문서화했습니다. `.env.example`에는 실제 해시나 비밀번호가 없습니다.

## 일반 Gradle 빌드

```powershell
.\gradlew.bat clean build
```

Gradle은 frontend에서 `npm ci` → `npm test` → `npm run build`를 한 번 실행합니다. `frontend/dist`에 결과를 만들고, 이 결과를 JAR의 `BOOT-INF/classes/static/`에 넣습니다. 출력 디렉터리가 없으면 `bootJar`를 실패시킵니다. `dist`는 Git에 포함하지 않습니다.

이미 만들어진 frontend 결과를 전달할 때는 다음처럼 빌드합니다.

```powershell
.\gradlew.bat clean build -PfrontendDistDir=frontend/dist
```

이 옵션을 사용하면 Gradle은 React를 다시 빌드하지 않고 전달된 `index.html`을 확인한 뒤 JAR에 포함합니다.

## Docker 이미지

`Dockerfile`은 Node 24에서 dependency 설치·frontend 테스트·build를 한 번 수행합니다. Java 21 JDK 단계는 그 `dist`를 Gradle에 전달하고 `bootJar`만 만듭니다. Java 21 JRE 단계에는 JAR만 복사하며 non-root `app` 사용자로 실행합니다. Docker 빌드는 Testcontainers 테스트를 다시 실행하지 않습니다.

## 운영 배포 환경변수

운영자는 secret store에서 값을 주입하고 `SPRING_PROFILES_ACTIVE=prod`로 실행해야 합니다.

필수 설정:

```text
DB_URL
DB_USERNAME
DB_PASSWORD
ADMIN_USERNAME
ADMIN_PASSWORD_HASH
```

연동 주소와 timeout은 환경에 맞게 지정합니다. `MOCK_HR_ENABLED`, `MOCK_GROUPWARE_ENABLED`, `INTEGRATION_WORKER_ENABLED`, `OPENAPI_ENABLED`는 기본적으로 false입니다. 운영에서 Mock이나 OpenAPI를 켤 때는 사용 목적과 접근 경로를 확인해야 합니다.

운영 cookie 기본값은 Secure=true, HttpOnly=true, SameSite=Lax입니다. HTTPS 종단이 외부 proxy에 있는 경우에도 브라우저에서 HTTPS same-origin으로 접근하도록 구성합니다. 이 프로젝트는 cross-origin 쿠키나 CORS 배포를 설정하지 않습니다.

오직 `/actuator/health`만 노출합니다. DB가 정상일 때 200과 `status=UP`, DB 장애일 때 503과 `status=DOWN`을 반환하고 상세 정보를 숨깁니다. 그 밖의 Actuator endpoint는 웹에 노출하지 않습니다.

## GitHub Actions

`.github/workflows/ci.yml`은 pull request와 `local`, `develop`, `main` push에서 Java 21 및 Node 24 설정, `npm ci`, frontend 테스트/build, Testcontainers를 포함한 Gradle `clean build`, Docker image build 순으로 실행합니다. 별도 PostgreSQL service와 자동 배포는 없습니다. 확인한 GitHub Actions run 37572932660에서는 frontend 검증이 통과했고, backend build가 시작 시 exit code 126으로 실패해 Docker image 단계는 skip됐습니다. 원인은 workflow가 `./gradlew`로 실행하는 wrapper의 Git 모드가 `100644`였던 점입니다. wrapper 모드를 `100755`로 수정했지만 이 수정은 아직 push하지 않았으므로 GitHub Actions 재실행 성공은 확인되지 않았습니다.
