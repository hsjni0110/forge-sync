# ADR-050: 관측 구간 전체를 대상으로 하는 Equipment State 구간 projection

- 상태: Accepted
- 날짜: 2026-09-07
- 관련 PRD: 26, 104, 105
- 관련 ADR: [ADR-026](./ADR-026-latest-observation-ordering.md),
  [ADR-027](./ADR-027-equipment-state-and-freshness.md),
  [ADR-033](./ADR-033-replay-control-cursor-and-seek.md),
  [ADR-034](./ADR-034-deterministic-machining-run-segmentation.md),
  [ADR-037](./ADR-037-cursor-bound-process-analysis-presentation.md)

## Context

가동률 계열 지표(Step 34~38)는 "이 설비가 얼마나 돌았나"에 답해야 한다. 그런데 Equipment State는
[ADR-027](./ADR-027-equipment-state-and-freshness.md)에 따라 **현재 Latest Observation 하나**만
투영한다. 시점 값만 있고 체류시간이 없으므로 KPI의 전제가 통째로 빠져 있다(실측 결함 D-05, D-06).

동시에 [ADR-037](./ADR-037-cursor-bound-process-analysis-presentation.md)은 공정 분석을 **정지된
Replay Cursor 시점**으로 한정한다. 확정된 `PAUSED`/`COMPLETED` cursor가 Twin의 session, sequence,
시각과 일치할 때만 분석하고, 그렇지 않으면 결과를 감춘다. 이 규칙은 "지금 이 순간 무엇이 보이는가"를
지키기 위한 것이다.

구간 집계는 그 질문에 답하지 않는다. "05:27:55부터 19:15:07까지 ACTIVE가 몇 초였나"는 cursor가 어디
있든 같은 답을 가져야 한다. 두 결정을 한 규칙으로 묶으면 둘 중 하나가 망가진다.

## Decision

### 두 경로를 분리한다

Cursor-bound 분석(ADR-037)과 구간 집계는 **서로 다른 질문에 답하는 별개 경로**다. ADR-037은 수정하지
않는다. 구간 집계는 cursor 위치, 재생 상태, Twin version과 무관하게 하나의 Replay Session이 지금까지
쌓은 관측 전체를 대상으로 한다. 화면이 두 경로를 나란히 보여줄 수 있지만, 한쪽의 무효화 규칙이 다른
쪽을 감추지 않는다.

구간 결과는 Twin snapshot이 아니다. TwinVersion을 올리지 않고, Equipment State의 현재 값을 바꾸지
않으며, Freshness 판단에 참여하지 않는다.

### Equipment Twin이 소유한다

`EquipmentState`는 Equipment Twin Context의 개념이므로 그 구간화도 같은 Context가 소유한다. Process
Analytics의 `MachiningRun`과 identity, 저장소, 생명주기를 공유하지 않는다.
[ADR-034](./ADR-034-deterministic-machining-run-segmentation.md)가 세운 immutable processing result
패턴만 가져오고, 두 Context는 versioned public contract와 안정적인 ID로만 연결한다.

### 신호마다 독립된 구간 시리즈를 만든다

`execution`, `mode`, `power`, `estop`은 원천에서 서로 다른 DataItem이고 각자의 시각에 따로 보고된다.
네 값을 한 구간에 묶으면 원천에 없는 합성 상태를 만들게 되므로, **신호별로 독립된 구간 시리즈**를
만든다. 여러 신호를 겹쳐 읽는 일(정지 구간과 estop의 시간적 중첩 등)은 소비자의 몫이며 인과를
주장하지 않는다.

### 구간 경계는 관측만으로 정한다

한 구간은 그 값을 보고한 관측에서 시작해 **같은 신호의 다음 관측**에서 끝난다. 임의의 staleness
임계값을 두지 않는다. MTConnect EVENT는 값이 바뀔 때 보고되므로 관측이 없다는 것은 상태가 유지된다는
뜻이고, 공백을 일정 시간 뒤 `UNKNOWN`으로 끊으면 원천에 없는 상태 전이를 만든다.

모르는 구간은 추론하지 않고 원천이 말해줄 때만 만든다. `UNAVAILABLE` 관측은 `UNKNOWN` 구간을 연다.
고정 원천에서 60초를 넘는 전체 공백은 3개뿐이고 그중 11,621초(전체의 23%)인 최대 공백은 이미
`05:27:55`의 `UNAVAILABLE` 관측이 열어 둔 구간 안에 있다. 임계값 없이도 모든 공백이 근거로 설명된다.

### 열린 구간과 세션 경계

종료 관측이 없는 마지막 구간은 **열린 구간**으로 남기고 종료 시각을 만들지 않는다. 구간은 하나의
`replaySessionId` 안에서만 이어진다. 새 세션은 [ADR-033](./ADR-033-replay-control-cursor-and-seek.md)에
따라 source time을 뒤로 되돌릴 수 있으므로 세션을 가로질러 이으면 음수 길이가 생긴다.

따라서 **구간 합은 원천 시간 범위보다 작다.** 이 차이는 결함이 아니라 설명해야 할 값이며, 첫 관측
이전 구간과 열린 구간의 길이로 분해해 결과에 함께 싣는다.

### 재처리는 덮어쓰지 않는다

처리 실행 identity는 `(machineId, replaySessionId, throughReplaySequence, ruleVersion, inputHash)`로
결정적으로 만든다. 같은 입력과 같은 rule version은 같은 구간 경계와 같은 result hash를 낸다. 늦게
도착한 관측은 기존 구간을 수정하지 않고 새 processing version으로 남는다.

## Consequences

- Step 34는 체류시간을 계산할 전제를 얻는다. `execution` 구간에서 상태별 체류시간을, 별도로 기계 누적
  카운터에서 같은 지표를 산출해 두 경로를 비교할 수 있다.
- Step 35는 정지 구간과 `estop`·`mode`·CONDITION의 시간적 중첩을 근거로 제시할 수 있다. 겹침은 근거이며
  인과가 아니다.
- 구간 합이 원천 범위보다 작다는 사실이 화면에 드러나야 한다. 비율을 낼 때 분모를 원천 범위로 쓸지
  구간 합으로 쓸지 명시하지 않으면 서로 다른 숫자가 나온다.
- Equipment Twin에 새 테이블과 조회 계약이 생기고, ADR-034의 처리 실행 패턴이 두 Context에 존재하게
  된다. 의미가 안정된 뒤 공유를 검토한다.

## Rejected alternatives

- ADR-037을 확장해 한 문서가 두 경로를 모두 규정: 이미 긴 Accepted 문서가 서로 다른 질문에 답하는 규칙을
  함께 담게 되고, cursor 무효화 규칙이 구간 집계에도 적용되는 것으로 오독될 여지가 남는다.
- 네 신호를 하나의 합성 상태 타임라인으로 묶기: 상태 띠를 그리기는 쉽지만 원천에 없는 합성 상태를 만들고
  구간 수가 신호 수만큼 배가된다.
- N초 무관측이면 `UNKNOWN`으로 끊기: 에이전트가 조용히 죽는 경우를 방어하지만 N을 정할 근거가 이 원천에
  없다. 실측상 모든 큰 공백이 이미 `UNAVAILABLE`로 표시되어 있어 임계값이 만드는 것은 방어가 아니라
  관측하지 않은 상태 전이뿐이다.
- Process Analytics가 소유: 처리 실행 패턴을 재사용할 수 있지만 다른 Context가 Equipment State를
  투영하게 되어 소유권 규칙과 어긋난다.
- 구간 합을 원천 범위에 맞추려 마지막 구간을 관측 끝 시각으로 닫기: 관측하지 않은 종료를 만든다.
