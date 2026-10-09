# AWS 배포 준비 (초안)

> 상태: 로컬 배포 산출물 준비 단계입니다. AWS 리소스를 생성하거나 실제 배포를 검증하지 않았습니다. 아래 내용은 구성 계획이며 운영 성공을 의미하지 않습니다.

## 목표 구성

- Region: Stockholm, eu-north-1
- Application: 단일 인스턴스 Elastic Beanstalk Docker 환경
- Runtime image: Java 21 JRE Alpine, 미리 빌드한 Spring Boot executable JAR
- Database: private RDS for PostgreSQL 17, Single-AZ
- HTTPS: CloudFront를 Elastic Beanstalk HTTP origin 앞에 둡니다.
- 애플리케이션과 Mock HR/Groupware는 같은 Spring Boot 프로세스에서 실행합니다. 기본 내부 호출 주소는 127.0.0.1과 앱 포트를 사용합니다.

현재 애플리케이션은 Session, 동기화 실행 guard, Mock Groupware 계정과 멱등 기록 일부를 메모리에 보관합니다. 따라서 최초 배포는 Elastic Beanstalk 인스턴스 한 대로 제한합니다. 인스턴스 교체나 애플리케이션 재시작 시 메모리 상태가 사라질 수 있습니다.

## 로컬 AWS bundle 생성

Windows PowerShell에서 저장소 루트 기준으로 실행합니다.

    .\scripts\New-AwsBundle.ps1

스크립트는 다음을 실행하고 확인합니다.

1. frontend에서 npm ci, npm test, npm run build
2. 빌드된 frontend/dist를 전달해 gradlew.bat clean build 실행
3. 실행 가능한 bootJar가 하나인지 확인
4. JAR에 BOOT-INF/classes/static/index.html, 정적 asset, Flyway V1/V6 migration이 있는지 확인
5. build/aws-bundle/에 Dockerfile과 application.jar만 복사
6. build/aws-bundle.zip 안에도 두 파일만 있는지, Dockerfile 내용과 JAR 크기가 검증한 산출물과 일치하는지 확인

이미 검증된 JAR로 bundle만 다시 만들 때는 다음을 사용합니다. -SkipBuild를 사용해도 JAR의 정적 파일과 migration 검사는 생략되지 않습니다.

    .\scripts\New-AwsBundle.ps1 -SkipBuild

업로드 전에 ZIP의 raw entry 이름이 루트의 Dockerfile과 application.jar 두 개뿐인지 따로 확인할 수 있습니다.

    .\scripts\Test-AwsBundle.ps1

Bundle에는 소스 코드, .env, secret, Compose 파일, 테스트 결과, Node modules, Gradle cache가 포함되지 않습니다. ZIP을 AWS에 올리기 전에 파일 목록을 직접 확인합니다.

## 환경변수

실제 값은 AWS 환경변수/secret 설정에 직접 입력합니다. Git, README, .env.example, 배포 bundle에 비밀번호나 BCrypt hash를 기록하지 않습니다.

필수 항목:

- SPRING_PROFILES_ACTIVE=prod
- DB_URL (RDS endpoint와 database 이름을 사용하는 JDBC URL)
- DB_USERNAME
- DB_PASSWORD
- ADMIN_USERNAME
- ADMIN_PASSWORD_HASH (유효한 BCrypt hash)

PORT가 있으면 Spring Boot가 사용하고, 없으면 SERVER_PORT, 이후 8080을 사용합니다. HR_BASE_URL과 GROUPWARE_BASE_URL은 기본적으로 현재 컨테이너의 loopback과 앱 포트를 가리킵니다. 별도 외부 서비스가 필요하다는 검증된 이유가 생기지 않으면 public domain을 통한 자기 호출을 설정하지 않습니다.

운영 cookie는 HTTPS 전제의 Secure=true, HttpOnly=true, SameSite=Lax 설정을 유지합니다. 로컬 Compose용 Secure override를 AWS에 복사하지 않습니다.

## 네트워크 및 데이터베이스 계획

- EB EC2와 RDS는 같은 VPC에 둡니다.
- RDS는 Publicly accessible=false로 설정합니다.
- RDS Security Group의 TCP 5432 인바운드는 EB EC2 Security Group만 source로 허용합니다.
- 0.0.0.0/0에서 PostgreSQL 접속을 허용하지 않습니다.
- schema는 Flyway가 생성하고 Hibernate는 ddl-auto=validate로 동작합니다. AWS Console이나 DBeaver에서 테이블을 수동 생성하지 않습니다.
- 기존 로컬 Compose volume은 AWS 준비 과정에서 삭제하거나 초기화하지 않습니다.

## HTTPS와 CloudFront 계획

CloudFront에서 viewer HTTPS를 강제하고, cache는 비활성화합니다. Session/CSRF 흐름에 필요한 cookie, query string, header를 origin까지 전달하도록 설정합니다. 로그인, CSRF, 새로고침, API 응답을 실제로 확인하기 전에는 CloudFront 구성이 검증됐다고 간주하지 않습니다.

CloudFront를 사용해도 EB 원본 주소가 기본적으로 비공개가 되는 것은 아닙니다. origin 직접 접근 제한은 별도로 검토해야 합니다. 현재 설계에서는 이를 해결했다고 주장하지 않습니다.

## 비용 승인 경계

Elastic Beanstalk 자체 요금이 없더라도 EC2, EBS, 공인 IPv4, RDS, 스토리지/백업, 데이터 전송 및 CloudFront에서 비용이 생길 수 있습니다. 실제 비용은 계정의 Free Plan/credit 상태와 리전별 선택 항목을 AWS Billing 및 Pricing Calculator에서 확인해야 합니다.

유료 플랜 선택, 결제, 비용이 발생하는 AWS 리소스 생성 단계에 도달하면 작업을 멈추고 비용과 해당 단계의 결과를 확인받습니다. 이 문서는 비용 견적이나 무료 운영 보장을 제공하지 않습니다.

## 배포 완료 전 검증 항목

아래는 실제 AWS 배포 뒤 확인할 항목입니다. 현재는 미검증입니다.

- EB 환경 health와 앱 시작 로그
- Flyway migration 적용, RDS 연결 및 재기동 뒤 DB 데이터 유지
- CloudFront HTTPS와 SPA 직접 접근/새로고침
- 없는 asset 경로가 index.html로 응답하지 않음
- 로그인, Session cookie, CSRF 보호
- /actuator/health 응답에 상세 정보가 노출되지 않음
- HR sync, 자동 Retry, FAILED와 수동 Retry
- 애플리케이션만 재배포한 뒤 RDS 데이터 유지 및 메모리 상태 초기화
- 실제 AWS 사용량과 남은 credit

Git merge, branch 변경, commit/push는 별도 요청 전까지 하지 않습니다.
