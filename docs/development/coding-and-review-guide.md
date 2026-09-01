# 코딩, 네이밍, 리뷰 및 에이전트 수정 가이드

## 1. 코드의 기본 형태

코드는 읽는 사람이 제조 도메인 문장을 따라갈 수 있어야 한다. 짧게 만드는 것보다 책임과 의도가 선명한 것이 우선이며, 불필요한 추상화와 중복도 함께 경계한다.

```text
나쁨: process(data), handle(), execute(flag), utils.convert(x)
좋음: preserveSourceArtifact(bytes, manifest)
      mapRawRecordToObservation(record, catalog)
      projectIfNewer(observation, currentTwin)
      acknowledgeAlarm(alarmId, operatorId, acknowledgedAt)
```

## 2. 메서드 분리 규칙

메서드는 한 가지 의도와 한 수준의 추상화를 가진다. 다음 신호가 있으면 분리를 검토한다.

- 이름에 `and`, `or`가 필요하다.
- 파싱, 검증, 저장, publish가 한 메서드에 섞였다.
- 조건 분기가 도메인 상태 전이와 기술 오류 처리를 동시에 다룬다.
- 긴 주석이 코드 블록의 목적을 대신 설명한다.
- 동일 인자 묶음이 반복된다.
- 테스트하려면 DB, network, clock을 모두 띄워야 한다.

절대적인 줄 수가 설계를 결정하지는 않지만 기본 리뷰 경고선은 다음과 같다.

- 메서드 20줄 안팎을 넘으면 책임을 다시 확인한다.
- 인자 4개를 넘으면 Command/Value Object가 더 명확한지 검토한다.
- cyclomatic complexity 10을 넘으면 Policy, guard clause, 상태 객체로 분리한다.
- class가 서로 무관한 public method나 여러 변경 이유를 가지면 분리한다.

예외는 가능하지만 PR에서 이유와 대안을 설명해야 한다.

## 3. 네이밍 규칙

- Domain 용어는 PRD와 ubiquitous language를 그대로 사용한다: `Observation`, `ReplaySession`, `Twin`, `OperationExecution`, `Advisory`.
- 같은 개념에 `job`, `task`, `operation` 같은 동의어를 섞지 않는다.
- boolean은 질문처럼 읽힌다: `isStale`, `hasVersionGap`, `canTransitionTo`.
- Collection은 복수형, 단일 값은 단수형으로 쓴다.
- 단위를 이름이나 타입에 드러낸다: `timeoutMs` 또는 `Duration`, `spindleRpm` 또는 `SpindleSpeed`.
- 시간은 의미를 포함한다: `sourceObservedAt`, `ingestedAt`, `projectedAt`.
- `data`, `info`, `item`, `result`, `temp`, `common`, `base` 같은 넓은 이름은 구체화한다.
- Adapter는 기술과 역할을 드러낸다: `MqttObservationPublisher`, `PostgresTwinRepository`.
- Use case는 행동으로 명명한다: `StartReplay`, `AcknowledgeAlarm`, `AssignOperation`.

언어 표기:

- Java: type `PascalCase`, method/field `camelCase`, constant `UPPER_SNAKE_CASE`.
- Python: type `PascalCase`, function/variable `snake_case`, constant `UPPER_SNAKE_CASE`.
- TypeScript/React: component/type `PascalCase`, function/value `camelCase`, hook `useXxx`.
- 파일명은 각 생태계 표준을 따르되 한 앱 안에서 혼용하지 않는다.

### Roadmap 번호 비누출

`Step 01`과 같은 roadmap 번호는 개발 문서의 순서 표시에만 사용한다. 실제 구현의 이름이나 runtime 데이터로 노출하면 문서 재정렬이 곧 호환성 변경이 되므로 다음 위치에서는 사용을 금지한다.

```text
source/test package와 symbol
CLI와 configuration
파일, 디렉터리, fixture
API, DB, MQTT
환경변수, log, metric
artifact/profile/run identity
```

리뷰 시 구현 이름이 “몇 번째 작업인가”가 아니라 “어떤 도메인 의미와 원천을 가지는가”를 설명하는지 확인한다. 이 규칙은 `docs/**` 바깥의 정적 naming boundary test로 보호한다.

## 4. 주석, 오류, 로그

- 주석은 코드가 무엇을 하는지 반복하지 않고 왜 이 제약이 존재하는지 설명한다.
- 도메인 오류는 `InvalidOperationTransition`, `ObservationOutOfOrder`처럼 의미 있는 타입/코드를 사용한다.
- 외부 오류를 domain exception으로 위장하지 말고 Adapter에서 번역한다.
- catch 후 무시하지 않는다. retry 가능 여부와 실패 상태를 명확히 한다.
- 로그에는 correlation/replaySession/event/machine 식별자를 구조화해 넣고, 전체 raw payload와 secret은 기본 출력하지 않는다.

## 5. 테스트 가능한 설계

- 현재 시간은 직접 읽지 않고 `Clock` Port로 받는다.
- UUID/sequence 생성은 결정 가능한 generator를 주입한다.
- network/DB 호출과 순수 변환을 분리한다.
- 상태 전이는 공개 도메인 행동으로 테스트한다.
- private method를 직접 테스트하기보다 책임이 독립적이면 별도 객체로 추출한다.
- mock 호출 수만 확인하지 말고 결과 상태와 외부 계약을 함께 검증한다.

## 6. 복잡도 증가 시 리팩터링 순서

1. 실패하는 characterization test로 현재 동작을 고정한다.
2. 도메인 용어와 책임이 섞인 지점을 표시한다.
3. guard clause로 예외 경로를 평탄화한다.
4. Value Object와 Policy로 primitive/조건 로직을 이동한다.
5. I/O와 순수 계산을 Port 경계로 분리한다.
6. 중복은 의미가 동일한지 확인한 뒤 제거한다.
7. 전체 관련 테스트와 architecture test를 다시 실행한다.

패턴 이름을 맞추기 위한 리팩터링은 하지 않는다. 변경 이유가 분리되고 테스트 가능성이 개선되는지가 기준이다.

## 7. 리뷰 체크리스트

### Correctness

- 인수 조건과 도메인 불변식을 충족하는가?
- duplicate, out-of-order, missing/unknown, stale 경로가 안전한가?
- source/replay/wall-clock time을 혼합하지 않았는가?

### Architecture

- 의존성이 Domain 안쪽을 향하는가?
- Bounded Context의 내부 모델을 우회해 접근하지 않았는가?
- DTO, persistence model, Domain Entity, ViewModel이 분리되어 있는가?
- 3D/AI/DB/MQTT 장애가 핵심 도메인에 새 결합을 만들지 않는가?

### Maintainability

- 이름만 읽고도 의도가 보이는가?
- 메서드가 한 수준의 추상화를 유지하는가?
- 새 추상화가 실제 변이점이나 중복을 해결하는가?
- 미래 요구를 추측한 범용화가 추가되지 않았는가?

### Truthfulness and Security

- field-level provenance가 남는가?
- REAL/SIM/AI를 혼동하거나 금지된 주장을 만들지 않는가?
- raw payload, secret, 내부 오류가 로그/API에 노출되지 않는가?
- 입력 schema, command allowlist, SQL binding을 지키는가?

### Tests

- 변경된 규칙의 정상/실패 테스트가 있는가?
- test가 구현 세부사항이 아니라 행동을 검증하는가?
- 관련 unit/contract/integration/E2E가 실제 실행되었는가?

## 8. 에이전트 리뷰 및 자동 수정 프로토콜

복잡한 변경은 구현자와 리뷰자의 역할을 분리해 다음 순서로 검토할 수 있다. 같은 에이전트가 수행하더라도 두 pass를 구분한다.

### Pass A — Review only

1. 변경된 파일과 연결된 PRD/ADR/Step을 확인한다.
2. diff를 데이터 흐름과 Context 경계 순서로 읽는다.
3. 심각도를 `BLOCKER`, `MAJOR`, `MINOR`, `NIT`로 분류한다.
4. 각 finding에 파일/위치, 위반 규칙, 실패 가능한 시나리오, 권장 수정을 기록한다.
5. 근거 없는 스타일 선호는 finding으로 만들지 않는다.

### Pass B — Safe fix

에이전트는 다음 범위에서 바로 수정할 수 있다.

- 명백한 naming/메서드 분리/중복 제거
- 누락된 unit/contract test 추가
- 계층 역의존 제거와 Adapter 추출
- 오류 처리, boundary validation, provenance 누락 보완
- 기존 PRD/ADR로 답이 확정된 동작 수정

다음은 임의 수정하지 않고 결정을 요청하거나 ADR 후보로 남긴다.

- public contract의 비호환 변경
- data 삭제/migration 또는 provenance 의미 변경
- Bounded Context 소유권 변경
- 외부 서비스/라이브러리/인프라 추가
- 신뢰성 보장 또는 MVP 범위 확대

수정 후에는 가장 가까운 테스트부터 전체 관련 suite 순으로 실행하고, 고친 finding과 남은 위험을 보고한다. 테스트를 실행하지 못했으면 통과했다고 표현하지 않는다.

## 9. Step/PR 템플릿

```markdown
### 목적
사용자/도메인 관점의 한 문장

### 범위
- 변경할 Context와 public contract
- 범위 밖 항목

### 인수 조건
- Given / When / Then

### 테스트
- 먼저 추가할 실패 테스트
- 실행 명령과 결과

### 아키텍처 영향
- Port/Adapter/Domain 변경
- ADR 필요 여부

### Provenance / Data 영향
- 원본, 파생물, migration, 보존 정책

### 위험 및 rollback
- 실패 모드와 되돌리는 방법
```
