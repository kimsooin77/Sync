# 포트폴리오 요약

## 프로젝트

Employee Lifecycle Sync — HR 직원 스냅샷을 내부 직원 정보와 비교하고 Groupware 계정 변경 작업 및 처리 이력을 관리하는 웹 애플리케이션

## 담당 역할

요구사항 정리부터 도메인 모델, Spring Boot API와 Worker, PostgreSQL/Flyway 스키마, React 관리자 화면, 통합 테스트 및 Docker 실행 구성을 구현했습니다.

## 문제

HR 변경을 내부 Employee에 반영하면서 Groupware API의 지연·실패가 내부 저장을 막지 않게 해야 했습니다. 응답 유실이나 프로세스 중단 뒤 작업을 안전하게 다시 실행하고, 운영자가 실패 원인과 호출 이력을 확인할 수 있어야 했습니다. 반복 동기화에서 변경 없는 직원의 개별 조회도 부담이었습니다.

## 해결

- Employee, SyncItem, AuditLog, IntegrationTask를 직원 단위 DB transaction으로 저장하고, 별도 Worker가 Task를 읽어 Groupware API를 호출하도록 분리했습니다.
- 자동 재시도와 Attempt 기록, 동일 idempotency key를 사용한 중복 처리 방지, stale PROCESSING 복구, FAILED 작업의 관리자 재시도를 구현했습니다.
- 관리자 UI에서 SyncJob, 직원 변경 이력, IntegrationTask와 Attempt Timeline, Mock Groupware 장애 규칙을 확인하도록 구성했습니다.
- 반복 동기화에서 정상화한 사번 1,000개 단위로 Employee를 bulk 조회합니다. 변경 후보는 직원별 transaction 안에서 다시 읽어 최신 값을 기준으로 처리합니다.

## 성과

Java 21.0.6과 PostgreSQL 17.11 Testcontainers에서 워밍업 후 3회 교차 측정한 중앙값 기준:

- 기존 10,000명 전체 SKIP: 27,172.516ms에서 18,332.157ms로 약 32.53% 감소. Employee 개별 조회는 10,000회에서 0회, bulk 조회는 10회였습니다.
- 1,000 UPDATE + 9,000 SKIP: 29,770.774ms에서 21,988.009ms로 약 26.14% 감소. Employee 개별 조회는 10,000회에서 1,000회로 감소했고 bulk 조회는 10회였습니다.

이 수치는 측정한 개발 환경에 한정됩니다. 직원별 transaction과 10,000개의 SyncItem 기록은 유지했습니다.

## 기술

Java 21, Spring Boot 4.1.x, Spring Data JPA, PostgreSQL 17.11, Flyway, Spring Security Session/CSRF, React, TypeScript, JUnit, Testcontainers, Gradle, Docker Compose, GitHub Actions

## 범위와 한계

실제 HR/Groupware 제품 연동은 없고 Mock API를 사용합니다. 앱 인스턴스와 Worker는 각각 단일 실행을 전제로 합니다. Session, Sync 실행 guard, Mock Groupware의 계정 및 idempotency 저장은 메모리 기반이므로 다중 인스턴스 운영을 보장하지 않습니다.
