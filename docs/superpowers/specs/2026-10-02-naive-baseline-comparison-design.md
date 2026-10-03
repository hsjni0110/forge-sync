# Naive Baseline 비교 실험 설계

Date: 2026-10-02

## 1. 목적

ForgeSync의 핵심 주장인 "근거 없는 숫자를 만들지 않는다"와 "중복·역순 전달에도 Twin이 틀어지지 않는다"를
부정형 서술이 아니라 **같은 데이터에서 naive 구현이 만드는 오류와의 비교**로 보여 준다.

이 실험은 ForgeSync가 제조 현실의 정답이라는 것을 증명하지 않는다. 정답이 존재하는 범위와 존재하지 않는
범위를 나누고, 각 범위에서 주장할 수 있는 것만 주장한다.

| Track | 질문 | 정답(ground truth) | 주장 형태 |
|---|---|---|---|
| A. 의미 해석 | 같은 원천에서 합리적인 naive 규칙들이 얼마나 다른 운영 숫자를 만드는가 | 없음 | 결과 폭(민감도)과 근거 없는 가정의 수 |
| B. 전달 장애 | 중복·역순·재시작이 주입될 때 naive consumer와 ForgeSync의 결과가 무장애 결과와 얼마나 다른가 | 무장애 재생 결과 | 정량 차이 |

## 2. 결정 사항

2026-10-02 대화에서 다음을 결정했다.

1. Track A와 Track B를 모두 수행한다.
2. Track B의 ForgeSync 측정은 실제 MQTT, PostgreSQL, Factory API를 띄운 end-to-end 실행으로 한다.
   도메인 정책을 직접 호출하는 in-process 측정으로 대체하지 않는다.
3. Alarm **건수** 비교는 제외한다. 3.3절의 순환성을 보고서에 그대로 기록하고, Alarm은 Track B의 중복
   부작용과 생명주기 지표로만 비교한다.

## 3. 사전 탐색에서 이미 본 값 (공개)

사전 등록 전에 raw artifact
`sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf`를 임시 스크립트로 직접 읽어
아래 값을 확인했다. 이 값을 보고 naive 변형을 골랐다는 사실을 숨기지 않는다. 임시 스크립트의 SHDR 분할은
정식 parser와 다르므로(레코드 116,387 vs 정식 115,991) 수치는 구현된 naive 규칙으로 다시 계산한다.

### 3.1 생산 수량

- `PartCountAct`: available 관찰 24건이 모두 `0`. 증가량 기준 생산 수량은 0이다.
- `execution`이 `ACTIVE`로 진입한 횟수: 137.
- ForgeSync Machining Run: 122(ADR-034 segmentation rule의 결과이며 생산 실적이 아니다).

### 3.2 가동률

- 원천 관측 범위: 약 13.79시간. 그중 `execution=UNAVAILABLE` 구간이 약 3.52시간(약 25.5%).
- `ACTIVE / 전체 관측 범위`: 약 23.5%.
- `ACTIVE / UNAVAILABLE을 제외한 시간`: 약 31.5%.
- 누적 카운터 `auto_time / total_time` 증가량 비율: 약 31.9%. 카운터 단위는 원천이 선언하지 않았다(V-044).

### 3.3 Alarm 규칙의 순환성

`ConditionToAlarmPolicy` `1.0.0`의 allowlist(`345`, `401`, `406`, `442`, `468`, `1101`, `1105`)는 이 raw에서
관찰된 Warning native code 7종과 정확히 같다. 같은 데이터로 만든 규칙을 같은 데이터로 평가하면 독립적인
결과가 아니다. 따라서 "naive는 알람 17건, ForgeSync는 N건" 형태의 비교는 하지 않는다.

## 4. 사전 등록: Naive 규칙

naive 규칙은 "MTConnect/SHDR 스트림으로 하루 안에 운영 대시보드를 만드는 합리적인 개발자"를 가정한다.
허수아비 비판을 피하려고 각 질문에 2개 이상 변형을 두며, ForgeSync에 가장 불리한 변형도 포함한다. 규칙은
실행 결과를 보기 전에 이 문서로 고정하고, 바꿀 때는 변경 이유와 이전 결과를 함께 남긴다.

### Track A 변형

| ID | 질문 | 규칙 | 숨은 가정 |
|---|---|---|---|
| NA-PROD-1 | 생산 수량 | available `PartCountAct`의 `max - min` | 카운터가 실제 완료 부품을 센다 |
| NA-PROD-2 | 생산 수량 | `execution`이 `ACTIVE`가 아닌 값에서 `ACTIVE`로 바뀐 횟수 | 가공 시작 1회 = 부품 1개 |
| NA-UTIL-1 | 가동률 | `ACTIVE` 체류시간 / 첫 관찰~마지막 관찰 | `UNAVAILABLE` = 비가동 |
| NA-UTIL-2 | 가동률 | `ACTIVE` 체류시간 / `UNAVAILABLE` 제외 시간 | 결측 구간은 나머지와 같은 분포 |
| NA-UTIL-3 | 가동률 | `auto_time` 증가량 / `total_time` 증가량 | 두 카운터가 같은 단위·같은 의미의 시간 |
| NA-OEE-1 | OEE | `NA-UTIL-1 × Performance × Quality`, Quality = 1 | 품질 원천 없음을 100% 양품으로 대체 |
| NA-OEE-2 | OEE | `NA-UTIL-2 × Performance × Quality`, Quality = 1 | 위와 같음 |
| NA-CAUSE-1 | 정지 원인 | 긴 정지 구간과 겹친 첫 non-NORMAL Condition을 원인으로 표시 | 시간상 겹침 = 인과 |

`Performance`는 같은 프로그램에서 관찰된 최단 `ACTIVE` 체류시간을 이상 cycle time으로 두고
`이상 cycle time × NA-PROD-2 / ACTIVE 체류시간`으로 계산한다. 이 정의도 naive 가정의 일부로 보고서에 적는다.
"긴 정지"는 ForgeSync Downtime Pareto와 같은 구간 목록을 쓰며 naive가 구간을 따로 정하지 않는다.

### Track B naive consumer

naive consumer는 ForgeSync와 **같은 MQTT topic, 같은 Observation envelope**을 받는다. 의미 매핑의 이점은
똑같이 누리고, 전달 처리만 다르다.

- Inbox와 중복 identity가 없다. 받은 메시지는 모두 처리한다.
- 현재 상태는 도착 순서의 last-write-wins다. `replaySequence`나 `sourceObservedAt`으로 정렬하지 않는다.
- 상태 구간은 새 `execution` 메시지가 도착할 때 그 메시지의 `sourceObservedAt`으로 이전 구간을 닫는다.
  음수 길이 구간은 0으로 자른다.
- 가공 횟수는 `NA-PROD-2` 규칙을 도착 순서로 적용한다.
- 알람 열림은 non-NORMAL Condition 메시지마다 1건이다.

## 5. 사전 등록: 가설과 판정 기준

결과가 가설과 다르면 그대로 보고한다. ForgeSync가 가설을 만족하지 못하면 그것은 결함 발견이며,
보고서에서 숨기거나 기준을 바꾸지 않고 별도 회귀 테스트와 수정 작업으로 넘긴다.

| ID | 가설 | 판정 |
|---|---|---|
| H-A1 | 사전 등록한 naive 생산 수량 변형은 서로 크게 다르다 | `NA-PROD-*`의 최댓값과 최솟값을 그대로 보고. 임계값을 두지 않음 |
| H-A2 | naive 가동률은 결측 처리 방식에 따라 달라진다 | `NA-UTIL-*`의 %p 폭을 보고하고 ForgeSync의 `UNKNOWN`·`uncoveredDuration` 공개와 나란히 표시 |
| H-A3 | naive OEE는 원천에 없는 품질 가정 위에서만 숫자가 된다 | `NA-OEE-*`에 사용된 근거 없는 가정 수와 ForgeSync의 `operational-effectiveness` 공개 상태를 비교 |
| H-A4 | naive 정지 원인은 인과 근거 없이 원인을 단정한다 | `NA-CAUSE-1`이 원인을 붙인 정지 수 vs ForgeSync가 인과 주장 없이 동시 근거만 보인 수 |
| H-B1 | 중복 주입에서 ForgeSync 결과는 무장애 결과와 같다 | 5.1절 지표 차이가 모두 0 |
| H-B2 | 역순 주입에서 ForgeSync의 현재 상태는 과거 값으로 되돌아가지 않고, 구간·Run은 무장애 결과와 같다 | rollback 0, 잘못 귀속된 시간 0초, Run 수 차이 0 |
| H-B3 | 같은 주입에서 naive consumer는 무장애 결과와 다르다 | 지표 차이 > 0. 크기는 주입률별로 그대로 보고 |
| H-B4 | Factory API가 재생 중 재시작돼도 ForgeSync는 무장애 결과로 수렴한다 | 조건부: Factory API MQTT session 설정을 먼저 확인. 미지원이면 결과 대신 한계로 기록 |

### 5.1 Track B 지표

무장애 실행의 결과를 기준으로 계산한다.

| 지표 | 정의 |
|---|---|
| 최종 상태 불일치 수 | 마지막 Twin의 execution, spindle speed, tool number, program, X/Y/Z 위치 중 기준과 다른 field 수 |
| Rollback 횟수 | 현재 상태가 더 오래된 `sourceObservedAt`의 값으로 바뀐 횟수 |
| 잘못 귀속된 시간 | 관측 범위 전체에서 상태가 기준과 다른 시간의 합(초) |
| Run 수 차이 | 기준 대비 가공 횟수의 차이 |
| 중복 부작용 | 기준에 없는 추가 Alarm 열림·Run 생성 수 |

ForgeSync 쪽 값은 REST 응답(`/twin`, `/equipment-state-intervals`, `/machining-runs`, `/alarms`)과
canonical history 테이블에서 읽는다. Rollback은 ForgeSync가 현재 상태 변경 이력을 노출하는 경로가 있는지
구현 계획에서 먼저 확인한다. 없으면 최종 상태와 구간으로만 판정하고 그 한계를 적는다.

## 6. 장애 주입 설계

### 6.1 주입 지점

Replay Edge의 `ReplayPublisher` Port에 seed 고정 decorator를 둔다. 실제 `MqttReplayPublisher`를 감싸며,
평가 전용 composition root에서만 조립한다. 일반 `forgesync-replay-api` 실행 경로의 동작은 바뀌지 않아야
하며 이를 테스트로 고정한다.

- **중복**: 선택된 envelope을 같은 byte로 한 번 더 publish한다. QoS1 재전송으로 같은
  `(replaySessionId, sourceEventKey)`가 다시 도착하는 상황을 모사한다.
- **역순**: 선택된 envelope을 보류했다가 이후 `k`개를 publish한 뒤 publish한다. 이 동안 replay cursor는
  이미 앞으로 간다. 이것은 V-019의 PUBACK 이후 cursor 전진 보장을 의도적으로 깨는 장애 모사이며, 평가
  실행에서만 허용한다.
- **재시작**: 재생 중 Factory API 프로세스를 종료하고 다시 시작한다(H-B4, 조건부).

주입 선택은 `(seed, envelope index)`만으로 결정되는 순수 함수로 만들어 단위 테스트한다. wall clock과
전역 난수를 쓰지 않는다.

### 6.2 시나리오 행렬

전체 재생은 최대 속도 100x에서 약 8분 이상 걸린다. E2E 실행 수를 줄이고 naive 쪽 반복은 offline으로 한다.

| 시나리오 | 중복률 | 역순률 / 최대 지연 `k` | Seed |
|---|---|---|---|
| S0 기준 | 0 | 0 | - |
| S1 중복 저 | 1% | 0 | 1 |
| S2 중복 고 | 5% | 0 | 1 |
| S3 역순 | 0 | 1% / 50 | 1 |
| S4 복합 | 5% | 5% / 50 | 1 |
| S5 재시작 | 0 | 0 | - (조건부) |

각 E2E 실행에서 broker에 도착한 메시지 순서를 별도 subscriber로 그대로 기록한다. naive consumer는 이 기록을
입력으로 하는 순수 함수로 실행하므로, ForgeSync와 **byte 단위로 같은 입력**을 받는다. naive의 seed 민감도가
필요하면 같은 주입 함수를 offline으로 여러 seed에 적용해 계산하고, 그 값은 E2E 결과와 구분해 표시한다.

## 7. 구조와 이름

- 새 Python workspace member: `evaluation/` (`forgesync-evaluation`, package `forgesync_evaluation`).
  제품 코드가 평가 코드에 의존하지 않고, 평가 코드만 `forgesync_edge`의 공개 Port와 composition 함수에 의존한다.
- 내부 구조는 `domain`(naive 규칙, 비교 지표, 주입 선택 함수), `application`(실험 실행 조율),
  `adapter`(raw SHDR 읽기, broker 기록 읽기, Factory API REST 읽기, 장애 주입 publisher)로 나눈다.
- 실행 명령: `./scripts/evaluate-naive-baseline`. Docker가 필요하므로 `./scripts/verify`에 넣지 않는다.
  단, 순수 규칙·지표·주입 함수의 단위 테스트와 architecture 검사는 `./scripts/verify`에 포함한다.
- 산출물:
  - `docs/evaluation/naive-baseline/results.json`: 입력 artifact checksum, mapping version, rule version,
    시나리오, seed, 지표.
  - `docs/evaluation/naive-baseline/report.md`: 결과 표, 가설 판정, 한계.
  - Verification Ledger 항목: Track A 결과, Track B 시나리오별 결과, Alarm 순환성.
- Roadmap Step 번호를 package, symbol, 경로, fixture에 쓰지 않는다.

`httpx2`, `paho` 등 이미 저장소에 있는 의존성만 사용한다. 새 외부 의존성이 필요해지면 구현 전에 결정을 요청한다.

## 8. 구현 순서 (각 단계 RED → GREEN → REFACTOR)

1. 이 문서의 4·5절을 사전 등록으로 커밋한다. 이후 규칙 변경은 이력으로 남긴다.
2. `NA-PROD-*`, `NA-UTIL-*`: 작은 SHDR fixture로 정상 경로와 `UNAVAILABLE`, 빈 입력, 카운터 역행 경계를
   검증한 뒤 실제 raw로 naive Track A 결과를 만든다.
3. `NA-OEE-*`, `NA-CAUSE-1`: 숨은 가정 목록을 결과의 일부로 출력하게 한다.
4. ForgeSync Track A 수집: S0 실행 후 REST로 utilization, production context, operational effectiveness,
   downtime pareto, machining runs를 읽어 같은 표에 놓는다.
5. 주입 선택 함수와 장애 주입 publisher: 결정성, 중복 byte 동일성, 역순 보류·해제를 단위 테스트한다.
   일반 composition 경로가 바뀌지 않음을 테스트한다.
6. Broker 도착 기록과 naive streaming consumer: 작은 기록 fixture로 중복·역순에서의 오류를 재현한다.
7. Track B 지표와 E2E 실행 script: S0~S4를 실행하고 결과 JSON을 만든다.
8. H-B4 확인: Factory API MQTT session 설정을 읽고 지원 여부에 따라 S5를 실행하거나 한계로 기록한다.
9. 보고서, Ledger, README 요약 표를 갱신한다.

## 9. 타당성 위협과 한계

- Track A에는 정답이 없다. naive가 틀렸다는 것이 아니라, 원천이 말하지 않는 가정을 고르면 결과가 크게
  달라진다는 것만 주장한다.
- naive 변형은 저자가 골랐고, 3절의 값을 본 뒤에 골랐다. 사전 탐색을 공개하고, 변형별 숨은 가정을 표에
  명시하는 것으로 완화한다.
- ForgeSync의 Machining Run 122도 하나의 segmentation 규칙이며 생산 실적의 정답이 아니다.
- 설비 1대, 하루 기록이다. 다른 설비, 다른 날짜, 실제 현장 전달 특성으로 일반화하지 않는다.
- 주입한 중복·역순 비율은 합성 값이다. 실제 QoS1 재전송률이나 네트워크 역순률을 주장하지 않는다.
- Alarm 규칙은 같은 데이터에서 만들어졌으므로 Alarm 건수의 적절성은 이 실험에서 평가하지 않는다.
- 역순 주입은 평가 실행에서만 V-019 보장을 의도적으로 깨며, 제품 실행 경로의 보장과 섞어 보고하지 않는다.
