# ForgeSync — NIST Mazak + PHM 2010 확장 설계 및 수정 가이드

## 0. 문서 목적

이 문서는 현재 **NIST SMS Test Bed의 Mazak01 MTConnect 데이터**를 기반으로 진행 중인 ForgeSync를 최대한 유지하면서, 여기에 **PHM 2010 Tool Wear 데이터셋**을 추가하여 다음 수준으로 확장하기 위한 수정 가이드다.

기존 ForgeSync의 핵심은 다음 질문에 답하는 데 있었다.

> **“CNC가 지금 어떤 상태이고, 어떤 방식으로 가공 중인가?”**

PHM 2010을 추가한 이후 ForgeSync는 다음 질문까지 답할 수 있어야 한다.

> **“CNC가 지금 어떤 상태이고, 이 공구 상태로 계속 가공해도 되는가?”**

따라서 이번 변경의 목표는 기존 NIST-Mazak 파이프라인을 버리는 것이 아니라 다음 세 계층으로 확장하는 것이다.

```text
STATE
현재 CNC가 무엇을 하고 있는가?
        ↓
NIST Mazak / MTConnect

ANOMALY
평소와 다른 가공이 발생하고 있는가?
        ↓
Mazak cycle analytics

HEALTH
공구가 얼마나 열화되었고 얼마나 더 사용할 수 있는가?
        ↓
PHM 2010
```

최종적으로 ForgeSync는 단순한 설비 모니터링 UI가 아니라 **CNC Operational + Process + Tool Health Digital Twin**을 목표로 한다.

---

# 1. 가장 중요한 전제

## 1.1 NIST Mazak과 PHM 2010은 같은 기계 데이터가 아니다

두 데이터셋을 하나의 실제 공장에서 동시에 측정한 것처럼 취급하면 안 된다.

### NIST Mazak

- 출처: NIST Smart Manufacturing Systems Test Bed
- 장비: Mazak Integrex 100-IV
- 데이터: MTConnect
- 주요 정보:
  - Execution
  - Controller Mode
  - Program
  - Sequence / Line
  - Spindle RPM
  - Spindle Load
  - Axis Position
  - Feed
  - Tool 관련 상태
  - Part Count
  - Alarm / Condition

### PHM 2010

- 대상: CNC milling tool wear / tool remaining useful life
- 실제 고속 milling 실험 데이터
- 주요 정보:
  - Cutting Force
  - Vibration
  - Acoustic Emission
  - Cut Number
  - Flute별 Tool Wear
  - Tool degradation

따라서 다음 표현은 금지한다.

```text
Mazak01의 T05 실제 마모량 = PHM 데이터의 182 μm
```

대신 다음과 같이 구분한다.

```text
Mazak Machine Twin
Source: NIST
Mode: OBSERVED

Tool Health Reference Twin
Source: PHM 2010
Mode: REFERENCE / MODEL
```

향후 Mazak에서 실제 tool wear 센서를 확보하면 `REFERENCE`를 `OBSERVED` 또는 `ESTIMATED`로 승격할 수 있는 구조로 설계한다.

---

# 2. 기존 ForgeSync에서 유지해야 하는 것

현재 NIST-Mazak 기반으로 만든 다음 계층은 가능한 한 그대로 유지한다.

```text
NIST Mazak Raw Data
        ↓
MTConnect Ingestion
        ↓
Parsing / Normalization
        ↓
Machine State
        ↓
Time-series storage
        ↓
API
        ↓
Dashboard
```

다음 기능이 이미 있다면 삭제하지 않는다.

- Machine 등록
- MTConnect device/component 구조
- DataItem 파싱
- Sample/Event/Condition 분리
- 시계열 저장
- Machine state timeline
- Spindle RPM
- Feed
- Load
- Axis
- Program
- Alarm
- Availability
- Execution state
- 현재 상태 조회 API
- historical replay

이번 변경의 핵심은 그 위에 **Cycle**, **Tool**, **Health**라는 새로운 도메인을 얹는 것이다.

---

# 3. 가장 먼저 수정해야 할 도메인 중심

## 기존 중심

```text
Machine
 ├─ Telemetry
 ├─ Event
 └─ Condition
```

이 구조는 설비 모니터링에는 충분하지만, 가공이라는 의미 단위를 표현하기 어렵다.

## 변경 후 중심

```text
Machine
   │
   └── MachiningRun
          │
          ├── ProcessSnapshot
          ├── CycleFeature
          ├── ToolUsage
          │      │
          │      └── Tool
          │
          └── HealthAssessment
```

ForgeSync의 중요한 aggregate는 앞으로 `Machine`만이 아니라 **MachiningRun 또는 MachiningCycle**이 되어야 한다.

---

# 4. MachiningRun / Cycle을 추가하는 이유

MTConnect 원본은 연속 시계열이다.

```text
14:31:01 RPM 0
14:31:02 RPM 0
14:31:03 EXECUTION READY
14:31:04 PROGRAM O1234
14:31:05 RPM 1500
14:31:06 RPM 4100
14:31:07 LOAD 35
...
14:34:48 RPM 0
14:34:49 EXECUTION READY
```

하지만 제조 담당자가 궁금한 것은 이것이다.

```text
Cycle #10582
Start      14:31:03
End        14:34:49
Duration   226 sec
Program    O1234
Tool       T05
Avg RPM    3,972
Max Load   72%
Result     COMPLETED
```

따라서 Raw stream과 사용자 사이에 다음 변환 계층이 필요하다.

```text
MTConnect Stream
      ↓
State Transition Detection
      ↓
Cycle Segmentation
      ↓
MachiningRun
```

---

# 5. Cycle segmentation 설계

## 5.1 첫 버전

첫 구현은 복잡한 AI가 아니라 deterministic rule 기반으로 한다.

예시 기준:

```text
Cycle Start 후보
- EXECUTION이 ACTIVE로 전환
- Program이 유효한 값으로 변경
- spindle이 idle → running으로 전환

Cycle End 후보
- EXECUTION ACTIVE → READY
- Program 종료
- spindle RPM = 0 일정 시간 유지
```

세 신호를 조합하여 신뢰도를 높인다.

## 5.2 Cycle state

```text
PENDING
RUNNING
COMPLETED
INTERRUPTED
ABORTED
UNKNOWN
```

## 5.3 필요한 필드

```text
MachiningRun
- id
- machine_id
- program_name
- sequence_no
- started_at
- ended_at
- duration_ms
- run_status
- tool_id
- part_count_before
- part_count_after
- source
- confidence
```

`confidence`는 segmentation 규칙이 얼마나 명확했는지 표시하는 용도다.

---

# 6. CycleFeature를 별도 모델로 둔다

Raw telemetry를 매번 화면에서 다시 계산하지 않는다.

가공 cycle이 끝날 때 다음 feature를 계산한다.

```text
CycleFeature

Time
- cycle_time
- cutting_time
- idle_time

Spindle
- rpm_mean
- rpm_max
- rpm_std
- rpm_active_ratio

Load
- load_mean
- load_max
- load_std
- high_load_duration

Feed
- feed_mean
- feed_max
- feed_std

Axis
- x_travel
- y_travel
- z_travel

State
- tool_change_count
- alarm_count
- stop_count
```

이 값들은 이후 anomaly detection과 비교 분석에서 사용한다.

---

# 7. Mazak 데이터에서 추가해야 하는 분석

PHM을 붙이기 전에 먼저 Mazak 데이터만으로 다음 기능을 구현한다.

## 7.1 Cycle 비교

```text
Cycle #181

Cycle Time      243 sec
Baseline        231 sec
Difference      +5.2%

Load Mean       61%
Baseline        49%
Difference      +12%p
```

## 7.2 Baseline

같은 Program 또는 비슷한 operating pattern의 과거 cycle들을 묶는다.

```text
Program O1234

Cycle 001
Cycle 002
Cycle 003
...
Cycle 030

        ↓

Baseline

Cycle Time  median
RPM         median / IQR
Load        median / IQR
Feed        median / IQR
```

초기에는 평균/표준편차보다 median/IQR도 함께 고려한다.

## 7.3 AnomalyAssessment

```text
AnomalyAssessment
- run_id
- model_version
- score
- cycle_time_score
- load_score
- spindle_score
- feed_score
- reasons
- created_at
```

사용자에게는 반드시 원인을 함께 보여준다.

```text
Anomaly Score: 0.81

Possible Reasons
- Cycle time +14.2%
- Spindle load +18.1%
- Feed variability +31.4%
```

단순히 `abnormal`만 표시하지 않는다.

---

# 8. PHM 2010을 별도 Bounded Context로 추가

프로젝트 내부 구조를 다음처럼 분리한다.

```text
forgesync/
│
├── machine/
│   ├── mtconnect/
│   ├── machine_state/
│   └── cycle/
│
├── health/
│   ├── phm2010/
│   ├── feature/
│   ├── wear/
│   └── rul/
│
├── twin/
│   ├── asset/
│   ├── process/
│   └── health/
│
└── visualization/
```

PHM 파서를 MTConnect parser 안에 넣지 않는다.

데이터 모델도 서로 독립된 source adapter를 갖는다.

---

# 9. PHM 2010 ingestion

## 입력

각 cut에 대해 고주파 센서 신호가 들어온다.

```text
Cut
 ├─ Force X
 ├─ Force Y
 ├─ Force Z
 ├─ Vibration X
 ├─ Vibration Y
 ├─ Vibration Z
 └─ Acoustic Emission
```

그리고 별도의 ground truth tool wear 측정값이 존재한다.

## 내부 표준 형태

```text
PHMCut
- dataset
- cutter_id
- cut_no
- started_at_virtual
- sample_rate
- sensor_file
- wear_available
```

```text
ToolWearMeasurement
- cutter_id
- cut_no
- flute_no
- wear_um
- measurement_type
- source
```

실제 시간이 없는 데이터라면 인위적인 real timestamp를 붙이지 말고 `cut_no`를 sequence identity로 사용한다.

---

# 10. PHM Feature Extraction

원본 50 kHz급 고주파 데이터를 매번 웹 UI로 전송하지 않는다.

각 cut에서 feature를 만든다.

## 초기 feature

### 시간 영역

```text
mean
std
rms
peak
peak_to_peak
skewness
kurtosis
crest_factor
```

### Force

```text
Fx RMS
Fy RMS
Fz RMS

Resultant Force
sqrt(Fx² + Fy² + Fz²)
```

### Vibration

```text
Vx RMS
Vy RMS
Vz RMS
```

### Acoustic Emission

```text
AE RMS
AE peak
AE energy
```

## 이후 확장

필요하면 FFT 기반 feature를 추가한다.

```text
dominant_frequency
spectral_centroid
band_energy
```

첫 버전부터 FFT 기반 수십 개 feature를 만들지는 않는다.

---

# 11. Tool Wear 모델

첫 번째 목표는 RUL보다 **Wear Estimation**으로 둔다.

이유:

- 결과를 이해하기 쉽다.
- PHM ground truth와 직접 비교 가능하다.
- 모델 검증이 쉽다.
- 이후 RUL의 기반이 된다.

## 입력

```text
PHM Cut Features
+
Cut Number
```

## 출력

```text
Estimated Tool Wear μm
```

## 모델 baseline

첫 구현 권장:

```text
Linear Regression
Random Forest
Gradient Boosting / XGBoost
```

딥러닝은 baseline 이후에 추가한다.

## 평가

```text
MAE
RMSE
R²
```

모델별 비교표를 기록한다.

---

# 12. Tool Health Score

사용자가 `wear = 182 μm`만 봐서는 의미를 이해하기 어렵다.

따라서 ForgeSync 내부에 UI용 health abstraction을 만든다.

예:

```text
0 ~ 100

100 = new / healthy
0   = replacement threshold 도달
```

단, 임의의 threshold를 사실처럼 제시하면 안 된다.

그래서 다음 구조를 사용한다.

```text
HealthAssessment
- tool_id
- assessment_type
- wear_estimate
- wear_unit
- health_score
- threshold_source
- model_id
- confidence
- provenance
```

`threshold_source` 예시:

```text
PHM_CHALLENGE_REFERENCE
EXPERIMENTAL
USER_CONFIGURED
OEM
UNKNOWN
```

---

# 13. RUL은 두 번째 단계에서 추가

RUL을 처음부터 무리하게 구현하지 않는다.

## 의미

```text
RUL = Remaining Useful Life
```

ForgeSync에서는 사용자가 이해하기 쉽게 다음 단위가 좋다.

```text
Estimated Remaining Cuts
```

예:

```text
Current Cut      221
Estimated RUL     37 cuts
```

실제 시간이 아닌 경우:

```text
37 hours
```

처럼 바꾸지 않는다.

데이터가 cut 기반이면 RUL도 cut 기반으로 유지한다.

---

# 14. NIST Mazak과 PHM을 연결하는 올바른 방법

두 데이터셋을 row 단위로 합치지 않는다.

다음과 같은 **추상화 수준에서 통합**한다.

```text
                 ForgeSync Twin

                      Machine
                         │
                         ▼
                    MachiningRun
                         │
              ┌──────────┴──────────┐
              │                     │
              ▼                     ▼
        Process State          Health State
              │                     │
         NIST Mazak              PHM2010
              │                     │
        RPM / Load / Feed      Wear / RUL
```

즉 데이터는 다르지만 사용자가 보는 제조 개념은 통합한다.

---

# 15. Provenance를 반드시 시스템 레벨에서 관리

ForgeSync의 주요 차별화 포인트로 삼을 수 있다.

모든 상태 값에는 provenance를 둔다.

```text
OBSERVED
실제 센서에서 직접 관측

DERIVED
관측값으로 계산

ESTIMATED
모델이 추정

SIMULATED
가정 기반 시뮬레이션

REFERENCE
다른 공개 데이터에서 학습/재현
```

예:

```text
Spindle RPM
4,821 rpm
OBSERVED
Source: NIST MTConnect

Cycle Anomaly
0.81
DERIVED
Source: Mazak Cycle Features

Tool Wear
182 μm
ESTIMATED
Model: PHM ToolWear v1

PHM Demonstration Tool
Measured Wear: 179 μm
OBSERVED
Source: PHM 2010
```

이 구분은 UI에서도 보이도록 한다.

---

# 16. 3D Digital Twin 방향 수정

기존에 공장 전체를 3D로 구현할 계획이었다면 우선순위를 낮춘다.

목표는 게임 같은 공장 탐색이 아니다.

## 16.1 필요한 3D

### Machine Exterior

실제 Mazak Integrex 100-IV와 시각적으로 유사한 simplified model.

표현해야 할 요소:

```text
Cabinet
Door
Window
Control Panel
Machine Work Area
```

### Machine Internal Functional Components

```text
Main Spindle
Sub Spindle
Milling Spindle
Chuck
Tool
X-axis
Y-axis
Z-axis
B-axis
C-axis
Workpiece placeholder
```

외부 볼트/배선 등의 디테일은 우선순위가 낮다.

---

# 17. MTConnect ↔ 3D Node Mapping

3D 파일은 기능 단위 node로 분리한다.

```text
Mazak01.glb

MazakRoot
├── Exterior
├── Door
├── ControlPanel
├── MainSpindle
├── SubSpindle
├── MillingSpindle
├── Chuck
├── XAxisAssembly
├── YAxisAssembly
├── ZAxisAssembly
├── BAxisAssembly
├── CAxisAssembly
└── ToolMount
```

매핑 테이블:

```text
TwinComponentBinding

- machine_id
- mtconnect_component_id
- data_item_id
- gltf_node
- binding_type
- scale
- offset
```

예:

```text
spindle_speed
        ↓
MainSpindle.rotation

B_axis_angle
        ↓
BAxisAssembly.rotation

X_position
        ↓
XAxisAssembly.translation
```

---

# 18. 3D에서 무엇을 실제로 움직일 것인가

첫 버전에서 모두 구현하려 하지 않는다.

우선순위:

## P0

```text
Execution
Spindle RPM
B-axis angle
Tool number
```

## P1

```text
X / Y / Z position
C-axis
Door state
```

## P2

```text
실제 cutting contact
chip animation
coolant
정교한 tool change animation
```

P2는 포트폴리오 핵심이 아니므로 뒤로 미룬다.

---

# 19. Tool 3D Twin

공구는 기계 외관보다 더 기능적으로 정확해야 한다.

구성:

```text
Tool Assembly
├── Tool Holder
└── Cutter
     ├── Flute 1
     ├── Flute 2
     ├── Flute 3
     └── Wear Zone
```

PHM 데이터에서 flute별 wear가 있다면 해당 위치를 강조한다.

예:

```text
Flute 1    141 μm
Flute 2    178 μm
Flute 3    152 μm
```

3D에서는 실제 형상을 억지로 깎아내지 않고:

```text
Wear Zone highlight
+
측정값 표시
```

만 한다.

---

# 20. 3D 표현의 진실성 규칙

### 가능한 표현

```text
Measured wear zone
Estimated health
Observed spindle rotation
Observed axis position
```

### 금지할 표현

실측 정보가 없는데 실제 파손 형상처럼 시각화하는 것.

```text
실제로 이런 모양으로 깨졌다고 단정
```

대신:

```text
Wear indicator
Estimated region
Simulation overlay
```

로 표시한다.

---

# 21. UI 구조 수정

## 기존

```text
Machine Overview

Status
RPM
Feed
Load
Alarm
Timeline
```

## 변경

```text
Machine Overview
        │
        ├── LIVE / REPLAY
        │
        ├── CURRENT RUN
        │
        ├── PROCESS
        │
        ├── ANOMALY
        │
        └── TOOL HEALTH
```

---

# 22. 추천 Machine Detail 화면

```text
┌────────────────────────────────────────────────────┐
│ MAZAK01                               ● RUNNING    │
│ NIST SMS Test Bed                                │
├───────────────────────────┬────────────────────────┤
│                           │ CURRENT RUN            │
│                           │                        │
│     Mazak 3D Twin         │ Run     #10583         │
│                           │ Program O1234          │
│                           │ Tool    T05            │
│                           │ Cycle   03:42          │
│                           │                        │
├───────────────────────────┼────────────────────────┤
│ PROCESS                   │ ANOMALY                │
│ RPM      4,821            │ Score    0.18          │
│ Feed     142              │ Status   NORMAL        │
│ Load      63%             │                        │
├───────────────────────────┴────────────────────────┤
│ TOOL HEALTH                                       │
│                                                   │
│ Tool T05       Health 63%      RUL 41 cuts        │
│                                                   │
│ [Tool 3D]      Wear estimate 182 μm               │
│                                                   │
│ Source: PHM Reference Model                       │
└───────────────────────────────────────────────────┘
```

---

# 23. Replay가 핵심 기능

Digital Twin을 단순 현재 상태 dashboard로 끝내지 않는다.

NIST 데이터는 historical dataset이므로 **Replay Mode**가 매우 중요하다.

```text
14:31 ━━━━━━━●━━━━━━━━━━━━ 14:42

PLAY
PAUSE
1x
5x
20x
```

시간을 움직이면:

```text
MTConnect Event
      ↓
Twin State
      ↓
3D State
      ↓
Charts
      ↓
Cycle State
```

가 동일한 timestamp를 기준으로 함께 움직인다.

---

# 24. Replay Architecture

```text
ReplayClock
    │
    ├── MachineTelemetryProjection
    ├── MachineStateProjection
    ├── Twin3DProjection
    ├── CycleProjection
    └── AlarmProjection
```

모든 UI가 각자 시간을 계산하지 않는다.

하나의 `ReplayClock`을 기준으로 동작하도록 한다.

---

# 25. API 수정 제안

예시:

```text
GET /machines
GET /machines/{id}
GET /machines/{id}/state

GET /machines/{id}/runs
GET /machines/{id}/runs/{runId}

GET /runs/{runId}/features
GET /runs/{runId}/anomaly

GET /tools
GET /tools/{id}
GET /tools/{id}/health

GET /health/models
GET /health/models/{id}

GET /replay/{machineId}
```

PHM 데모 전용:

```text
GET /datasets/phm2010/cutters
GET /datasets/phm2010/cutters/{id}/cuts
GET /datasets/phm2010/cutters/{id}/wear
```

---

# 26. DB 변경 제안

## machine

```text
machine
machine_component
data_item
telemetry
machine_event
condition_event
```

기존 유지.

## process

```text
machining_run
cycle_feature
anomaly_assessment
```

추가.

## tool

```text
tool
tool_usage
tool_wear_measurement
health_assessment
```

추가.

## ml

```text
model_registry
model_run
prediction
feature_schema
```

선택적으로 추가.

---

# 27. Tool과 Mazak Tool Number를 바로 동일시하지 않는다

현재 NIST에서 `T05`를 관측했다고 해도 그 실제 cutter geometry나 PHM cutter와 동일하다는 근거가 없다.

따라서:

```text
MachineToolSlot
- machine_id
- tool_number
```

와

```text
ReferenceTool
- dataset
- cutter_id
- geometry
```

를 분리한다.

향후 실제 매핑 근거가 생기면 연결한다.

---

# 28. ML Serving 구조

처음부터 별도 거대한 AI 인프라는 필요 없다.

추천:

```text
Training
Python Notebook / Script
        ↓
Model Artifact
        ↓
Model Registry
        ↓
Inference Service
```

초기에는 backend 내부 service로도 충분하다.

모델별로 반드시 기록:

```text
model_id
model_name
version
dataset
feature_schema
training_date
metrics
artifact_path
```

---

# 29. Verification Ledger 확장

각 기능은 “구현됨”만 기록하지 않고 무엇을 검증했는지 남긴다.

예:

```text
VR-CYCLE-001

Claim
ACTIVE → READY 구간이 machining run으로 분리됨.

Evidence
Mazak01 sample 2016-XX-XX

Expected
Run count = 42

Actual
Run count = 42

Status
PASS
```

PHM:

```text
VR-PHM-WEAR-001

Claim
Wear model이 unseen cutter/cut에 대해 baseline보다 낮은 MAE를 보임.

Dataset
PHM2010

Metric
MAE

Status
PASS
```

3D:

```text
VR-TWIN-003

Claim
B-axis MTConnect 값과 3D BAxisAssembly 각도가 동일함.

Input
42.7 deg

Rendered
42.7 deg

Status
PASS
```

---

# 30. 개발 Step 재구성

## Step 01 — 기존 Mazak pipeline 동결 및 회귀 테스트

목표:

기존 기능이 깨지지 않는 기준점을 만든다.

완료 조건:

- NIST Mazak dataset ingestion 성공
- 주요 DataItem 파싱 성공
- Machine State API 회귀 테스트
- Replay 데이터 조회 가능

---

## Step 02 — MachiningRun 도메인 추가

목표:

연속 MTConnect stream을 가공 run 단위로 변환한다.

구현:

- run start/end detector
- run persistence
- status
- program association

완료 조건:

- 동일 입력에서 deterministic run segmentation
- run별 start/end 확인
- raw telemetry traceability 유지

---

## Step 03 — CycleFeature 생성

목표:

Run 비교 가능한 제조 feature를 만든다.

구현:

- RPM
- Feed
- Load
- Duration
- State statistics

완료 조건:

- run 종료 후 feature 생성
- 수동 계산 결과와 일치

---

## Step 04 — Cycle Baseline / Anomaly

목표:

“평소와 다른 가공”을 감지한다.

초기 구현:

- robust z-score
- IQR
- Isolation Forest 중 하나

완료 조건:

- anomaly score 제공
- 원인 feature Top-N 제공

---

## Step 05 — PHM 2010 Raw Adapter

목표:

PHM dataset을 ForgeSync 내부 표준 모델로 읽는다.

완료 조건:

- cutter
- cut
- sensor channel
- wear measurement

조회 가능.

---

## Step 06 — PHM Feature Extraction

목표:

고주파 sensor stream을 cut-level feature로 변환.

완료 조건:

- 동일 입력에 deterministic feature
- NaN / overflow 처리
- feature version 저장

---

## Step 07 — Tool Wear Baseline

목표:

공구 마모 추정 모델 구축.

최소 비교:

- Linear Regression
- Random Forest
- Gradient Boosting

완료 조건:

- train/test split 명시
- MAE/RMSE 기록
- model artifact 저장

---

## Step 08 — HealthAssessment Domain

목표:

모델별 결과를 UI와 domain에서 통일.

구현:

```text
HealthAssessment
Wear
Health Score
RUL
Confidence
Provenance
Model
```

완료 조건:

PHM model 결과가 공통 API로 조회 가능.

---

## Step 09 — RUL Baseline

목표:

남은 공구수명을 cut 단위로 제공.

완료 조건:

- RUL definition 문서화
- train/test leakage 방지
- error metric 기록

---

## Step 10 — Twin Component Model

목표:

Machine / Component / Tool을 3D와 매핑 가능한 형태로 확장.

구현:

```text
TwinComponent
TwinComponentBinding
```

완료 조건:

MTConnect component id → Twin node 조회 가능.

---

## Step 11 — Mazak Simplified 3D Model

목표:

실제 Mazak Integrex 100-IV와 식별 가능한 외형을 가진 low/medium poly model 확보.

필수 분리:

- Exterior
- MainSpindle
- SubSpindle
- MillingSpindle
- X/Y/Z
- B/C
- ToolMount

완료 조건:

glTF/GLB node 이름 고정.

---

## Step 12 — 3D State Binding

목표:

실제 MTConnect 값을 3D 동작으로 연결.

P0:

- spindle
- B-axis
- tool
- execution

완료 조건:

Replay timestamp에 따라 모델 상태 변경.

---

## Step 13 — Replay Synchronization

목표:

차트, 3D, run, alarm을 하나의 timeline으로 동기화.

완료 조건:

한 시점 이동 시 모든 projection의 timestamp 동일.

---

## Step 14 — Tool 3D Twin

목표:

PHM tool wear를 사용자가 물리적으로 이해할 수 있게 시각화.

구현:

- holder
- cutter
- flute
- wear zone

완료 조건:

flute별 wear 값과 UI 위치 대응.

---

## Step 15 — Machine Detail UI 통합

목표:

다음 4가지 정보를 한 화면에서 이해 가능하게 만든다.

```text
Machine State
Current Run
Anomaly
Tool Health
```

완료 조건:

사용자가 raw signal을 몰라도 현재 상태 설명 가능.

---

# 31. v1에서 하지 않을 것

범위가 폭발하지 않게 명시적으로 제외한다.

```text
X 실제 Box Assembly integration
X 실제 Mazak product identity 추정
X 공장 전체 ultra-realistic 3D
X CFD / physics simulation
X 실제 chip simulation
X coolant simulation
X full FEM
X 모든 CNC 기종 지원
X tool wear 실측 없는 상태에서 Mazak wear를 사실로 표시
```

---

# 32. v1 최종 데모 시나리오

## Demo 1 — Mazak Replay

사용자가 Mazak01을 선택한다.

```text
14:31
READY
```

PLAY.

```text
14:32
ACTIVE

Spindle
0 → 4,800 rpm

B-axis
0 → 31°
```

3D가 함께 움직인다.

---

## Demo 2 — MachiningRun

ForgeSync가 자동으로 묶는다.

```text
Run #10583

Program
O1234

Duration
243 sec

RPM mean
4,192

Load mean
54%
```

---

## Demo 3 — Anomaly

다음 cycle에서:

```text
Cycle Time
+14%

Load
+21%

Anomaly
HIGH
```

ForgeSync:

```text
This cycle differs from the recent baseline.

Possible contributors:
1. Spindle load
2. Cycle time
```

---

## Demo 4 — Tool Health Reference

PHM 2010 Tool Twin 화면:

```text
Cutter C1
Cut #221

Measured Wear
174 μm

Estimated Wear
181 μm

Health
62%

Estimated RUL
38 cuts
```

3D cutter의 flank wear zone을 강조한다.

---

## Demo 5 — 통합 관점

최종 overview:

```text
MAZAK01

Machine
RUNNING

Process
NORMAL

Current Run
#10583

Anomaly
LOW

Tool Health
REFERENCE MODEL AVAILABLE
```

사용자에게 명확히 보여준다.

```text
Machine telemetry
Observed from NIST Mazak

Tool health model
Validated using PHM 2010
```

---

# 33. ForgeSync의 최종 설명 문장

기존:

> MTConnect 기반 제조 설비 모니터링 플랫폼

변경:

> **ForgeSync is a manufacturing digital twin platform that reconstructs CNC operations from MTConnect data, segments machining cycles, detects process anomalies, and integrates tool degradation models for explainable machine and tool health monitoring.**

한국어:

> **ForgeSync는 MTConnect 데이터를 기반으로 CNC 가공 상태와 가공 사이클을 재구성하고, 공정 이상과 공구 열화를 함께 분석하여 기계·공정·공구 상태를 설명 가능한 형태로 보여주는 제조 디지털 트윈 플랫폼이다.**

---

# 34. 아키텍처 최종 그림

```text
                        ┌──────────────────────┐
                        │      ForgeSync       │
                        └──────────┬───────────┘
                                   │
              ┌────────────────────┼────────────────────┐
              │                    │                    │
              ▼                    ▼                    ▼

        Operational Twin      Process Twin        Health Twin
              │                    │                    │
              │                    │                    │
         NIST Mazak          Mazak Cycle            PHM 2010
              │                    │                    │
         MTConnect          Segmentation          Sensor Signal
              │                    │                    │
      ┌───────┼───────┐      CycleFeature       Feature Extraction
      │       │       │            │                    │
     RPM     Feed    Load       Anomaly             Wear Model
      │       │       │            │                    │
      └───────┴───────┘            │                   RUL
              │                    │                    │
              └────────────────────┼────────────────────┘
                                   │
                                   ▼
                            Unified Twin State
                                   │
                    ┌──────────────┼──────────────┐
                    │              │              │
                    ▼              ▼              ▼
                 REST API        Replay          3D Twin
                                                    │
                                              Mazak / Tool
```

---

# 35. 당장 다음 작업

현재 구현에서 가장 먼저 수정할 것은 PHM parser가 아니다.

순서는 반드시 다음을 권장한다.

```text
1. 현재 Mazak ingestion 회귀 테스트 고정
        ↓
2. MachiningRun 모델 추가
        ↓
3. 실제 Mazak stream cycle segmentation
        ↓
4. CycleFeature
        ↓
5. Cycle comparison / anomaly
        ↓
6. PHM ingestion
        ↓
7. Tool Wear model
        ↓
8. HealthAssessment
        ↓
9. 3D component binding
        ↓
10. Replay + Tool Health UI
```

이 순서를 따르면 현재 작성한 NIST-Mazak 코드를 거의 버리지 않고 ForgeSync를 훨씬 의미 있는 Digital Twin 프로젝트로 확장할 수 있다.

---

# 36. 핵심 의사결정 요약

### 유지

- NIST Mazak
- MTConnect
- 기존 ingestion
- machine state
- time-series
- backend/API 기반
- replay 방향

### 추가

- MachiningRun
- CycleFeature
- Cycle anomaly
- PHM 2010 adapter
- Tool Wear
- RUL
- HealthAssessment
- Provenance
- Mazak 3D functional twin
- Tool 3D twin

### 수정

```text
Machine-centric
        ↓
Machine + MachiningRun-centric

Monitoring
        ↓
Monitoring + Analysis + Health

3D Factory
        ↓
Functional Machine / Tool Twin

Sensor Dashboard
        ↓
Manufacturing Decision Support
```

### 절대 혼동하지 않을 것

> PHM 2010의 실제 공구 마모 측정값은 NIST Mazak에서 측정된 값이 아니다.

ForgeSync는 두 데이터셋을 **동일 제조 사실로 합치는 것이 아니라**, NIST로 Operational Twin을 만들고 PHM으로 Health Twin을 검증한 뒤 하나의 Digital Twin 플랫폼 인터페이스 안에서 통합한다.
