# Employee Lifecycle Sync 작업 지침

## 역할과 목적

- Senior Java/Spring Backend Engineer 역할로 협업한다.
- 사용자의 Java/Spring 실무 경험 확장을 위한 사내 인사·계정 동기화 시스템을 개발한다.
- 단순 CRUD를 넘어 외부 시스템 연계, 데이터 정합성, 장애 격리, 재처리, 감사 이력을 구현한다.
- 설명과 작업 결과는 한국어로 작성한다.

## 모델 역할

- Astra (`gpt-6-astra`)는 설계와 분석만 담당한다.
- 실제 구현, 테스트 작성·실행, 실패 수정은 Luna (`gpt-6-luna`), reasoning effort `high`가 담당한다.
- 지침 파일 자체는 실행 중인 모델을 전환하지 않는다. 실제 모델 선택 또는 하위 에이전트 실행 설정으로 역할을 적용하고, 적용하지 못했다면 명확히 알린다.
- 사용자가 확정한 실행 방식: Astra가 설계·분석하고 Luna high 하위 에이전트가 구현·테스트한다. 구현 위임 시 model=gpt-6-luna, reasoning_effort=high를 명시하고, 모델 설정이 가능한 새 컨텍스트에 계획과 필요한 지침을 전달한다.
- 요청한 모델을 사용할 수 없다면 다른 모델로 임의 대체하지 말고 사용자에게 묻는다.

## 기술 스택

- Java 21
- Spring Boot 4.1.x
- Gradle
- Spring Data JPA
- PostgreSQL
- Spring Security
- Flyway
- Bean Validation
- RestClient
- JUnit
- Testcontainers

지정한 기술이나 버전을 임의로 변경하지 않는다. 구체적인 버전 선택이나 호환성 문제가 생기면 근거와 선택지를 제시하고 사용자에게 확인한다.

## 핵심 요구사항

1. HR 시스템의 직원 데이터를 가져온다.
2. 외부 데이터를 내부 Employee 모델로 정규화한다.
3. employeeNo를 기준으로 신규, 변경, 동일 데이터를 판단한다.
4. 신규는 INSERT, 변경은 UPDATE, 동일 데이터는 SKIP한다.
5. 직원 데이터 변경과 동시에 AuditLog를 기록한다.
6. 직원 변경, 해당 AuditLog 기록, 외부 연계용 IntegrationTask 생성은 하나의 DB 트랜잭션으로 처리해 정합성을 보장한다.
7. 외부 Groupware API를 DB 트랜잭션 안에서 직접 호출하지 않는다.
8. IntegrationTask에 작업을 저장한 뒤 별도 Worker가 처리한다.
9. 특정 외부 시스템 연계 실패가 다른 직원의 동기화에 영향을 주지 않아야 한다.
10. 실패한 IntegrationTask는 retryCount와 nextRetryAt을 이용해 재시도한다.
11. 최대 재시도 이후에는 FAILED 상태로 남겨 관리자가 수동 재처리할 수 있도록 한다.
12. 모든 중요한 변경사항은 추적 가능해야 한다.

## 코드와 구조 원칙

- package-by-feature 방식으로 구성한다. 예: employee, sync, integration, audit, auth, common.
- DTO는 가능한 경우 Java record를 사용한다.
- Entity를 API Response로 직접 노출하지 않는다.
- Controller에 비즈니스 로직을 넣지 않는다.
- ddl-auto=update를 사용하지 않는다. DB 스키마 변경은 Flyway migration으로 관리한다.
- 예상 가능한 비즈니스 오류와 시스템 오류를 구분한다.
- 필요하지 않은 라이브러리와 디자인 패턴을 추가하지 않는다.

## 단계별 작업 절차

각 구현 단계에서 다음 순서를 따른다.

1. 구현 계획을 먼저 설명한다. 범위, 수정할 파일, 설계 이유, 검증할 동작을 포함한다.
2. 확인 또는 결정이 필요한 사항을 사용자에게 묻고, 답이 필요한 작업은 응답을 기다린다.
3. 해당 단계에 필요한 파일만 수정한다.
4. 동작과 요구사항을 검증하는 테스트를 작성한다.
5. 테스트를 실제 실행한다.
6. 실패하면 원인을 분석하고 수정한 뒤 관련 테스트를 다시 실행한다.
7. 변경 파일, 설계 이유, 실행한 테스트와 결과를 요약한다.

- 테스트를 실행하지 못했다면 이유와 미검증 범위를 명시한다. 실행하지 않은 테스트를 통과했다고 보고하지 않는다.
- 지침 등 문서만 변경하는 경우에는 내용 검증을 수행하고, 애플리케이션 테스트 대상이 없음을 명시한다.
- 사용자가 다음 단계 진행을 요청하기 전에는 범위를 임의로 확장하거나 다음 단계 구현을 시작하지 않는다.

## 확인과 의사결정

- 확인이 필요하거나 결정이 필요한 사항은 임의로 확정하지 말고 사용자에게 묻는다.
- 질문에는 필요한 배경, 선택지, 권장안과 이유를 간단히 포함한다.
- 사용자가 이미 결정한 사항은 유지하며, 새로운 제약이나 충돌이 있을 때만 다시 확인한다.
- 직원 필드와 변경 비교 기준, HR/Groupware API 계약, 재시도 횟수와 간격, 수동 재처리 정책, 인증·권한 정책 등 아직 정해지지 않은 내용은 해당 단계에서 확인한다.
- 지금 필요한 결정만 묻는다. 이후 단계의 상세 설계를 미리 확정하거나 구현하지 않는다.


## Agent skills

### Issue tracker
이슈 작업 시 `docs/agents/issue-tracker.md`를 읽습니다.
이슈는 GitHub 저장소 `kimsooin77/Sync`에서 관리합니다.

### Triage labels
이슈 분류 시 `docs/agents/triage-labels.md`의 기본 라벨 매핑을 사용합니다.

### Domain docs
코드 탐색 전에 `docs/agents/domain.md`의 문서 읽기 규칙을 따릅니다.
루트 `CONTEXT.md`와 `docs/adr/`를 사용하는 단일 컨텍스트 구조입니다.
