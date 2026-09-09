# ADR-052: 정지 Pareto와 동시 관측 근거

- 상태: Accepted
- 날짜: 2026-09-10
- 관련 PRD: 104, 105, 120
- 관련 ADR: [ADR-050](./ADR-050-range-scoped-equipment-state-intervals.md),
  [ADR-051](./ADR-051-observed-utilization-kpi-projection.md)

## Context

Step 33은 상태 신호별 관측 구간을 보존하고 Step 34는 `EXECUTION` 구간을 가동 상태로 묶는다. 정지
분석에는 이 구간과 같은 시간에 관측된 비상정지, 운전 모드 변화, CONDITION이 유용하지만, 시간상
겹친다는 사실만으로 어느 신호가 정지를 일으켰다고 판정할 수는 없다. 특히 CONDITION은 현재 상태의
지속시간을 나타내는 별도 구간 계약이 없으므로 WARNING/FAULT 한 건에서 지속 구간을 만들어서는 안 된다.

## Decision

### 닫힌 비가동 구간만 순위화한다

Step 34와 같은 상태 묶음을 재사용해 닫힌 `EXECUTION` 구간 중 `STOPPED`, `INTERRUPTED`, `UNKNOWN`만
대상으로 삼는다. 지속시간 내림차순이며, 동률은 시작 시각, 시작 replay sequence, source event key
순으로 정렬한다. 비율 분모는 순위 대상 전체 지속시간이고 누적 비율은 그 정렬 순서를 따른다. 열린
구간에 임의 종료 시각을 붙이지 않는다.

### 겹침은 근거로만 연결한다

- `EMERGENCY_STOP=TRIGGERED` 구간은 비가동 구간과 실제 시간 범위가 겹칠 때 연결한다.
- 운전 모드 근거는 최초 모드 관찰을 제외한 뒤, 새 모드 구간의 시작 관찰 시점이 비가동 구간 안에
  있을 때 연결한다.
- CONDITION WARNING/FAULT는 점시점 관찰이다. 관찰 시각이 반열린 범위
  `[downtime.startedAt, downtime.endedAt)` 안에 있을 때만 연결하며 지속을 추론하지 않는다.

근거는 종류와 시간의 결정적 순서로 모두 반환한다. 연결된 근거는 `CONCURRENT_EVIDENCE`, 하나도 없으면
`UNCONFIRMED_REASON`으로 분류한다. 전자는 동시 관측을 뜻할 뿐 원인 판정이 아니다.

### 결과와 탐색은 같은 불변 입력을 가리킨다

Pareto `1.0.0`은 명시적인 Utilization processing run, 그 결과가 가리키는 interval processing run,
같은 Replay watermark까지의 CONDITION 관찰을 입력으로 한다. V011에 새 processing result를 보존하며
기존 결과를 수정하지 않는다. Dashboard는 Replay가 일시정지 또는 완료된 안정된 cursor에서만 이
연쇄 projection을 요청한다. 항목을 선택하면 해당 구간의 `startedAt`으로 기존 Replay seek를 보내며,
이후 2D, 3D, current run은 기존 권위 cursor 수렴 경로를 그대로 사용한다.

## Consequences

- 긴 비가동 구간부터 재현 가능하게 탐색하면서 시작/종료와 동시 관측 근거까지 추적할 수 있다.
- CONDITION 종료 시점이나 정지 원인을 새로 만들지 않는다.
- 새로운 입력이나 규칙은 새 processing identity가 되고 이전 Pareto 결과는 그대로 남는다.
- 전체 Shift Overview 전에 최소 Dashboard 목록을 제공하며, 종합 KPI 화면 구성은 Step 38에 남는다.

## Rejected alternatives

- 가장 가까운 CONDITION을 구간 사유로 지정: 겹치지 않는 관찰을 인과처럼 보이게 한다.
- WARNING/FAULT가 다음 NORMAL까지 지속된다고 추론: 현재 canonical 계약에 없는 지속 의미를 만든다.
- 겹치는 근거 중 하나만 대표 사유로 선택: 여러 독립 신호의 불확실성을 숨긴다.
- 현재 시각으로 열린 구간을 닫아 순위화: wall clock과 조회 시점에 따라 결과가 바뀐다.
