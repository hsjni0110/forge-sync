# Naive Baseline 비교 결과

사전 등록: [설계 문서](../../superpowers/specs/2026-10-02-naive-baseline-comparison-design.md)
(4·5절, 10절 보충). 결과는 가설과 무관하게 그대로 기록한다.

## 요약

- 같은 Mazak01 하루 기록에서 사전 등록한 naive 규칙들은 생산 수량을 **0개와 137개**로, 가동률을
  **23.5%·31.5%·31.9%**로 서로 다르게 말한다. 어느 쪽도 원천이 뒷받침하지 않는 가정 위에 있다.
- ForgeSync는 같은 질문에 생산 실적 `NOT_OBSERVED`, 종합 OEE `UNAVAILABLE`과 사유를 돌려주고,
  가동률 23.47%와 함께 관측 범위의 25.5%가 `UNKNOWN`임을 공개한다. 가동률 값 자체는 naive
  `NA-UTIL-1`과 같다. 차이는 숫자가 아니라 모르는 부분을 드러내는 방식이다.
- 중복·역순 전달을 주입하면 naive 소비자는 현재 값이 과거 값으로 되돌아가고(최대 3,952회) 알람을
  중복으로 연다. ForgeSync의 상태 구간과 가공 런은 모든 시나리오에서 무장애 결과와 같았다.
- 실험 중 ForgeSync 결함 2건(재생 cursor 정렬과 OEE, 늦게 도착한 Warning의 알람 누락)과 확인이 필요한
  값 1건(Performance 757%)을 발견했다. 모두 아래에 그대로 남긴다.

## 재현

| 입력 | 값 |
|---|---|
| Raw artifact | `sha256:6eec7afdba356285d0fef70f43552996ab3f17802b3af5cbab243dd2676ef2cf` |
| Canonical run | `839ad138d6da7b2d5439c118d6f3bd88ab820824ae9f460c8e1848e6c638109c` (mapping `2.3.0`) |
| Replay | 100x, 마지막 published sequence 101692, 수집 cursor 101679 |

```bash
./scripts/evaluate-naive-baseline
```

결과: [`track-a-comparison.json`](./track-a-comparison.json),
[`track-a-collection.json`](./track-a-collection.json). 수집한 ForgeSync 응답 문서는
`datasets/evaluation/`에 로컬로 남고, 비교 결과에 각 문서의 SHA-256을 기록한다.

## Track A: 의미 해석

정답이 없는 Track이다. naive가 틀렸다는 판정이 아니라, 원천이 말하지 않는 가정을 고르면 결과가
얼마나 흔들리는지와 ForgeSync가 같은 질문에 무엇을 공개하는지를 나란히 둔다.

| 질문 | Naive 규칙 | Naive 값 | 숨은 가정 수 | ForgeSync |
|---|---|---:|---:|---|
| 생산 수량 | NA-PROD-1 부품 카운터 증가량 | 0 | 1 | 생산 실적 `NOT_OBSERVED`, 부품 카운트 `UNAVAILABLE`(`NO_USABLE_TRANSITIONS`) |
| | NA-PROD-2 ACTIVE 진입 횟수 | 137 | 1 | 가공 런 122건(완료 102, 상태 불명 11, 중단 9). 생산 실적으로 확정하지 않음 |
| 가동률 | NA-UTIL-1 ACTIVE / 관측 범위 | 23.47% | 1 | ACTIVE 23.47% `AVAILABLE`, `UNKNOWN` 25.50% 별도 공개 |
| | NA-UTIL-2 ACTIVE / UNAVAILABLE 제외 | 31.50% | 1 | (해당 계산 없음) |
| | NA-UTIL-3 누적 카운터 비율 | 31.90% | 1 | 31.56% `PARTIAL`, 상태 경로와 "다른 근거"로 명시 |
| 종합 OEE | NA-OEE-1 | 0.34% | 5 | `UNAVAILABLE`(`QUALITY_COMPONENT_UNAVAILABLE`) |
| | NA-OEE-2 | 0.46% | 5 | (위와 같음) |
| 정지 원인 | NA-CAUSE-1 | 59건 중 6건에 원인 표시 | 1 | 인과 주장 0건. 동시 근거 42건, 사유 미확인 17건 |

### 가설 판정

| ID | 판정 | 근거 |
|---|---|---|
| H-A1 | 확인 | NA-PROD 변형이 0과 137로 갈린다 |
| H-A2 | 확인 | NA-UTIL 변형 폭 8.43%p. ForgeSync는 `UNKNOWN` 25.50%를 공개한다 |
| H-A3 | 확인 | NA-OEE는 품질·계획시간·이상 cycle에 대한 근거 없는 가정 5개 위에서만 숫자가 된다 |
| H-A4 | 확인 | naive는 6건에 원인을 붙였고 ForgeSync는 같은 연결 기준에서 원인을 주장하지 않는다 |

### Naive OEE가 0.3%대인 이유

프로그램 155의 ACTIVE stretch는 134개이고 중앙값은 약 120초이지만 최솟값이 1.1초다. 사전 등록한
규칙은 최솟값을 이상 cycle time으로 쓰므로 Performance가 1.4%가 되고 OEE가 무너진다. 규칙은
사전 등록대로 유지했다. "naive OEE는 한 개의 이상치에 지배된다"는 것도 이 실험의 관찰이다.

## 실험 중 발견한 ForgeSync 문제

1. **재생 cursor 정렬 결함.** 상태 구간 projection은 요청 cursor를 마지막 상태 관찰로 당기고
   (101692 → 101679), 가공 런은 요청 cursor를 유지한다. OEE는 두 cursor가 같아야만 계산하므로,
   마지막 관찰이 상태 변화가 아닌 cursor에서는 400으로 실패한다. 웹 화면도 같은 순서로 호출한다.
   평가 코드는 모든 projection을 상태 구간이 정한 cursor로 맞춰 우회했고, 결함 수정은 별도 작업으로
   넘겼다.
2. **Performance 757%.** ForgeSync는 최근 cycle 8.5초를 과거 중앙값 64.5초와 비교한 Performance
   757%를 `AVAILABLE`로 공개한다. 종합 OEE는 만들지 않지만, 100%를 크게 넘는 구성 요소의 의미가
   ADR-053의 의도와 맞는지 확인이 필요하다. 결함으로 단정하지 않는다.
3. **Alarm 규칙 순환성.** `ConditionToAlarmPolicy` `1.0.0` allowlist가 이 raw의 Warning 코드 7종과
   같다. 그래서 Alarm 건수 비교는 하지 않는다.

## Track B: 전달 장애

정답이 있는 Track이다. 각 소비자를 **자기 자신의 무장애 실행(S0)**과 비교한다. naive와 ForgeSync는
브로커가 같은 순서로 전달한 같은 envelope를 받는다.

```bash
./scripts/evaluate-delivery-faults
```

결과: [`track-b-comparison.json`](./track-b-comparison.json), 시나리오별
`track-b-collection-S*.json`. 모든 시나리오에서 마지막 published sequence는 101692, 수집 cursor는
101679였다.

| 시나리오 | 주입 | 도착 envelope | Naive | ForgeSync |
|---|---|---:|---|---|
| S1 | 중복 1% | 102,712 (+1,019) | 차이 없음 | 차이 없음 |
| S2 | 중복 5% | 106,785 (+5,092) | 알람 열림 +1 | 차이 없음 |
| S3 | 역순 1%, 최대 50 | 101,693 | 과거 값 rollback 829회 | 차이 없음 |
| S4 | 중복 5% + 역순 5% | 106,783 (+5,090) | rollback 3,952회, 실행 상태 오귀속 15.2초, 알람 열림 +1 | **알람 1건 누락** |

모든 시나리오에서 두 소비자 모두 최종 상태 불일치 0, 가공 횟수 차이 0이었다. ForgeSync의 실행 상태
구간은 다섯 시나리오 모두 329개로 같았고 오귀속 시간은 0초였다.

### 가설 판정

| ID | 판정 | 근거 |
|---|---|---|
| H-B1 | 확인 | 중복만 주입한 S1·S2에서 ForgeSync의 모든 지표가 S0과 같다 |
| H-B2 | **부분 위반** | 상태 구간과 Run은 S3·S4에서 S0과 같다. 그러나 S4에서 알람 1건이 사라졌다. rollback은 REST로 관찰할 수 없어 판정하지 않았다 |
| H-B3 | 부분 확인 | S2·S3·S4에서 naive가 어긋났다. S1에서는 차이가 없었다 |
| H-B4 | 미실시 | Factory API 재시작 시나리오(S5)는 실행하지 않았다 |

### ForgeSync 결함: 늦게 도착한 Warning의 알람 누락

S4에서 `Mazak01-controller_3`의 코드 1101(INTERFERE) Warning(sequence 88446,
17:02:35.876)이 지연돼, 이를 해제하는 Normal(88450, 17:02:39.612)보다 늦게 도착했다. ForgeSync는
Normal을 먼저 처리했고, 늦게 온 Warning으로 알람을 열지 않았다. 원천 관찰은 Inbox 이력에 남아 있지만
업무 알람 생명주기는 도착 순서에 의존한다. 늦게 도착한 Warning을 어떻게 기록할지는 알람 의미
(ADR-055)에 관한 결정이므로, 회귀 테스트와 수정은 별도 작업으로 넘겼다.

### 해석할 때 주의할 점

- **최종 상태 지표는 판별력이 없다.** 원천 기록이 하루 끝에 모든 항목을 UNAVAILABLE로 보내고 끝나므로
  재생 끝의 최종 상태는 어느 소비자든 같아진다. 사전 등록한 지표라 그대로 보고했다.
- **중복은 연속으로 주입했다.** 같은 envelope를 바로 다시 보내므로 naive의 상태나 가공 횟수는 바뀌지
  않고 알람 열림만 늘었다. 실제 QoS1 재전송은 재연결 뒤 떨어져서 올 수 있으며, 그 경우는 시험하지 않았다.
- **역순은 대부분 고빈도 sample에 걸린다.** 실행 상태 envelope는 329개뿐이라 naive의 오귀속 시간이
  작다(S4 15.2초). rollback 횟수 차이가 더 큰 신호다.
- **ForgeSync rollback은 측정하지 않았다.** REST에 Twin 변경 이력이 없어 0으로 채우지 않고
  "관찰 불가"로 남겼다.
- 주입 비율은 합성 값이며 실제 현장의 재전송률이나 역순률을 주장하지 않는다.

## 한계

- Track A에는 정답이 없다. naive 변형은 저자가 골랐고, 사전 탐색 값을 본 뒤에 골랐다(설계 3절 공개).
- ForgeSync의 Machining Run 122건도 하나의 segmentation 규칙이며 생산 실적의 정답이 아니다.
- 설비 1대, 하루 기록이다. 다른 설비나 날짜로 일반화하지 않는다.
- Alarm 규칙은 같은 데이터에서 만들어졌으므로 Alarm 건수의 적절성은 평가하지 않는다.
- Track B는 시나리오마다 seed 1로 한 번씩만 실행했다. 다른 seed에서 결과가 달라질 수 있다.
- 평가 실행은 모든 projection을 상태 구간이 정한 cursor(101679)로 맞췄다. 이는 위 cursor 결함을
  우회한 선택이며 제품 화면의 호출 방식과 다르다.
