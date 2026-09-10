# ADR-053: Cycle 성능과 OEE 공개 경계

- 상태: Accepted
- 날짜: 2026-09-10
- 관련 PRD: 104, 105, 120
- 관련 ADR: [ADR-036](./ADR-036-explainable-cycle-baseline-and-anomaly-assessment.md),
  [ADR-051](./ADR-051-observed-utilization-kpi-projection.md)

## Context

고정 NIST 원천에는 실행 상태, 완료 가공 사이클, `PartCountAct`가 있지만 양품·불량을 구분하는 품질
입력이 없다. 따라서 Availability와 Performance 일부를 설명할 수 있어도 세 요소의 곱인 OEE를 만들 수
없다. `PartCountAct`에는 unavailable 공백과 카운터 역행 가능성도 있으므로 첫 값과 마지막 값만 빼면
음수 생산량 또는 공백을 건넌 증가량을 만들 수 있다.

## Decision

Operational Effectiveness contract `1.0.0`은 같은 Replay cursor의 불변 Utilization과 Cycle Feature
processing run을 명시적으로 입력받는다.

- Availability의 원천은 관측된 `EXECUTION` 구간이고 값은 `ACTIVE_DURATION / OBSERVED_RANGE`로
  파생한다. 원천 `OBSERVED`와 값 `DERIVED`를 별도 필드로 공개한다.
- Performance는 가장 최근 완료 사이클과, 같은 설비·프로그램·Cycle Feature version의 엄격히 이전
  완료 사이클을 비교한다. 최소 5개, 최근 최대 30개의 duration 중앙값을 기준으로
  `reference cycle seconds / actual cycle seconds × 100`을 계산하며 100% 초과를 자르지 않는다.
- 외부 이상 사이클 시간은 프로그램별 양수 입력만 허용하고 `ASSUMED`로 표시한다. 관측 baseline과
  같은 provenance로 표현하지 않는다.
- Throughput은 시간순으로 인접한 available `PartCountAct` 사이의 0 이상 증가만 합한다. unavailable은
  연결을 끊고 역행은 reset으로 세며 음수 생산량으로 합하지 않는다. 사용 가능한 전이가 없으면 숫자
  0이 아니라 `UNAVAILABLE`이다.
- Quality는 `QUALITY_SOURCE_NOT_AVAILABLE`, composite OEE는
  `QUALITY_COMPONENT_UNAVAILABLE`로 두며 numeric field를 계약과 화면에서 생략한다.

결과는 V012에 immutable processing run으로 보존한다. Process Analytics application은 소비자 관점의
`UtilizationEvidenceSource`만 의존하고 Equipment Twin 타입 변환은 바깥 adapter가 담당한다.

## Consequences

- 사용자는 관측, 파생, 가정, 원천 없음 상태를 구분할 수 있다.
- 실제 고정 원천의 `PartCountAct`가 0과 unavailable만 반복하는 범위에서는 “생산 0개”가 아니라
  “연속된 생산량 관측 근거 부족”으로 보인다.
- 품질 입력 계약이 도입되기 전에는 종합 OEE를 비교하거나 정렬할 수 없다.
- 전체 교대조 화면 구성은 Step 38에 남고 Dashboard에는 최소 공개 패널만 둔다.

## Rejected alternatives

- Quality를 100%로 가정: 관측되지 않은 품질을 만들고 종합 OEE를 과대 주장한다.
- 카운터 첫 값과 마지막 값의 단순 차: reset과 unavailable 공백을 숨긴다.
- Performance를 100%로 상한 처리: 기준보다 빠른 실제 비교 결과를 왜곡한다.
- 프로그램 구분 없는 단일 이상 사이클 시간: 서로 다른 공정 기준을 섞는다.
