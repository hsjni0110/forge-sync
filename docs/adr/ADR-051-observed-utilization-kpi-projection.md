# ADR-051: 관측 범위 기반 Utilization KPI Projection

- 상태: Accepted
- 날짜: 2026-09-08
- 관련 PRD: 26, 104, 105, 120
- 관련 ADR: [ADR-049](./ADR-049-accumulated-time-and-operating-signal-mapping.md),
  [ADR-050](./ADR-050-range-scoped-equipment-state-intervals.md)

## Context

같은 Replay Session에서 설비 가동을 설명하는 근거가 두 종류다. `EXECUTION` 관찰로 만든 상태 구간은
ACTIVE 등의 체류시간을 말하고, 기계 누적 카운터는 전체·자동운전·절삭 누적시간을 말한다. 두 경로는
측정 의미와 보고 주기가 다르므로 값이 달라도 하나를 정답으로 선택할 수 없다.

ADR-050의 열린 마지막 구간과 첫 상태 관찰 전 시간은 임의로 상태를 붙일 수 없다. 누적 카운터도 리셋,
역행, `UNAVAILABLE`을 canonical 단계에서 보정하지 않으므로 projection에서 이를 숨기지 않는 계산 규칙이
필요하다. 원천에는 계획정지나 품질 데이터가 없어 계획시간 기준 Availability와 종합 OEE도 계산할 수
없다.

## Decision

### 상태 비율은 전체 원천 관측 범위를 분모로 사용한다

상태 비율의 분모는 Step 33 결과의 `observedFrom`부터 `observedTo`까지다. 닫힌 `EXECUTION` 구간만
상태별 체류시간으로 더한다. `UNAVAILABLE`이 연 구간은 `UNKNOWN`에 포함하지만, 첫 관찰 전 시간과 열린
마지막 구간은 상태를 만들지 않고 `uncoveredDuration`으로 별도 표시한다. 따라서 상태 비율 합은 100%보다
작을 수 있으며 이 차이를 결측 없이 계약에 싣는다. 관측 범위가 0이면 0%를 만들지 않고
`UNAVAILABLE/ZERO_DENOMINATOR`다.

상태 묶음은 `ACTIVE`, `READY`, `STOPPED`, `INTERRUPTED`, `UNKNOWN`이다. 원천의 `FEED_HOLD`와
`INTERRUPTED`는 `INTERRUPTED` 묶음으로 계산하되 Step 33 구간의 원래 값과 경계 근거를 수정하지 않는다.
그 외 미지원 값은 추측하지 않고 `UNKNOWN` 묶음에 포함한다.

### 카운터는 단조 증가 구간의 증가량만 합산한다

각 카운터를 Replay 순서로 독립 처리한다. 연속된 available 관찰의 차이가 0 이상일 때만 증가량에 더한다.
역행은 리셋 경계로 계수하고 그 한 전이는 더하지 않으며, 이후 새 값부터 다음 단조 증가 구간을 시작한다.
`UNAVAILABLE`은 구간을 끊는다. 리셋이나 unavailable이 하나라도 있으면 계산 가능한 값도 `PARTIAL`이며
사용 전이 수, 리셋 수, unavailable 관찰 수를 coverage로 제공한다.

- 자동운전 비율: `AUTO_DELTA / TOTAL_DELTA`
- 절삭 비율: `CUT_DELTA / AUTO_DELTA`

분모가 0이거나 표본이 부족하면 숫자를 만들지 않는다. 분자가 분모보다 크면 100%로 자르지 않고
`UNAVAILABLE/COUNTER_RELATION_VIOLATION`으로 반환한다.

### 두 경로는 나란히 비교하되 같은 의미라고 주장하지 않는다

상태 구간의 ACTIVE 비율과 카운터의 자동운전 비율을 모두 제공하고
`counterAutomatic - intervalActive`의 signed percentage-point 차이를 함께 제공한다. 계약은
`DIFFERENT_EVIDENCE_PATHS_NOT_EQUIVALENT`를 명시한다. 차이는 진단 단서이지 어느 경로가 틀렸다는 판정이
아니다.

### 처리 결과는 불변 version으로 보존한다

계산 입력은 명시적인 `intervalProcessingRunId`와 그 결과가 가리키는 machine, Replay Session,
`throughReplaySequence`, 그리고 같은 watermark까지의 누적 카운터다. 계산 규칙과 계약의 첫 버전은
`1.0.0`이다. 카운터도 interval 결과의 `observedFrom`/`observedTo` 안에 있는 관찰만 사용해 두 경로의
원천 시간 범위를 맞춘다. 같은 입력은 같은 processing identity를 재사용하고, 입력이나 규칙이 바뀌면 V010 테이블에
새 결과를 저장한다. 이전 결과와 Step 33 구간을 수정하지 않는다.

## Consequences

- 상태 구간과 기계 카운터의 차이를 숨기지 않고 각각의 계산식, 분모, coverage와 version을 조회할 수 있다.
- `UNKNOWN`과 미관측 시간이 0이나 정상 상태로 바뀌지 않는다.
- 카운터 리셋 이후의 관측 가능한 증가량은 사용할 수 있지만 결과가 완전하다고 표시되지 않는다.
- 이 projection은 계획시간 기준 Availability, Quality 또는 종합 OEE를 제공하지 않는다.
- 화면 구성은 별도 Shift Overview 단계의 책임이며 이 결정은 backend projection과 계약만 정의한다.

## Rejected alternatives

- 닫힌 상태 구간 합을 분모로 사용: 첫 관찰 전과 열린 꼬리를 분모에서 숨겨 비율을 과대 표시한다.
- `FEED_HOLD`를 ACTIVE로 계산: 가공 run이 열린 상태라는 의미와 실제 절삭·가동 체류시간을 혼동한다.
- 리셋 경계의 음수 차이를 합산하거나 카운터를 보정: 원천에 없는 값을 만든다.
- 100% 초과 값을 clamp: 데이터 관계 위반을 정상 수치처럼 보이게 한다.
- 상태와 카운터 중 하나만 대표 가동률로 노출: 서로 다른 관측 근거의 불일치를 숨긴다.
