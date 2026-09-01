# ForgeSync 엔지니어링 및 아키텍처 원칙

## 1. 설계 목표

ForgeSync의 핵심 품질은 화려한 3D 자체가 아니라 실제 제조 데이터의 의미, 시간, 순서, 출처를 신뢰할 수 있는 Twin State로 만드는 것이다. 설계는 다음 변화가 서로를 불필요하게 흔들지 않도록 해야 한다.

- NIST 입력 형식이나 전송 방식의 변화
- 저장소와 프레임워크의 변화
- 생산, 알람, 정비 규칙의 변화
- REST/WebSocket/2D/3D 표현의 변화
- Condition Intelligence 모델의 교체

MVP는 분산 마이크로서비스가 아니라 **명시적인 모듈 경계를 가진 Modular Monolith + 독립 Edge/AI Adapter**를 기본으로 한다. 런타임이 나뉘어도 도메인 경계를 네트워크 구조와 동일시하지 않는다.

## 2. Clean Architecture 의존성 규칙

```text
Framework / UI / DB / MQTT
           ↓ implements
Adapters (inbound/outbound)
           ↓ depends on
Application (use cases, ports, transaction orchestration)
           ↓ depends on
Domain (entities, value objects, policies, domain events)
```

의존성은 안쪽을 향한다.

- Domain은 Spring, JPA, MQTT, HTTP, JSON, React를 알지 못한다.
- Application은 유스케이스를 조율하지만 제조 규칙을 대신 구현하지 않는다.
- Inbound Adapter는 입력을 검증하고 Command/Query로 변환한다.
- Outbound Adapter는 Repository, Clock, Publisher, Source Reader 같은 Port를 구현한다.
- DTO와 영속 모델을 Domain Entity로 재사용하지 않는다.
- 3D 컴포넌트는 backend DTO를 직접 소비하지 않고 `MachineVisualState` Adapter를 거친다.

허용되지 않는 예:

```text
Controller → JPA Repository → 화면용 DTO 조립
React component → backend DTO 해석 + freshness 규칙 계산
Domain Entity → MQTT publish / SQL 실행
AI response → Machine FAULT 직접 변경
```

## 3. Bounded Context와 소유권

| Context | 소유하는 개념 | 다른 Context와의 통신 |
|---|---|---|
| Source Acquisition | SourceArtifact, RawRecord, source manifest | 검증된 원본/레코드를 Ingestion에 제공 |
| Ingestion | ObservationEnvelope, Inbox, semantic mapping | 수락된 Observation과 품질 결과 발행 |
| Replay | ReplaySession, ReplayClock, ReplaySequence | 원천 시간은 보존하고 재생 시간만 제어 |
| Equipment Twin | MachineIdentity, EquipmentState, TwinVersion, Freshness | 권위 있는 현재 상태 Query 제공 |
| Production | ProductionRequest, WorkOrder, Routing, OperationExecution, ProductionResult | Machine은 ID로 참조하며 telemetry와 결과를 자동 동일시하지 않음 |
| Alarm | Alarm, AlarmRule, Acknowledgement | Condition/Data Quality/Advisory 이벤트를 명시적 규칙으로 변환 |
| Maintenance | MaintenanceRequest, Assignment | Alarm과 연결할 수 있으나 독립 생명주기 유지 |
| Intelligence | FeatureSchema, ModelMetadata, Advisory | 조언만 제공하며 설비 제어 권한 없음 |
| Presentation | 2D ViewModel, MachineVisualState, Scene State | Twin을 표현하며 truth source가 되지 않음 |

Context 간에는 공개 Application Port, 명시적 이벤트, 버전이 있는 계약만 사용한다. 다른 Context의 내부 Repository나 Entity를 직접 참조하지 않는다.

## 4. DDD 모델링 규칙

### Entity와 Aggregate

- 생명주기와 식별자가 중요하면 Entity로 만든다.
- 한 트랜잭션에서 반드시 일관되어야 하는 범위만 Aggregate로 묶는다.
- Aggregate Root만 외부에서 변경할 수 있다.
- `MachineTwin`, `ReplaySession`, `OperationExecution`, `Alarm`, `MaintenanceRequest`를 거대한 하나의 Aggregate로 합치지 않는다.
- Aggregate 간 참조는 객체 그래프 대신 안정적인 ID를 기본으로 한다.

### Value Object

시간, 식별자, 단위, 출처, 상태 전이를 primitive로 흩뿌리지 않는다. 예:

```text
MachineId
SourceEventKey
ReplaySessionId
ObservationTime
MetricValue(value, unit)
Provenance(source, evidenceState)
TwinVersion
FreshnessWindow
```

Value Object는 생성 시 불변식을 검증하고 가능하면 불변으로 유지한다.

### Domain Service와 Policy

Entity 하나의 책임이 아닌 순수 도메인 규칙만 Domain Service/Policy로 둔다.

- `ObservationOrderingPolicy`
- `FreshnessPolicy`
- `ConditionToAlarmPolicy`
- `OperationTransitionPolicy`

이름이 `Manager`, `Helper`, `Utils`, `CommonService`인 범용 클래스는 책임이 불명확하다는 신호다.

### Domain Event

Business Event와 raw telemetry를 구분한다. Outbox 대상은 `ALARM_CREATED`, `OPERATION_COMPLETED` 같은 의미 있는 상태 변화이며 모든 RPM 샘플이 아니다.

## 5. 핵심 불변식

- 저장된 Raw Artifact는 수정하지 않는다. 재처리는 새 Processing Run으로 남긴다.
- Canonical 변환 실패가 Raw 보존 실패를 의미하지 않는다.
- `Sample`, `Event`, `Condition` 의미를 서로 바꾸지 않는다.
- 알 수 없는 DataItem을 추측 매핑하지 않는다.
- 과거/순서가 낮은 Observation은 이력에는 저장할 수 있지만 최신 Twin을 rollback하지 않는다.
- Inbox 중복 키는 `(replaySessionId, sourceEventKey)`다.
- Source time과 Replay time을 덮어쓰거나 혼합하지 않는다.
- `Condition != Alarm`, `Advisory != Fault`, `Telemetry PartCount != ProductionResult`다.
- 3D는 Projection이며 상태를 쓰는 주체가 아니다.
- STALE 상태에서 live처럼 보이는 애니메이션을 지속하지 않는다.
- WebGL/asset/AI 장애가 2D 핵심 운영 기능을 중단시키지 않는다.
- 실제 PLC/CNC/Safety PLC 제어는 MVP 경계 밖이다.

## 6. 패키지와 모듈 구조

언어별 문법은 달라도 동일한 구조적 의미를 유지한다.

```text
<context>/
├── domain/
│   ├── model/
│   ├── policy/
│   └── event/
├── application/
│   ├── port/in/
│   ├── port/out/
│   └── service/
└── adapter/
    ├── in/
    └── out/
```

Frontend feature도 전역 `components`, `utils`, `services`에 모든 것을 섞지 않는다.

```text
features/twin/{domain,application,adapters,ui}
features/factory3d/{visual-state,scene,components}
features/replay/{domain,application,ui}
```

공유 코드는 둘 이상의 실제 소비자가 생기고 의미가 안정된 뒤 추출한다.

## 7. 객체지향 설계 규칙

- Tell, Don't Ask: 상태를 꺼내 외부에서 판단하기보다 객체가 유효한 행동을 수행하게 한다.
- 불가능한 상태를 표현하기 어렵게 만든다. 상태 전이는 명명된 메서드로 제한한다.
- 상속보다 조합을 우선한다. 교체 가능한 정책과 Adapter는 작은 인터페이스로 둔다.
- 인터페이스는 소비자 관점에서 작게 정의한다. 구현 클래스마다 기계적으로 인터페이스를 만들지 않는다.
- 변경 이유가 다른 파싱, 영속화, projection, 표현 로직을 분리한다.
- 도메인 객체에 setter를 공개하지 않는다.
- boolean 인자가 행동을 바꾸면 의도를 드러내는 메서드나 타입으로 분리한다.

예:

```text
나쁨: operation.updateStatus("ACTIVE", true)
좋음: operation.start(at, assignedMachineId)

나쁨: freshnessService.check(dto)
좋음: freshnessPolicy.classify(lastProjectedAt, now)
```

## 8. 아키텍처 검증

아키텍처 규칙도 테스트 대상이다.

- Java: ArchUnit으로 Domain의 framework 의존 금지와 Context 간 내부 package 접근 금지를 검증한다.
- Python: import-linter 또는 동등한 정적 검사로 domain → adapter 역의존을 막는다.
- TypeScript: ESLint boundaries 규칙으로 feature 내부 계층과 3D Adapter 경계를 검증한다.
- Contract schema는 producer와 consumer 양쪽 테스트에서 동일 fixture로 검증한다.
- ADR을 위반하는 예외는 PR 설명과 후속 제거 조건을 기록한다.

## 9. 변경 의사결정

다음 중 하나면 ADR을 추가하거나 기존 ADR을 갱신한다.

- 데이터 의미 또는 불변식이 바뀐다.
- Context 소유권이나 의존성 방향이 바뀐다.
- 새로운 영속/메시징/렌더링 기술을 도입한다.
- 신뢰성 보장 범위나 실패 처리 방식이 바뀐다.
- 실제/시뮬레이션/AI provenance 표현이 바뀐다.

단순 리팩터링, 이름 변경, 구현 세부사항은 ADR 대상이 아니다.
