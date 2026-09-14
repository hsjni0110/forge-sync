# ADR-058: Channel-separated Observed Tool Load Trend

- Status: Accepted
- Date: 2026-09-14
- Related steps: 28, 37, 42
- Extends: [ADR-032](./ADR-032-observed-process-analytics-boundary.md),
  [ADR-054](./ADR-054-observed-metric-channel-disclosure.md)

## Context

고정 Mazak01 원천에는 서로 다른 component와 DataItem에 속한 `LOAD` 여섯 채널이 있다. 이 값들은
모두 `PERCENT`지만 B, C, C2, X, Y, Z가 무엇의 부하인지가 서로 다르므로 합치거나 대표 채널을
추측할 수 없다. 또한 원천에는 공구의 실제 마모량, 잔여 수명이나 공구 교환 판정 label이 없다.

## Decision

- Tool Load Trend `1.0.0`은 `(machine, program, tool number, component, source DataItem)`별로 계산한다.
  서로 다른 부하 채널, 프로그램, 공구를 합치지 않는다.
- 완료된 Machining Run 안에서 활성 `TOOL_NUMBER`와 같은 시각 범위에 있는 available `LOAD` 표본을
  사용한다. 한 후보 점에 available 원본 표본이 3개 이상일 때만 그 중앙값을 유효 점으로 만든다.
- 수치 추세에는 유효 점 5개 이상, 관측된 후보 점 대비 유효 점 coverage 0.8 이상이 필요하다.
  coverage 분모는 원천 전체 시간이나 기대 sampling 횟수가 아니라 해당 그룹에서 실제 관측된
  run-tool-channel 후보 점이다.
- 기준선은 첫 유효 점 3개의 중앙값이다. 각 편차는
  `((pointMedian - baselineMedian) / baselineMedian) * 100`, 추세 기울기는 유효 점 순번에 대한
  편차 백분율의 ordinary least squares slope다. 0 기준선에서는 수치를 만들지 않는다.
- 결과는 `DERIVED` provenance와 원본 `sourceEventKey`, `rawRecordId`, DataItem, mapping version을
  보존한다. 서로 다른 provider/source set 또는 `SIMULATED` 입력은 거부한다.
- 결과는 관측 부하 변화 대리지표일 뿐 물리적 마모량이나 잔여 수명이 아니다. 이 값만으로 Machine
  `FAULT`, Alarm, Maintenance Request 또는 제어 명령을 만들지 않는다.

## Consequences

- 같은 공구라도 서로 다른 부하 채널의 추세는 별도로 보이며 단일 “공구 건강 점수”는 제공하지 않는다.
- 한 run에서 채널 자체가 전혀 관측되지 않으면 현재 coverage 분모에 들어가지 않는다. 따라서 이 비율은
  sampling completeness나 전체 시간 coverage로 해석할 수 없다.
- 표본이나 coverage가 부족하면 상태와 이유만 표시하고 baseline, 최근 편차, 기울기를 생략한다.

## Verification

- Domain tests가 수동 중앙값·기준선·편차·기울기, 채널/프로그램/공구 분리, 표본·coverage·0 기준선,
  simulated source 거부를 검증한다.
- 공유 JSON Schema와 Java/TypeScript contract tests가 계산식과 정책 version을 고정하고 wear, RUL,
  fault, alarm 필드를 거부한다.
- 실제 PostgreSQL integration test가 Canonical Machining Run, TOOL_NUMBER, LOAD history에서 같은
  projection을 재구성한다.
- Chromium E2E가 실제 Replay 범위의 화면에서 파생 provenance와 비마모·비알람 고지를 확인한다.
