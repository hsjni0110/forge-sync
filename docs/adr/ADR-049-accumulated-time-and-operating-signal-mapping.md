# ADR-049: Accumulated Time과 운전 신호의 Canonical Mapping 확장

- 상태: Accepted
- 날짜: 2026-09-07
- 관련 PRD: 19~25, 120
- 관련 ADR: [ADR-022](./ADR-022-explicit-canonical-mapping.md)

## Context

고정 Mazak01 raw artifact `6eec7a...ef2cf`의 115,991 record 중 53,939건(46.50%)만 canonical로
변환되고 있었다. 미매핑 항목에는 가동률 계열 지표의 유일한 원천인 기계 누적 카운터
(`total_time` 32,471, `auto_time` 10,111, `cut_time` 3,516)와 운전 신호(`estop` 51, 오버라이드 184,
`line`/`sequenceNum` 1,372)가 포함되어 있었다.

이 항목들을 ADR-022의 규칙으로 그대로 매핑할 수 없는 세 가지 충돌이 있다.

1. `ACCUMULATED_TIME` 세 DataItem은 Devices.xml에 `units` 속성이 아예 없다. ADR-022의 SAMPLE 규칙은
   catalog unit이 canonical target의 기대 단위와 같을 것을 요구한다.
2. `(componentId, category, target)` 채널 유일성 규칙과 충돌한다. 누적 카운터 3종은 모두
   `Mazak01-path / SAMPLE / ACCUMULATED_TIME`이고, `Fovr`와 `Frapidovr`는 모두
   `Mazak01-path / EVENT / PATH_FEEDRATE_OVERRIDE`다. 구분되는 정보는 subType뿐이다.
3. 오버라이드 3종은 EVENT인데 catalog가 `PERCENT` 단위를 선언한다. 기존 규칙은 EVENT의 단위 선언을
   거부한다.

## Decision

### 파생 단위(derived unit)를 mapping table에 명시한다

Mapping entry에 `derivedUnit`을 추가한다. catalog가 단위를 선언하지 않은 SAMPLE만 이 값을 가질 수
있고, catalog 단위가 있는 entry가 이 값을 선언하면 매핑 시작 전에 실패한다. 반대로 catalog 단위가
없는 SAMPLE이 `derivedUnit`을 선언하지 않아도 실패한다. 단위를 조용히 비우거나 추측해서 채우는 경로를
남기지 않기 위해서다.

누적 카운터의 `derivedUnit`은 `SECOND`다. 근거는 고정 artifact에서 재현 가능한 계수 대 실제 경과
시간의 대응이다. `total_time`은 `2016-10-05T08:43:49.514Z`부터 `19:13:55.847Z`까지 37,811 증가했고
같은 구간의 실제 경과는 37,806.3초로 비율은 1.00012다. 32,446개 유효 값에서 역행은 0회이며 `+1`
증가 간격의 중앙값은 1.052초다. 이는 관측에서 파생한 근거이지 원천의 선언이 아니며, mapping report의
단위 열에 `(derived)`로, Verification Ledger V-044에 재현 절차와 함께 남긴다.

### subType이 의미를 가르면 canonical target 이름이 그 의미를 담는다

MTConnect type만으로 채널이 겹치고 subType이 실제로 다른 지표를 가리키면, canonical target 이름에
그 의미를 넣는다: `TOTAL_ACCUMULATED_TIME`, `AUTO_ACCUMULATED_TIME`, `CUT_ACCUMULATED_TIME`,
`PROGRAMMED_PATH_FEEDRATE_OVERRIDE`, `RAPID_PATH_FEEDRATE_OVERRIDE`. ADR-022의 채널 유일성 규칙은
그대로 유지되고, 소비자는 `(type, subType)` 쌍이 아니라 하나의 target enum으로 지표를 고른다.
원천의 `type`과 `subType`은 mapping table과 report에 그대로 보존된다.

`x:SEQUENCE_NUMBER`처럼 벤더 확장 접두사가 붙은 type의 target은 접두사를 뺀 `SEQUENCE_NUMBER`다.
canonical target은 ForgeSync의 어휘이고 원천 type은 mapping table과 Devices artifact에 남는다.

### EVENT의 catalog 단위는 허용하되 canonical payload에 싣지 않는다

오버라이드처럼 catalog가 EVENT에 단위를 선언하는 경우를 허용한다. canonical Event payload에는 단위
필드가 없으므로 값만 싣고, 원천 단위는 Devices artifact와 `sourceDataItemId`로 추적한다. EVENT가
`derivedUnit`을 선언하는 것은 계속 금지한다.

### 정수 Event

`TOOL_NUMBER`, `PART_COUNT`에 더해 오버라이드 3종과 `LINE`, `SEQUENCE_NUMBER`를 비음수 정수 Event로
둔다. 고정 artifact의 관측값이 모두 정수이기 때문이다. 소수나 음수가 나타나면 잘라내지 않고
`INVALID_VALUE` record로 남겨 원본 locator와 함께 검토한다.

### 누적 카운터의 값은 보정하지 않는다

단조 증가 위반, 리셋, 역행을 canonical 단계에서 고치지 않는다. 관측값 그대로 보존하고, 해석은 이후
projection의 책임이다.

### Observation envelope 2.1.0

새 어휘만 추가하는 minor 증가다. 필드는 추가·삭제·변경되지 않으므로 `2.0.0`으로 저장된 기존 history와
canonical run은 그대로 유효하고 replay 가능하다. 새 어휘는 `2.1.0`에만 속하며, `2.0.0`을 선언한
문서가 새 metric이나 Event type을 담으면 거부된다. MQTT `schema-version` property는 build-time 상수가
아니라 payload의 `schemaVersion`을 반복한다.

## Consequences

- Semantic coverage가 53,939 / 115,991 (46.50%)에서 101,644 / 115,991 (87.63%)로 올라간다.
  invalid value는 0건이다.
- Mapping version은 `2.2.0`, mapper version은 `2.1.0`, report schema version은 `2.1.0`이 되고
  새 processing run `sha256:80ce6b...b3ef`가 만들어진다. 기존 run은 덮어쓰지 않는다.
- Replay 대상이 53,939건에서 101,644건으로 늘어난다. sequence hash와 replay plan이 새로 만들어진다.
- Step 33의 상태 구간, Step 34의 가동률·절삭 비율, Step 35의 정지 사유가 원천 근거를 갖는다.
- 남은 미매핑 14,325건(`Bfrt`, `Cfrt`, `Xfrt`/`Yfrt`/`Zfrt`, `Cload`, `Cdeg`, `Tool_group`,
  `Tool_suffix`, 주석·프로그램 보조 항목)은 이 결정의 범위 밖이며 계속 원본과 함께 보존·계수된다.

## Rejected alternatives

- 단위를 미확정으로 두고 값만 보존: Step 34의 비율은 계산할 수 있지만 시간 단위 표시가 불가능하고,
  AVAILABLE SAMPLE이 단위 없이 존재할 수 있는 구멍을 계약에 남긴다. 재현 가능한 근거가 이미 있다.
- 초 단위를 코드에만 두고 mapping table에는 남기지 않기: 무엇이 원천 선언이고 무엇이 파생인지 계약에서
  구분할 수 없게 된다.
- payload에 subType 필드를 추가: 원천에 더 충실하지만 모든 소비자가 `(target, subType)` 쌍으로 지표를
  골라야 하고, 하나의 지표를 가리키는 데 두 값이 필요해진다.
- 오버라이드를 SAMPLE로 매핑: category를 바꾸는 것은 데이터 불변식 위반이다.
- 누적 카운터를 단조 증가로 보정: 관측하지 않은 값을 만든다.
