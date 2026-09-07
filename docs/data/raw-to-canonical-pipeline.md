# Raw 데이터에서 Human-readable Twin까지

## 1. 목적

원천 데이터를 먼저 **그대로 보존**하고, 그 이후의 파싱·정규화·도메인 projection을 언제든 재현할 수 있게 한다. 파서 버그나 schema 변경이 발생해도 원본을 다시 내려받거나 추측으로 복구하지 않아야 한다.

```text
External Source
  → Immutable SourceArtifact
  → RawRecord / Source Catalog
  → Canonical ObservationEnvelope
  → Operational Projection
  → Human-readable API / 2D / 3D
```

각 화살표는 독립된 변환 경계다. 앞 단계의 내용을 뒤 단계 형식으로 덮어쓰지 않는다.

## 2. 데이터 계층

### L0. SourceArtifact

다운로드하거나 수집한 원본 byte를 변경 없이 저장한다.

필수 manifest:

```text
artifactId
sourceName
sourceUri
retrievedAt
mediaType
byteLength
sha256
licenseOrTermsReference
evidenceState
collectorVersion
```

- 저장 경로 예: `datasets/raw/nist/<artifactId>/payload`
- sidecar 예: `datasets/raw/nist/<artifactId>/manifest.json`
- 파일명, 줄바꿈, encoding 정규화는 이 단계에서 하지 않는다.
- 동일 byte의 재수집은 같은 content hash로 식별하되, 수집 시도 자체의 기록은 남길 수 있다.
- 원본 파일을 Git에 넣을지는 크기와 라이선스 확인 후 결정한다. 넣지 않는 경우 download script, checksum, manifest fixture를 버전 관리한다.

### L1. RawRecord와 Source Catalog

원본 구조를 잃지 않는 범위에서 레코드 경계와 metadata를 식별한다. RawRecord는 source payload 또는 그 안전한 참조를 보유하며 아직 ForgeSync 도메인 의미를 강제하지 않는다.

```text
rawRecordId
artifactId
sourceLocator (line/offset/path/sequence)
rawPayload or rawPayloadRef
sourceObservedAtRaw
sourceTypeRaw
parseStatus
parseError
parserVersion
```

Devices.xml 등의 metadata는 DataItem catalog로 파생하되 원본 XML도 L0에 남긴다. 알 수 없는 DataItem은 `UNKNOWN`으로 집계하고 임의 metric으로 매핑하지 않는다.

### L2. Canonical Observation

PRD의 세 Variant를 보존한다.

```text
ObservationEnvelope
├── SampleObservation
├── EventObservation
└── ConditionObservation
```

Envelope 최소 필드:

```text
schemaVersion
eventId
sourceEventKey
machineId
observationKind
source metadata
sourceObservedAt
agentInstanceId? / sourceSequence?
replaySessionId / replaySequence
provenance
payload
rawRecordId
mappingVersion
```

원천에 없는 `agentInstanceId`, `sourceSequence`, 단위, 시간은 만들어 내지 않는다. 파생하거나 보정한 값은 방식과 버전을 provenance에 기록한다.

catalog가 단위를 선언하지 않는 SAMPLE의 단위는 mapping table의 `derivedUnit`으로만 정할 수 있다. 근거 없이 `derivedUnit`을 채우거나 catalog 단위를 `derivedUnit`으로 덮어쓰는 것은 매핑 시작 전에 실패하고, mapping report의 단위 열이 `(derived)`로 원천 선언과 구분한다.

mapping table은 Ingestion 경계를 넘지 않으므로 파생 여부를 소비자가 되짚을 수 없다. 그래서 Canonical Observation의 available SAMPLE은 `unitProvenance`로 `SOURCE_DECLARED`와 `DERIVED`를 직접 구분한다([ADR-049](../adr/ADR-049-accumulated-time-and-operating-signal-mapping.md)).

### L3. Operational Projection

Canonical Observation을 사람이 이해하는 현재 상태로 투영한다.

```text
Mazak01 / CNC-MILL-01
Connectivity  ONLINE
Execution     ACTIVE
Spindle speed 6,842 rpm
Tool          #12
Program       O1234
Freshness     FRESH
Source        REAL:NIST
```

Projection은 재생성 가능한 파생 데이터다. 과거 event는 이력에 저장하되 ordering policy를 통과하지 못하면 current state를 변경하지 않는다.

## 3. Human-readable Profile

첫 데이터 Step의 출력은 UI가 아니라 재현 가능한 profile이다. 기계가 읽는 JSON과 사람이 검토할 Markdown을 함께 생성한다.

`profile.json` 최소 내용:

```text
artifact hash and parser version
record count / valid / invalid / unknown
observed time range
machine identifiers
DataItem id, category, type, subtype, unit frequency
sample values with source locator
missing required fields
ordering anomalies
semantic coverage
```

`profile.md`는 위 내용을 표와 제한사항으로 표현하며, 다음을 명시한다.

- 확인된 사실과 아직 확인할 항목
- 원천 단위/시간대/encoding을 추정했는지 여부
- canonical mapping 후보와 미매핑 항목
- profile을 재생성하는 정확한 명령

Profile은 원본이 아니며 삭제 후 동일 입력과 tool version으로 재생성 가능해야 한다.

## 4. Port와 책임 분리

```text
SourceReader       원천 byte stream 획득
ArtifactStore      원본과 manifest의 불변 저장
ChecksumVerifier   저장 전후 무결성 확인
RawRecordDecoder   source grammar로 레코드 경계 추출
MetadataCataloger  Devices.xml 등 metadata catalog 생성
ObservationMapper  catalog 기반 canonical mapping
ProfileGenerator   raw/catalog 통계와 검토 문서 생성
```

다운로더가 canonical JSON을 만들거나, parser가 DB에 직접 쓰거나, UI가 raw source 의미를 해석해서는 안 된다.

## 5. 오류와 재처리

- 수집 실패: 부분 파일을 완료 artifact로 등록하지 않는다.
- checksum 불일치: 격리하고 실패를 명시한다.
- parse 실패: Source Artifact는 보존하고 `RawRecord.parseStatus=INVALID` 및 위치/이유를 기록한다.
- mapping 실패: unknown으로 계수하고 dead-letter 또는 review queue에 남긴다.
- schema upgrade: 원본을 수정하지 않고 새로운 `mappingVersion`과 Processing Run을 생성한다.
- 중복 전달: Inbox에서 business side effect를 막되 관찰 가능한 중복 지표는 유지한다.

로그에는 전체 민감 payload를 기본 출력하지 않는다. 오류 재현에는 `artifactId`, `sourceLocator`, hash를 사용한다.

## 6. 시간, 순서, identity

- `sourceObservedAt`: 원천이 주장하는 시간
- `replayPublishedAt`: replay가 발행한 wall-clock 시간
- `ingestedAt`: API가 수신한 시간
- `projectedAt`: Twin projection 완료 시간
- `replaySequence`: 동일 replay session의 전송 순서
- `sourceEventKey`: 원천 레코드의 안정적 identity 또는 명시된 결정적 생성 규칙

정렬 우선순위는 동일 ReplaySession의 `replaySequence`, 역사적 `sourceObservedAt`, tie-breaker `sourceEventKey`다. freshness는 역사적 source time이 아니라 최신 projected/received wall-clock을 기준으로 한다.

## 7. Provenance

모든 소비자가 다음을 구분할 수 있어야 한다.

```text
REAL:NIST
SIMULATED_LAYOUT
SIMULATED_PRODUCTION
AI:PHM2010 또는 AI:NASA
UNKNOWN / TO_VERIFY
```

여러 출처를 합친 ViewModel은 필드별 provenance를 보존한다. 객체 하나에 provenance label 하나만 붙여 서로 다른 출처를 가리지 않는다.

## 8. 필수 테스트

### Golden fixture

- 작은 원본 fixture의 byte와 SHA-256을 고정한다.
- ingest 후 저장된 byte가 fixture와 완전히 같은지 검증한다.
- 같은 fixture로 profile을 두 번 생성했을 때 volatile field를 제외한 결과가 같은지 검증한다.

### Parser property/boundary

- 빈 입력, malformed record, unknown DataItem, 잘못된 timestamp/number/unit을 검증한다.
- parser 실패 후에도 artifact 조회가 가능해야 한다.
- locale과 관계없이 숫자와 시간이 동일하게 해석되어야 한다.

### Mapping contract

- SAMPLE/EVENT/CONDITION golden case를 schema로 검증한다.
- source metadata, time, rawRecordId, provenance가 mapping 후 남아 있는지 검증한다.
- 미매핑 DataItem이 조용히 버려지지 않고 coverage에 반영되는지 검증한다.

### Projection

- 동일 입력 재처리 결과가 결정적인지 검증한다.
- duplicate가 business side effect를 늘리지 않는지 검증한다.
- out-of-order observation이 current Twin을 rollback하지 않는지 검증한다.

## 9. 데이터 보존 정책 초기값

실제 보존 기간은 데이터 크기를 측정한 뒤 ADR로 확정한다. 확정 전 기본 정책은 다음과 같다.

- 검증에 사용한 작은 fixture와 manifest/checksum은 영구 버전 관리한다.
- 외부 대용량 원본은 라이선스가 허용하는 저장소에 보존하고 checksum으로 참조한다.
- Raw, Canonical, Projection을 같은 테이블/파일에서 lifecycle 관리하지 않는다.
- 자동 삭제 정책은 데이터 크기, 재다운로드 가능성, 라이선스가 Verification Ledger에서 확인되기 전 활성화하지 않는다.
