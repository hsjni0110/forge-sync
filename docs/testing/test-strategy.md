# ForgeSync 테스트 전략

## 1. 원칙

테스트는 구현이 끝난 뒤 추가하는 활동이 아니라 각 Step의 산출물이다. 테스트가 없는 Step은 완료되지 않았다.

- 가장 작은 규칙은 빠른 단위 테스트로 검증한다.
- Adapter 경계는 실제 schema/protocol에 가까운 계약 테스트로 검증한다.
- DB transaction, MQTT, REST/WebSocket은 통합 테스트로 검증한다.
- 사용자의 핵심 여정은 소수의 결정적인 E2E 테스트로 검증한다.
- 3D의 의미 mapping은 unit/component test로, 실제 표시 여부는 E2E/visual test로 나눈다.
- 외부 네트워크와 wall clock에 의존하는 flaky test를 만들지 않는다. Source, Clock, ID Generator를 주입한다.

## 2. 테스트 계층

| 계층 | 검증 대상 | 대표 도구/환경 | 실행 시점 |
|---|---|---|---|
| Domain Unit | 상태 전이, 불변식, ordering, freshness, mapping policy | 언어별 unit runner | 모든 변경 |
| Application Unit | use case orchestration, port 호출, 오류 mapping | fake port/clock | 모든 변경 |
| Contract | Observation/Twin/WebSocket schema, producer-consumer 호환 | shared fixtures/schema validator | 모든 변경 |
| Adapter Integration | file, PostgreSQL/TimescaleDB, MQTT, HTTP | 임시 디렉터리/Testcontainers/Compose | PR |
| Component | React 상태와 접근성, 3D fallback/visual adapter | DOM/WebGL mock 또는 test renderer | PR |
| E2E | NIST replay → 2D/3D Twin, reconnect, 업무 흐름 | 실제 runtime 묶음 | PR 핵심/야간 |
| Non-functional | 성능, 장애 복구, 보안, visual regression | browser/containers/scanner | milestone/릴리스 |

## 3. 테스트 작성 규칙

테스트 이름은 행위와 결과를 설명한다.

```text
givenOlderObservation_whenProjecting_thenKeepsCurrentTwinVersion
rejects_transition_from_completed_to_active
shows_stale_badge_and_stops_animation_when_freshness_expires
```

각 테스트는 Arrange/Act/Assert 또는 Given/When/Then 구조를 유지한다. 한 테스트가 여러 독립 규칙을 검증하지 않는다. 구현의 private call 순서보다 공개된 행위와 관찰 가능한 결과를 검증한다.

## 4. Fixture 정책

```text
tests/fixtures/raw/          byte-for-byte source samples
tests/fixtures/canonical/    valid/invalid envelope examples
tests/fixtures/twin/         versioned snapshot/patch examples
tests/fixtures/contracts/    REST/WebSocket payload examples
```

- fixture에는 출처, 축약/변형 여부, checksum, 기대 의미를 기록한다.
- 운영 데이터 전체를 테스트에 복사하지 않는다. 의미 경계가 드러나는 최소 fixture를 사용한다.
- 실제 원본을 축약했다면 `DERIVED_FIXTURE`로 표시하고 변형 절차를 기록한다.
- snapshot은 의도를 읽기 어렵게 만드는 남용을 금지한다. 핵심 필드는 명시적으로 assert한다.

## 5. Context별 필수 테스트

### Source Acquisition / Ingestion

- 저장 전후 raw byte와 checksum 동일
- partial download가 완료 artifact로 등록되지 않음
- malformed/unknown record 보존 및 품질 지표 반영
- SAMPLE/EVENT/CONDITION category 유지
- schemaVersion 불일치 거절
- 동일 inbox key 중복 시 `SKIPPED_DUPLICATE`
- transaction 실패 시 Inbox만 남거나 projection만 갱신되는 부분 성공이 없음

### Replay / Ordering

- pause 중 source progression 정지
- 1x/10x/100x에서 순서 동일, 간격만 변경
- source time과 replay time 분리
- 같은 session에서는 replaySequence 우선
- 과거 observation 저장 가능, current Twin rollback 금지
- 고정 Clock으로 freshness 경계를 2초/10초에서 검증

### Equipment Twin / API

- observation에서 EquipmentState로의 mapping
- TwinVersion 단조 증가
- REST snapshot 전체를 담은 WebSocket patch의 base/target version 계약
- version gap/역행 감지 후 REST resync
- field-level provenance와 consistency state 노출

### Production / Alarm / Maintenance

- 허용/금지 상태 전이
- telemetry PartCount가 ProductionResult를 자동 확정하지 않음
- Condition은 규칙 없이 Alarm이 되지 않음
- Alarm ACK/RESOLVE idempotency
- Operation/Alarm/Maintenance business event가 한 번만 발생
- Advisory HIGH가 Equipment Health FAULT를 직접 만들지 않음

### Frontend 2D/3D

- Twin DTO → MachineVisualState mapping
- ACTIVE + rpm > 0일 때 animation 활성화
- STOPPED 또는 STALE에서 animation 정지
- warning/fault가 색 이외의 cue도 제공
- selection과 right panel의 machineId 일치
- asset 실패 시 fallback primitive
- WebGL 실패 시 2D Machine Detail/Replay/Alarm 기능 유지
- reduced-motion 설정에서 비필수 motion 감소

### Intelligence

- feature schema와 model metadata 일치
- cutter/cut group leakage 방지
- inference input validation
- 모델 또는 서비스 unavailable 시 `UNKNOWN`/unavailable 표현
- response에 model version, dataset provenance, confidence/limitation 포함
- 실제 설비 제어 Port에 접근할 수 없음을 아키텍처 테스트로 검증

## 6. 핵심 E2E 시나리오

### E2E-01 Real Data Vertical Slice

```gherkin
Given checksum이 고정된 NIST fixture와 replay session이 있고
When edge gateway가 fixture를 publish하면
Then API는 canonical observation을 저장하고
And Twin snapshot의 RPM, execution, tool, program을 갱신하고
And 2D와 MachineVisualState는 같은 Twin version과 provenance를 표시한다
```

### E2E-02 Duplicate and Ordering

```gherkin
Given 동일 sourceEventKey 이벤트를 100회 전달하고
When 더 오래된 observation도 뒤늦게 전달하면
Then observation history 정책에 따라 기록되며
And business side effect는 한 번이고
And current Twin은 rollback하지 않는다
```

### E2E-03 Disconnect and Resync

```gherkin
Given UI가 FRESH Twin을 표시하고
When WebSocket 연결이 끊기고 stale threshold가 지나면
Then RECONNECTING/STALE을 표시하고 animation을 멈추며
When 연결이 복구되면
Then REST snapshot을 먼저 동기화한 뒤 patch 구독을 재개한다
```

### E2E-04 3D Failure Isolation

```gherkin
Given GLB load 또는 WebGL 초기화가 실패하고
When 사용자가 Machine Detail, Replay, Alarm 화면을 사용하면
Then 3D unavailable 안내가 보이고
And 모든 2D 핵심 동작은 성공한다
```

## 7. 품질 게이트

### 각 commit/로컬

- 변경 범위 unit test
- lint/type check/format check
- schema/architecture test

### Pull Request

- 전체 unit + contract test
- 변경된 Adapter integration test
- 핵심 E2E 최소 1개 또는 변경과 무관하다는 근거
- flaky retry 없이 통과
- 새 도메인 규칙의 정상/실패 경로 포함

### Milestone/릴리스

- 네 가지 핵심 E2E 전체
- duplicate, out-of-order, MQTT/DB/AI/WebSocket/asset 장애 주입
- 브라우저 5/20 machine 성능 기록
- dependency/security scan
- 대표 상태 visual regression
- Verification Ledger와 Definition of Done 검토

초기 단계에서 숫자 coverage 목표를 성공 기준으로 삼지 않는다. 대신 변경된 도메인 규칙과 분기, 실패 경로의 테스트 누락을 허용하지 않는다. 측정 기반 baseline이 생기면 모듈별 mutation/branch coverage gate를 별도 ADR로 정한다.

## 8. 실패 테스트 처리

- 테스트를 삭제하거나 assertion을 약하게 만들어 통과시키지 않는다.
- 제품 규칙 변경이면 PRD/ADR/인수 조건을 먼저 갱신한다.
- flaky면 원인(clock, network, ordering, shared state)을 제거하고 격리 사유와 owner를 기록한다.
- 환경 장애와 제품 결함을 CI 결과에서 구분한다.
- E2E 실패 시 가장 가까운 contract/integration test를 추가해 재현 범위를 줄인다.
