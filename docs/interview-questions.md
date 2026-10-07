# 면접 예상 질문과 답변 포인트

## 설계와 외부 연계

### 왜 Kafka를 사용하지 않았나요?

현재는 단일 Spring 애플리케이션과 Worker로 구성했고, 직원 변경과 외부 연계 작업을 같은 PostgreSQL transaction에서 원자적으로 기록하는 요구가 중심입니다. IntegrationTask 테이블이 필요한 작업을 보존하고 별도 Worker가 이를 처리하므로 현재 범위에 브로커를 더할 이유가 적었습니다. 여러 consumer, 높은 처리량, 서비스 간 이벤트 전달이 요구되면 Kafka 같은 broker와 outbox 전달 구조를 검토할 수 있습니다.

### 왜 외부 HTTP 호출을 DB transaction 밖에서 하나요?

외부 응답을 기다리는 동안 DB transaction을 열면 연결과 잠금을 점유하고, timeout이 길어질수록 내부 트랜잭션도 불필요하게 늘어납니다. 먼저 Employee, SyncItem, AuditLog, IntegrationTask를 함께 commit한 뒤 Worker가 HTTP를 호출합니다. 외부 장애가 내부 직원 변경을 rollback시키지 않고, 저장된 Task로 추적·재시도할 수 있습니다.

### DB 저장은 성공했는데 Groupware 연계가 실패하면 어떻게 하나요?

Employee 변경과 IntegrationTask는 이미 commit되어 있습니다. Worker는 실제 호출 결과를 IntegrationAttempt로 저장하고, 재시도 가능한 실패면 RETRY_WAIT과 다음 실행 시각을 설정합니다. 예산을 다 쓰면 FAILED로 남기고 관리자가 기존 작업을 수동 재시도할 수 있습니다.

### idempotency key는 왜 필요한가요?

외부 업무 처리는 성공했지만 응답이 Worker에 도착하지 않을 수 있습니다. 같은 작업을 다시 호출할 때 키가 없으면 계정 생성이 반복될 수 있습니다. Task 생성 시 만든 UUID를 재시도와 수동 재시도에도 유지하며, Groupware가 이미 처리한 동일 키 요청에 기존 성공 결과를 반환하도록 계약했습니다.

### 응답 유실과 실제 업무 실패를 어떻게 구분하나요?

호출 측은 timeout만으로 원격 업무가 실행됐는지 판단할 수 없습니다. 그래서 실패 결과를 기록하고 같은 키로 재호출합니다. 원격 멱등 저장소가 같은 키의 업무를 중복 실행하지 않고 기존 결과를 반환해야 합니다. 현재 Mock은 이 동작을 인메모리로 보여주며 실제 Groupware의 보장은 별도 계약과 영속 저장이 필요합니다.

### PROCESSING 상태에서 서버가 중단되면 어떻게 하나요?

Worker는 PROCESSING 시작 시각을 저장합니다. 기본 60초보다 오래된 Task를 stale로 간주해 retry budget이 남으면 같은 payload와 key로 재실행하고, budget이 소진되면 추가 HTTP 호출 없이 `PROCESSING_RECOVERY_EXHAUSTED` 사유로 FAILED 처리합니다.

### 왜 Attempt를 가짜로 생성하지 않나요?

PROCESSING commit 뒤 HTTP 요청 전, 요청 전송 중, 응답 수신 후 결과 저장 전 어느 시점에서 종료됐는지 DB만으로 알 수 없습니다. 추정한 행을 실제 HTTP 결과처럼 기록하면 운영자가 호출 여부를 오해할 수 있습니다. Attempt는 결과를 확인해 저장할 수 있었던 실제 HTTP 호출에만 생성합니다.

### retryCount와 Attempt 번호는 왜 별도인가요?

retryCount는 자동 재실행 예산입니다. attemptNo는 저장된 외부 호출 결과 이력의 순번입니다. 중단 복구 과정에는 실제 HTTP 호출 없이 retryCount가 증가할 수 있으므로 두 값은 항상 같지 않습니다.

### 최대 재시도 횟수는 어떻게 정했나요?

현재 설정은 최초 호출 후 자동 재실행 3회, 대기 5초·15초·30초입니다. 일시적인 외부 장애를 몇 차례 흡수하면서 무한 재시도를 막기 위한 현재 프로젝트 설정입니다. 실제 운영값은 Groupware의 SLA, rate limit과 업무 긴급도에 따라 조정해야 합니다.

## 동기화와 성능

### 왜 직원별 transaction을 사용했나요?

전체 HR batch를 하나의 긴 transaction으로 묶으면 한 직원의 제약 오류나 지연이 전체 결과를 rollback시킬 수 있습니다. 직원 단위 transaction이면 Employee, SyncItem, AuditLog, IntegrationTask의 정합성을 직원별로 보장하면서 실패한 행을 격리하고 다음 직원을 계속 처리할 수 있습니다.

### 10,000개 transaction이 병목 아닌가요?

고정 비용이므로 비용이 남아 있고 측정 결과 역시 특정 환경에서만 유효합니다. 다만 전체 batch를 한 transaction으로 묶으면 실패 격리와 처리 시간이 나빠질 수 있어 현재 요구에서 직원별 경계를 유지했습니다. 10,000건 SKIP 시에는 개별 Employee SELECT를 10,000회에서 0회로 줄여 측정 중앙값이 약 32.53% 낮아졌습니다. SyncItem 저장과 직원별 transaction은 그대로이므로 추가 최적화 여지는 남아 있습니다.

### 왜 Bulk UPDATE까지 하지 않았나요?

현재 비교에서 측정 가능한 병목은 모든 직원의 개별 조회였습니다. Bulk UPDATE는 JPA 영속성 상태, AuditLog와 IntegrationTask 생성, 직원별 오류 격리와 일관성을 다시 설계해야 합니다. 먼저 조회 경로만 바꿔 결과를 측정했습니다. 더 큰 데이터에서 쓰기 비용이 병목임을 측정으로 확인하면 별도 설계 과제로 다룰 수 있습니다.

### 성능 측정은 어떻게 했나요?

Legacy 직원별 조회 경로와 bulk snapshot 경로를 같은 Spring Context와 PostgreSQL 17.11 Testcontainers 환경에서 비교했습니다. 각 구현을 워밍업한 뒤 3회 실행하고 순서를 교차했으며 중앙값을 기록했습니다. fixture 생성, DB 초기화, 결과 검증 쿼리는 측정과 SQL 집계에서 제외했습니다. 결과를 다른 장비나 데이터 분포의 성능 보장으로 일반화하지 않습니다.

## 운영과 확장

### 동시 HR Sync는 어떻게 막나요?

현재 프로세스 안의 SyncExecutionGuard를 이용해 한 인스턴스에서 동시에 하나의 HR Sync만 허용합니다. 두 번째 요청은 SyncJob을 만들지 않고 409를 반환합니다. 이 guard는 메모리 기반이라 여러 인스턴스를 함께 운영할 때는 동작하지 않습니다.

### 여러 서버로 확장하려면 무엇을 바꾸나요?

분산 환경에서 동기화 중복을 막는 분산 잠금 또는 DB 기반 claim/lease가 필요합니다. Session은 공유 저장소나 stateless 인증으로 바꾸고, Mock이 아닌 Groupware의 idempotency 결과를 영속화해야 합니다. Worker는 작업 claim과 잠금 만료, 중복 실행 방지 등 다중 worker 동시성 제어가 필요합니다.

### Redis를 넣지 않은 이유는 무엇인가요?

현재 범위에서는 필수 구성 요소가 아니며, Task의 원본과 감사 이력을 PostgreSQL에 둡니다. 분산 잠금이나 공유 Session 요구가 생기면 Redis를 선택지로 평가할 수 있지만, 현재 단일 인스턴스 데모에 미리 추가하지 않았습니다.

### 현재 프로젝트의 한계는 무엇인가요?

실제 외부 제품과 연결하지 않은 Mock 연동이고, 단일 애플리케이션과 Worker, 메모리 기반 Session·guard·Mock idempotency를 전제로 합니다. 대량 SKIP 최적화도 반복 개별 조회에 한정하며 SyncItem 저장과 직원별 transaction은 유지합니다.
