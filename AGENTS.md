# ForgeSync 에이전트 개발 규칙

이 파일은 저장소 전체에 적용된다. 하위 디렉터리에 더 구체적인 `AGENTS.md`가 생기면 해당
디렉터리에서는 하위 파일의 규칙을 함께 적용한다. 구현 전에는 변경 대상과 직접 관련된 PRD,
ADR, 개발 Step, 데이터 계약을 먼저 읽는다.

## 1. 규칙의 우선순위

충돌 시 다음 순서로 판단한다.

1. 안전, 데이터 진실성, 원천 및 provenance 보존
2. `docs/ForgeSync_PRD_v1.4_Spatial_Digital_Twin.md`의 제품 범위와 사용자 행동
3. `docs/architecture/engineering-principles.md`와 `docs/adr/**`의 구조 및 결정
4. `docs/도메인 용어.md`의 Ubiquitous Language와 `docs/data/**`의 데이터 계약
5. `docs/development/implementation-steps.md`의 작업 순서와 완료 조건
6. `docs/testing/test-strategy.md`와 `docs/development/coding-and-review-guide.md`

문서와 코드가 다르면 추측으로 한쪽을 정답으로 만들지 않는다. 데이터 의미, Context 소유권,
public contract, 신뢰성 보장이 걸린 충돌은 ADR 후보로 기록하고 결정을 요청한다. 검증하지 않은
외부 사실이나 성능 수치를 새로 주장하지 말고 `docs/verification-ledger.md`에 근거와 상태를 남긴다.

## 2. 작업 방식과 완료 조건

변경은 리뷰 가능한 작은 vertical behavior 단위로 끝낸다. 테스트 없는 DTO, Repository,
Controller만 따로 추가하는 수평 분할을 피한다.

1. 관련 문서와 기존 구현을 확인하고 인수 조건을 Given/When/Then 수준으로 고정한다.
2. 먼저 실패하는 테스트 또는 명시적인 재현 시나리오를 만든다.
3. 요구를 충족하는 최소 구현을 작성한다.
4. 책임과 계층 경계를 리팩터링하고 정적/아키텍처 검사를 수행한다.
5. 가장 가까운 테스트부터 관련 전체 suite 순으로 실행한다.
6. 계약, 운영 방법, 근거 또는 결정이 달라졌다면 같은 변경에서 문서와 Ledger/ADR도 갱신한다.

정상 경로와 최소 한 개의 경계값 또는 실패 경로를 검증해야 한다. 테스트를 삭제하거나 assertion을
약화해 구현 결함을 숨기지 않는다. 실행하지 않은 검사는 통과했다고 보고하지 않는다.

## 3. 아키텍처 경계

MVP 기본 구조는 **명시적인 모듈 경계를 가진 Modular Monolith + 독립 Edge/AI Adapter**다.
서비스 배포 단위와 도메인 경계를 동일시하거나 새 마이크로서비스, Kafka, Kubernetes 같은 범위 밖
인프라를 임의로 추가하지 않는다.

의존성은 항상 안쪽을 향한다.

```text
Framework / UI / DB / MQTT
          -> Adapter
          -> Application (use case, port, transaction orchestration)
          -> Domain (entity, value object, policy, domain event)
```

- Domain은 HTTP, JSON, DB/ORM, MQTT, UI 및 특정 framework를 알지 않는다.
- Application은 use case를 조율하고 Port를 정의하지만 제조 도메인 규칙을 대신 구현하지 않는다.
- Inbound Adapter는 경계 입력을 검증해 Command/Query로 변환한다.
- Outbound Adapter는 Repository, Clock, Publisher, SourceReader 등의 Port를 구현한다.
- DTO, persistence model, Domain Entity, ViewModel을 각각 분리한다.
- 다른 Bounded Context의 내부 Entity나 Repository를 직접 참조하지 않는다. 공개 Application Port,
  명시적 domain/business event, versioned contract만 사용하고 Aggregate 간에는 안정적인 ID로 참조한다.
- interface는 소비자 관점의 작은 Port로 만든다. 구현마다 기계적으로 interface를 만들지 않는다.
- 공유 코드는 실제 소비자가 둘 이상이고 의미가 안정된 뒤 추출한다. 범용 `utils`, `common`,
  `base`, `manager`, `helper`에 책임을 모으지 않는다.

Context 소유권은 다음과 같다.

- Source Acquisition: `SourceArtifact`, `RawRecord`, source manifest
- Ingestion: `ObservationEnvelope`, Inbox, semantic mapping
- Replay: `ReplaySession`, `ReplayClock`, `ReplaySequence`
- Equipment Twin: `MachineIdentity`, `EquipmentState`, `TwinVersion`, Freshness
- Production: request/work order/routing/operation/result
- Alarm과 Maintenance: 서로 독립적인 생명주기
- Intelligence: feature/model metadata와 Advisory만 소유하며 설비 제어 권한 없음
- Presentation: 2D/3D projection이며 truth source가 아님

새 Context는 가능한 한 다음 의미를 유지한다.

```text
<context>/
  domain/{model,policy,event}
  application/{port/in,port/out,service}
  adapter/{in,out}
```

Frontend는 `features/<feature>/{domain,application,adapters,ui}`로 기능 경계를 유지한다. 3D는 backend
DTO를 직접 해석하지 않고 `MachineVisualState` Adapter를 거친다. WebGL, asset 또는 3D 오류가 2D
핵심 기능을 중단시키면 안 된다.

## 4. 도메인과 데이터 불변식

아래 규칙은 편의를 위해 우회할 수 없다.

- 외부 byte는 canonical 변환 전에 immutable `SourceArtifact`로 보존하고 길이, SHA-256, 출처,
  수집 시각, 라이선스/조건과 evidence state를 manifest에 기록한다.
- L0 SourceArtifact, L1 RawRecord/Catalog, L2 Canonical Observation, L3 Projection은 별도 계층이다.
  뒤 단계가 앞 단계 파일이나 레코드를 덮어쓰지 않는다.
- parse/mapping 실패와 unknown DataItem도 원본 및 locator와 함께 보존·계수한다. 모르는 의미,
  단위, sequence, timestamp를 추측하거나 만들어내지 않는다.
- 재처리는 기존 artifact를 수정하지 않고 새 `processingRunId`/`mappingVersion` 결과로 남긴다.
- `Sample`, `Event`, `Condition` category를 서로 바꾸지 않는다.
- `Condition != Alarm`, `Advisory != Fault`, `Telemetry PartCount != ProductionResult`다.
- Canonical 결과는 `rawRecordId`, mapping version, provenance로 원본까지 추적 가능해야 한다.
- `sourceObservedAt`, `replayPublishedAt`, `ingestedAt`, `projectedAt`의 의미를 섞거나 덮어쓰지 않는다.
- 동일 replay session의 순서는 `replaySequence`, 이후 `sourceObservedAt`, 마지막으로
  `sourceEventKey`를 사용한다. 과거 observation은 이력에 저장할 수 있지만 현재 Twin을 rollback하지 않는다.
- Inbox 중복 identity는 `(replaySessionId, sourceEventKey)`이며 business side effect는 idempotent해야 한다.
- MQTT QoS1은 at-least-once다. 선언된 DB transaction 경계의 effectively-once만 주장하고 global
  exactly-once를 주장하지 않는다. Outbox는 raw telemetry가 아니라 의미 있는 business event에 쓴다.
- 여러 source를 합친 결과는 field-level provenance를 유지한다. `REAL:NIST`, simulation, AI,
  `UNKNOWN`을 명확히 구분하고 NIST 설비와 별도 AI dataset을 같은 실제 장비 데이터처럼 표현하지 않는다.
- Freshness는 historical source time이 아니라 최신 projected/received wall clock으로 판단한다.
  STALE이면 live처럼 보이는 animation을 멈추거나 약화한다.
- 3D는 권위 있는 Twin의 projection일 뿐 상태를 쓰지 않는다.
- MVP command target은 Virtual Controller뿐이다. 실제 CNC, PLC, Safety PLC 또는 interlock 제어를
  추가하지 않는다.

생명주기와 identity가 중요하면 Entity, 생성 시 검증되는 불변 값은 Value Object로 모델링한다.
한 transaction에서 반드시 일관되어야 하는 범위만 Aggregate로 묶고 Aggregate Root의 명명된 행동을
통해서만 상태를 바꾼다. 공개 setter와 boolean mode 인자를 피하고, 순수한 다중-Entity 규칙은
`OrderingPolicy`, `FreshnessPolicy`처럼 의도가 드러나는 Domain Policy로 둔다.

## 5. 코드 스타일과 네이밍

- 이름은 `docs/도메인 용어.md`의 Ubiquitous Language를 사용한다. 같은 개념에 `job`, `task`, `operation` 같은
  동의어를 섞지 않는다.
- 함수/use case는 구체적인 행동으로, Adapter는 기술과 역할로 이름 짓는다. `process(data)`,
  `handle()`, `execute(flag)`보다 `preserve_source_artifact`, `StartReplay`처럼 의도를 드러낸다.
- boolean은 질문처럼(`is_stale`, `has_version_gap`, `can_transition_to`), collection은 복수형으로 쓴다.
- 시간과 단위는 이름 또는 타입에 명시한다: `source_observed_at`, `timeout_ms`, `Duration`.
- 한 함수는 한 가지 의도와 한 수준의 추상화를 유지한다. parsing/validation/storage/publish,
  도메인 전이/기술 오류 처리를 한 함수에 섞지 않는다.
- 함수가 약 20줄, 인자 4개, cyclomatic complexity 10을 넘으면 분리를 검토한다. 예외에는 변경
  설명에 이유와 대안을 남긴다.
- guard clause, Value Object, Policy, Port 순으로 복잡도를 낮춘다. 상속보다 조합을 우선한다.
- 주석과 docstring은 코드의 동작을 반복하지 않고 제약의 이유와 근거를 설명한다.
- 외부 오류는 Adapter에서 안정적인 application/domain 오류로 번역한다. catch 후 무시하지 말고
  retry 가능 여부와 실패 상태를 명확히 한다.
- 로그는 machine/event/replay/correlation identity를 구조화한다. secret이나 전체 raw payload를
  기본 출력하지 않는다.
- roadmap의 `Step 01` 같은 번호를 `docs/**` 밖의 package, symbol, test, CLI, config, 경로,
  fixture, API/DB/MQTT/log/metric/runtime identity에 사용하지 않는다.
- 미래 요구를 추측한 추상화나 현재 요청과 무관한 대규모 정리를 함께 수행하지 않는다.

언어별 표기는 생태계 표준을 따른다.

- Python: type `PascalCase`, 함수/변수/module `snake_case`, 상수 `UPPER_SNAKE_CASE`.
  Python 3.12+ 문법, type annotation, immutable `@dataclass(frozen=True, slots=True)`를 적절히 사용한다.
- Java: type `PascalCase`, method/field `camelCase`, 상수 `UPPER_SNAKE_CASE`.
- TypeScript/React: component/type `PascalCase`, 함수/값 `camelCase`, hook `useXxx`.
- JSON 및 외부 contract의 field casing은 versioned schema를 따른다. 내부 언어 관례 때문에 public
  contract를 암묵적으로 바꾸지 않는다.

## 6. 테스트 가능한 설계와 검증 명령

- wall clock을 직접 읽지 말고 `Clock` Port를 주입한다. UUID/sequence도 결정 가능한 generator를 주입한다.
- network/DB/file I/O와 순수 parsing/mapping/policy를 분리한다.
- 테스트는 Arrange/Act/Assert 또는 Given/When/Then 구조로 공개 행동과 관찰 가능한 결과를 검증한다.
  private 호출 순서나 mock 호출 횟수만 검증하지 않는다.
- fixture는 최소 크기로 만들되 source, checksum, 축약/변형 여부와 기대 의미를 기록한다. 실제 원본을
  변형한 fixture는 `DERIVED_FIXTURE`로 표시한다.
- duplicate, out-of-order, malformed/unknown, stale, partial failure와 contract version mismatch 중
  변경과 관련된 경계를 반드시 검증한다.
- producer와 consumer는 복제 DTO가 아니라 동일한 versioned schema와 fixture로 contract를 검증한다.

전체 저장소의 기본 품질 명령은 루트에서 실행한다.

```bash
./scripts/verify
```

이 명령은 lockfile에 고정된 의존성을 설치하고 Python, Java, TypeScript의 format/lint/type/compile,
unit, architecture test와 build를 실행한다. `pyproject.toml`이 Python 공통 기준이며 현재 Python은
`>=3.12,<3.15`, Ruff line length는 100, mypy는 strict다. formatter를 적용해야 할 때는 변경 파일에
`uv run ruff format <paths...>`를 실행한 뒤 root 검증을 다시 수행한다. 외부 네트워크, 실제 dataset
또는 wall clock에 의존하는 flaky test를 만들지 않는다.

GitHub Workflow에 품질 검사를 중복 구성하지 않는다. 에이전트는 변경을 push하기 직전에 반드시
`./scripts/verify`를 직접 실행하고, 실패하면 push하지 않는다. 최종 보고에는 실행 결과와 실행하지
못한 검사가 있다면 그 이유를 명시한다.

## 7. 계약, 보안, 결정 변경

- 모든 경계 입력에 schema와 값 검증을 적용하고 SQL은 parameter binding을 사용한다.
- secret을 저장소에 넣지 않는다. CORS는 명시적으로 제한하고 command는 allowlist로 통제하며
  container는 가능한 non-root로 실행한다.
- public contract 비호환 변경, 데이터 삭제/migration, provenance 의미 변경, Bounded Context 소유권
  변경, 새 외부 서비스/라이브러리/인프라, 신뢰성 보장 또는 MVP 범위 확대는 임의로 결정하지 않는다.
- 데이터 의미/불변식, 의존성 방향, 주요 기술, 실패 처리, provenance 표현이 바뀌면 ADR을 추가하거나
  갱신한다. 단순 rename과 내부 refactoring은 ADR 대상이 아니다.
- 라이선스가 확인되지 않은 대용량 원본과 asset을 커밋하지 않는다. 원본을 Git에서 제외할 때도 pinned
  URI/commit, checksum, manifest, 재현 절차와 attribution은 버전 관리한다.

## 8. 변경 완료 전 체크리스트

- 변경이 인수 조건과 관련 PRD/ADR을 충족하는가?
- Domain 안쪽으로 의존하고 Context 내부를 우회하지 않는가?
- raw, canonical, projection과 각 시간/provenance가 분리되어 있는가?
- 정상 및 실패/경계 테스트가 있고 실제로 실행했는가?
- lint, format, type, architecture/contract 검사를 변경 범위에 맞게 실행했는가?
- 사용자에게 보이는 행동이 인수 조건 또는 E2E 시나리오로 이어지는가?
- 코드와 문서, schema, fixture, Verification Ledger가 서로 일치하는가?
- 검증하지 않은 사실, 정확도, 성능, 안전성 또는 exactly-once 주장을 추가하지 않았는가?
