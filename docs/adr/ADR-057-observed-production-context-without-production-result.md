# ADR-057: Observed Production Context Without Production Result

- Status: Accepted
- Date: 2026-09-14
- Related steps: 04, 18, 36, 40, 41
- Extends: [ADR-022](./ADR-022-explicit-canonical-mapping.md),
  [ADR-032](./ADR-032-observed-process-analytics-boundary.md),
  [ADR-053](./ADR-053-cycle-performance-and-oee-disclosure.md)

## Context

고정 NIST 원천에는 main `program` 50건, `subprogram` 49건, `PartCountAct` 49건과 관측에서
재구성한 Machining Run이 있다. 기존 mapping `2.2.0`은 main program과 part count만 보존했고,
`subprogram`은 지원하지 않았다. 원본의 subprogram 값은 25건의 `UNAVAILABLE`과 24건의 빈 문자열로,
확인 가능한 하위 프로그램 이름은 없다. Part count도 대부분 0과 unavailable이며 생산 완료·양품·불량
의미를 제공하지 않는다.

## Decision

- Canonical Event `SUBPROGRAM`을 `PROGRAM`과 별도로 추가한다. Mapping `2.3.0`, mapper `2.3.1`은
  `Mazak01-path_2`를 변환하며, 빈 문자열은 이름을 만들지 않고 `UNAVAILABLE`로 보존한다.
- 새 immutable Canonical processing run은
  `sha256:839ad138d6da7b2d5439c118d6f3bd88ab820824ae9f460c8e1848e6c638109c`이고, 의미 변환률은
  101,693 / 115,991(87.67318153994706%)다.
- Production Context는 Production bounded context의 읽기 전용 관측 projection이다. Process
  Analytics의 공개 processing identity와 같은 Replay session/cursor를 사용하되 Machining Run의
  identity나 lifecycle을 소유하지 않는다.
- main program과 subprogram은 각각 값 변화 또는 unavailable 관측에서 새 구간을 시작한다. 같은 값의
  반복은 구간을 나누지 않으며 모든 경계는 source time과 raw locator를 가진다.
- 프로그램별 run 수, 완료 경계가 있는 run 수, 총·평균·중앙 가공시간은 Machining Run에서 `DERIVED`로
  계산한다. 프로그램이 없는 run은 별도로 계수하고 임의 프로그램에 넣지 않는다.
- Part count는 unavailable을 가로질러 차분하지 않고 음수 변화는 reset으로 계수한다. 0 이상 인접 증가는
  보존하지만 run과의 관계는 `TEMPORAL_OVERLAP_ONLY`로만 표현한다.
- 계약은 `productionResultStatus: NOT_OBSERVED`를 필수로 하며 planned/completed/good/reject quantity를
  제공하지 않는다. `PartCount != ProductionResult` 불변식을 유지한다.

## Consequences

- 화면은 프로그램별 실제 가공 구간 통계를 제공하지만 작업지시, 목표 수량, 진척률, 생산 실적을 만들지 않는다.
- 현재 고정 원천에서 subprogram 이름은 계속 “확인 불가”로 보인다. 이는 데이터 결함을 숨기지 않는 결과다.
- Mapping 변경으로 Step 40의 Semantic Coverage와 Replay plan/regression identity도 함께 갱신된다.

## Verification

- Python mapping tests가 이름 있는 subprogram과 빈 subprogram의 분리·unavailable 보존을 검증한다.
- Domain tests가 main/subprogram 구간, 프로그램 미확인 run, 평균·중앙값, reset/unavailable 경계를 검증한다.
- PostgreSQL integration test가 Machining Run processing cursor에서 program/subprogram/part count를 함께 읽는다.
- 공유 JSON Schema와 Java/TypeScript tests가 ProductionResult 필드 부재와 비인과 관계 표시를 고정한다.
- 실제 Chromium E2E가 일시정지한 Replay의 2D Production Context를 확인한다.
