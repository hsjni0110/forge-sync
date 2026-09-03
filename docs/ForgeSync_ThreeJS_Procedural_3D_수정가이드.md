# ForgeSync 3D Digital Twin 수정 가이드
## Blender 없이 Three.js Procedural Model로 시작하는 방향

> 목적: 현재 ForgeSync의 NIST Mazak 기반 Digital Twin 구현을 유지하면서, Blender 학습 없이 Three.js만으로 기능적인 3D CNC Twin을 먼저 완성하고 이후 필요 시 정교한 GLB/GLTF 모델로 교체할 수 있도록 구조를 수정한다.

---

# 1. 수정 방향 요약

현재 ForgeSync의 3D 목표를 다음과 같이 변경한다.

기존에 생각했던 방향:

```text
실제 Mazak과 최대한 비슷한 정교한 3D 모델
        ↓
Three.js에서 로드
        ↓
MTConnect 데이터 연결
```

수정 방향:

```text
NIST MTConnect
        ↓
TwinState
        ↓
3D Binding Layer
        ↓
Three.js Procedural Model
        ↓
향후 GLB/GLTF로 선택적 교체
```

핵심 원칙은 다음과 같다.

1. Blender는 v1 필수 기술에서 제외한다.
2. CNC 외형은 Three.js 기본 Geometry를 조합해서 만든다.
3. 외형보다 `Spindle`, `Chuck`, `B Axis`, `Tool`, `Workpiece` 등 기능적 컴포넌트를 우선한다.
4. MTConnect 데이터를 Three.js object에 직접 연결하지 않는다.
5. 중간에 `TwinState`를 둔다.
6. 기계 모델과 공구 모델은 교체 가능한 Adapter 형태로 만든다.
7. 향후 GLB를 확보하거나 직접 제작할 수 있게 되면 데이터 처리 코드를 수정하지 않고 3D Asset만 교체한다.
8. 3D는 장식이 아니라 "제조 데이터를 물리적 동작으로 해석하는 화면"이어야 한다.

---

# 2. ForgeSync에서 3D가 해결해야 하는 문제

ForgeSync의 3D 목적은 다음 질문에 답하는 것이다.

```text
이 CNC가 지금 무엇을 하고 있는가?
```

숫자 Dashboard만 보면:

```text
Execution = ACTIVE
RPM       = 4821
B         = 37.2
Tool      = T03
X         = 121.4
Z         = -31.2
```

제조 도메인에 익숙하지 않은 사용자는 이 값이 실제 기계에서 무엇을 의미하는지 바로 이해하기 어렵다.

3D Twin은 이를 다음과 같이 바꾼다.

```text
RPM 4821
    ↓
Chuck / Spindle이 회전

B = 37.2°
    ↓
Milling Head가 기울어짐

Tool = T03
    ↓
현재 Tool Model 표시

X/Z 변화
    ↓
공구 또는 축 이동

Execution = ACTIVE
    ↓
Machine Running
```

즉 ForgeSync의 3D 핵심 가치는:

```text
Raw Manufacturing Data
        ↓
Physical Interpretation
```

이다.

---

# 3. v1에서 하지 않을 것

초기 구현에서 다음 작업은 제외한다.

```text
- Mazak OEM CAD 수준의 정밀 복제
- Blender 학습
- 볼트, 배선, 호스까지 실제처럼 모델링
- 실제 칩 생성
- 냉각수 유동 애니메이션
- 공구 마모 형상을 실제 μm 단위로 변형
- 실제 소재 제거를 Boolean 연산으로 실시간 구현
- 전체 공장 Walk-through
- VR/AR
- 모든 공구 종류 구현
```

이 기능들은 Digital Twin 핵심 가치에 비해 개발 비용이 너무 높다.

---

# 4. v1의 목표 모델

Three.js Primitive Geometry를 이용해 다음 정도의 CNC 구조를 만든다.

```text
┌──────────────────────────────────────┐
│                                      │
│   Main Chuck                         │
│      ●████████ Workpiece             │
│                         ╲             │
│                          ╲ Head       │
│                           ╲           │
│                            Tool       │
│                                      │
└──────────────────────────────────────┘
                             ┌────────┐
                             │Control │
                             └────────┘
```

외관은 실제 Mazak Integrex 계열의 특징을 일부 반영하되, 정확한 복제품으로 만들지 않는다.

---

# 5. 3D 모델의 최우선 구조

3D object hierarchy는 다음처럼 구성한다.

```text
MazakTwin
│
├── StaticBody
│   ├── Enclosure
│   ├── Door
│   ├── Window
│   ├── ControlPanel
│   └── MachineBed
│
├── MainSpindleGroup
│   ├── MainSpindle
│   ├── MainChuck
│   └── WorkpieceMount
│       └── Workpiece
│
├── SubSpindleGroup
│   └── SubChuck
│
├── AxisGroup
│   ├── XAxis
│   ├── YAxis
│   └── ZAxis
│
└── BAxisPivot
    └── MillingHead
        └── ToolSpindle
            └── ToolMount
                └── ActiveTool
```

이 구조는 시각적인 모델보다 중요하다.

이유는 MTConnect 데이터가 결국 이 component hierarchy와 연결되기 때문이다.

---

# 6. Three.js Procedural Model 구조

추천 파일 구조:

```text
frontend/
└── src/
    └── twin3d/
        ├── machine/
        │   ├── createMazakTwin.ts
        │   ├── createEnclosure.ts
        │   ├── createChuck.ts
        │   ├── createSpindle.ts
        │   ├── createMillingHead.ts
        │   └── createWorkpiece.ts
        │
        ├── tools/
        │   ├── createTool.ts
        │   ├── createTurningTool.ts
        │   ├── createDrill.ts
        │   ├── createEndMill.ts
        │   └── createFaceMill.ts
        │
        ├── binding/
        │   ├── MachineTwinBinding.ts
        │   ├── SpindleBinding.ts
        │   ├── AxisBinding.ts
        │   ├── ToolBinding.ts
        │   └── SceneBinding.ts
        │
        ├── state/
        │   ├── TwinState.ts
        │   └── TwinStateMapper.ts
        │
        └── replay/
            └── TwinReplayController.ts
```

---

# 7. Machine Model Factory

기계 모델 생성 로직을 UI Component 내부에 직접 작성하지 않는다.

예:

```ts
export function createMazakTwin(): THREE.Group {
  const machine = new THREE.Group();
  machine.name = "MazakTwin";

  const enclosure = createEnclosure();
  const spindle = createMainSpindle();
  const head = createMillingHead();

  machine.add(enclosure);
  machine.add(spindle);
  machine.add(head);

  return machine;
}
```

Three.js 모델은 가능한 한 `THREE.Group` 단위로 나눈다.

---

# 8. Blender를 대신하는 Primitive Geometry

## 8.1 Enclosure

```text
BoxGeometry
```

사용.

역할:

```text
CNC 외장
Door
Control Panel
Machine Bed
```

---

## 8.2 Spindle / Chuck

```text
CylinderGeometry
```

사용.

예:

```ts
const chuck = new THREE.Mesh(
  new THREE.CylinderGeometry(0.35, 0.35, 0.22, 32),
  chuckMaterial
);
```

필요하면 chuck jaw를 작은 BoxGeometry 3개로 표현한다.

```text
        ┌─ jaw
       /
   ── ● ──
       \
        └─ jaw
```

정밀한 jaw geometry는 필요 없다.

---

# 9. Workpiece 표현

Mazak01 데이터에서는 정확히 어떤 부품을 가공했는지가 명확하게 식별되지 않는 경우가 있으므로, v1에서는 대표 소재를 사용한다.

예:

```text
Cylinder Stock
```

Three.js:

```ts
new THREE.CylinderGeometry(...)
```

UI에는 반드시 provenance를 표시한다.

```text
Workpiece
Representative Geometry

SIMULATED
```

실제 NIST 제품 CAD라고 오해하게 만들지 않는다.

---

# 10. Milling Head

Milling Head는 BoxGeometry와 CylinderGeometry를 조합한다.

```text
             Head Body
           ┌─────────┐
           │         │
           └────┬────┘
                │
             Spindle
                │
             ToolMount
                │
               Tool
```

v1에서는 실루엣이 충분하다.

---

# 11. B Axis Pivot은 반드시 별도 Group

ForgeSync 3D에서 가장 중요한 구현 중 하나다.

잘못된 구조:

```text
MillingHead
```

만 존재.

권장 구조:

```text
BAxisPivot
    └── MillingHead
        └── ToolMount
```

Three.js:

```ts
const bAxisPivot = new THREE.Group();
bAxisPivot.name = "BAxisPivot";

bAxisPivot.add(millingHead);
```

MTConnect 데이터:

```text
B = 37.2°
```

가 들어오면:

```ts
bAxisPivot.rotation.z =
  THREE.MathUtils.degToRad(37.2);
```

로 변환한다.

---

# 12. 실제 좌표와 Three.js 좌표 분리

MTConnect 값과 Three.js 좌표를 바로 동일하게 사용하지 않는다.

예:

```text
MTConnect

X = 125.4 mm
```

를 그대로:

```ts
mesh.position.x = 125.4
```

하지 않는다.

대신:

```text
Physical Coordinate
        ↓
Coordinate Mapper
        ↓
Visualization Coordinate
```

구조를 둔다.

예:

```ts
function machineXToScene(xMm: number): number {
  return xMm * MM_TO_SCENE;
}
```

추후 실제 기계 좌표계와 Three.js scene 좌표계가 다르더라도 쉽게 수정 가능하다.

---

# 13. MTConnect → Three.js 직접 연결 금지

다음 코드는 피한다.

```ts
if (mtconnect.rpm > 0) {
  chuck.rotation.x += 0.2;
}
```

이 방식은 데이터 parsing, domain logic, 3D visualization이 서로 강하게 결합된다.

대신 다음 구조를 사용한다.

```text
MTConnect
    ↓
Normalized Machine State
    ↓
TwinState
    ↓
3D Binding
    ↓
Three.js
```

---

# 14. TwinState 추가

예:

```ts
export interface TwinState {
  timestamp: string;

  execution: {
    state: "READY" | "ACTIVE" | "STOPPED" | "UNKNOWN";
  };

  spindle: {
    rpm: number | null;
    load: number | null;
  };

  axes: {
    x: number | null;
    y: number | null;
    z: number | null;
    b: number | null;
  };

  tool: {
    number: string | null;
    type?: ToolType;
  };

  scene?: {
    type: MachiningSceneType;
    confidence?: number;
  };
}
```

TwinState는 Three.js가 읽는 유일한 제조 상태 객체로 만든다.

---

# 15. TwinStateMapper

MTConnect normalized data를 TwinState로 변환한다.

```text
MTConnect Normalized Event
       ↓
TwinStateMapper
       ↓
TwinState
```

예:

```ts
function toTwinState(machineState: MachineState): TwinState {
  return {
    timestamp: machineState.timestamp,

    execution: {
      state: machineState.execution
    },

    spindle: {
      rpm: machineState.spindleRpm,
      load: machineState.spindleLoad
    },

    axes: {
      x: machineState.x,
      y: machineState.y,
      z: machineState.z,
      b: machineState.b
    },

    tool: {
      number: machineState.toolNumber
    }
  };
}
```

---

# 16. Binding Layer

TwinState와 Three.js 모델 사이에 별도 Binding Layer를 둔다.

```text
TwinState
   │
   ├── SpindleBinding
   ├── AxisBinding
   ├── ToolBinding
   └── SceneBinding
```

예:

```ts
export class MachineTwinBinding {
  update(state: TwinState) {
    this.spindleBinding.update(state.spindle);
    this.axisBinding.update(state.axes);
    this.toolBinding.update(state.tool);
  }
}
```

---

# 17. Spindle RPM 시각화

실제 RPM을 그대로 Three.js 회전 속도로 적용할 필요는 없다.

```text
4821 RPM
```

을 화면에서 실제 4821 RPM으로 돌리면 사용자가 회전을 관찰할 수 없고 browser rendering에도 의미가 없다.

따라서:

```text
Physical RPM
      ↓
Visual RPM Mapping
      ↓
Animation Speed
```

을 사용한다.

예:

```ts
function rpmToVisualAngularVelocity(rpm: number): number {
  const normalized = Math.min(rpm / 6000, 1);
  return normalized * 12;
}
```

중요:

UI 숫자는 실제 Observed 값 그대로 표시한다.

```text
Spindle
4,821 RPM

OBSERVED
```

3D 회전 속도는 visualization 용이다.

---

# 18. Axis Movement

MTConnect가 실제 X/Y/Z 위치를 제공하는 경우:

```text
X
Y
Z
```

값을 해당 Three.js Group 이동으로 변환한다.

하지만 v1에서는 모든 축을 완벽하게 구현하지 않아도 된다.

우선순위:

```text
P0
B-axis
Main spindle rotation
Tool change

P1
X/Z

P2
Y
C-axis
Sub-spindle
```

---

# 19. 공구 모델 정책

Blender 없이 다음 네 가지 공구만 먼저 만든다.

```text
TURNING_TOOL
DRILL
END_MILL
FACE_MILL
```

이 네 종류면 CNC machining을 설명하기에 충분하다.

---

# 20. ToolFactory

```ts
export type ToolType =
  | "TURNING_TOOL"
  | "DRILL"
  | "END_MILL"
  | "FACE_MILL"
  | "UNKNOWN";
```

Factory:

```ts
export function createTool(type: ToolType): THREE.Group {
  switch (type) {
    case "TURNING_TOOL":
      return createTurningTool();

    case "DRILL":
      return createDrill();

    case "END_MILL":
      return createEndMill();

    case "FACE_MILL":
      return createFaceMill();

    default:
      return createUnknownTool();
  }
}
```

---

# 21. Turning Tool

구성:

```text
Holder
━━━━━━━━━━━━━━━━━━◇
                  Insert
```

Three.js:

```text
Holder
→ BoxGeometry

Insert
→ 간단한 triangular / diamond geometry
```

선삭 공구는 Primitive만으로도 비교적 현실적으로 표현 가능하다.

---

# 22. Drill

v1:

```text
Cylinder + Cone
```

구조:

```text
Drill
├── Shank
├── CuttingBody
└── Tip
```

나선 flute는 처음에는 생략한다.

v2에서만 필요하면 procedural helix를 추가한다.

---

# 23. End Mill

v1:

```text
EndMill
├── Shank
├── CuttingBody
└── CuttingEdge markers
```

외형:

```text
      ││
      ││
     ║║
     ║║
```

실제 나선 flute를 완벽히 표현하는 것이 목표가 아니다.

---

# 24. Face Mill

구조:

```text
FaceMill
├── CutterBody
├── Insert01
├── Insert02
├── Insert03
└── Insert04
```

CylinderGeometry + 작은 insert mesh 여러 개로 표현 가능하다.

---

# 25. ToolMount 반드시 분리

기계 모델 안에 공구를 고정시키지 않는다.

```text
MillingHead
    └── ToolSpindle
        └── ToolMount
```

ToolMount 아래의 ActiveTool만 동적으로 교체한다.

```text
T01
↓
TurningTool

T03
↓
EndMill

T08
↓
Drill
```

---

# 26. Tool Registry

MTConnect Tool Number와 실제/추정 공구 종류를 별도로 관리한다.

```ts
interface ToolRegistryEntry {
  machineId: string;
  toolNumber: string;

  toolType: ToolType | "UNKNOWN";

  identificationSource:
    | "OBSERVED"
    | "DOCUMENTED"
    | "INFERRED"
    | "MANUAL";

  modelAsset?: string;
}
```

예:

```text
T03

Tool Type:
END_MILL

Identification:
INFERRED
```

확실하지 않은 것을 실제 공구라고 표현하면 안 된다.

---

# 27. Tool Number가 변경되면 모델 교체

```text
TwinState

Tool T03
    ↓
ToolBinding
    ↓
ToolRegistry
    ↓
createEndMill()
    ↓
ToolMount.add()
```

예:

```ts
toolBinding.setActiveTool("T03");
```

---

# 28. 향후 GLB 교체를 고려한 인터페이스

Three.js procedural model과 GLB model이 동일 인터페이스를 사용하도록 한다.

예:

```ts
export interface ToolModelProvider {
  loadTool(tool: ToolRegistryEntry): Promise<THREE.Object3D>;
}
```

초기:

```text
ProceduralToolModelProvider
```

나중:

```text
GLBToolModelProvider
```

로 교체.

그러면 상위 코드 수정이 거의 필요 없다.

---

# 29. Machine Model도 동일하게 교체 가능하게 만든다

```ts
export interface MachineModelProvider {
  loadMachine(machineId: string): Promise<MachineTwinModel>;
}
```

v1:

```text
ProceduralMazakProvider
```

v2:

```text
GLBMazakProvider
```

---

# 30. MachineTwinModel 표준 노드

모델이 procedural이든 GLB든 다음 reference를 제공하게 만든다.

```ts
export interface MachineTwinModel {
  root: THREE.Object3D;

  nodes: {
    mainChuck?: THREE.Object3D;
    mainSpindle?: THREE.Object3D;
    subChuck?: THREE.Object3D;
    bAxisPivot?: THREE.Object3D;
    millingHead?: THREE.Object3D;
    toolMount?: THREE.Object3D;
    workpieceMount?: THREE.Object3D;
  };
}
```

이렇게 하면 모델 생성 방법과 데이터 Binding을 분리할 수 있다.

---

# 31. Replay와 3D 연결

현재 ForgeSync Replay 구조가 있다면 ReplayClock을 source of truth로 유지한다.

```text
ReplayClock
    ↓
Telemetry Snapshot
    ↓
MachineState
    ↓
TwinState
    ↓
3D Binding
```

Three.js가 자체적으로 시간을 진행하게 하지 않는다.

---

# 32. MachiningScene과 3D 연결

향후 Scene inference를 추가하면 3D animation 의미를 더 강화할 수 있다.

```text
IDLE
PROGRAM_START
SPINDLE_START
APPROACH
CUTTING
RETRACT
TOOL_CHANGE
PROGRAM_END
```

TwinState:

```ts
scene: {
  type: "CUTTING",
  confidence: 0.91
}
```

3D에서는 scene을 과도하게 가짜 animation으로 만들지 않는다.

예:

```text
CUTTING
→ 실제 spindle/axis 값 표시
→ Cutting indicator 표시

TOOL_CHANGE
→ Tool swap UI

IDLE
→ 움직임 없음
```

---

# 33. 관측된 값과 시각 효과 구분

매우 중요하다.

화면의 모든 데이터에는 가능하면 provenance를 붙인다.

예:

```text
Spindle RPM
4,821
OBSERVED
```

```text
Machining Scene
CUTTING
INFERRED
Confidence 0.91
```

```text
Workpiece Geometry
SIMULATED
```

```text
Tool Wear
182 μm
REFERENCE MODEL
```

---

# 34. PHM과 Tool 3D 연결

PHM 2010은 Mazak01과 같은 실제 공구의 데이터가 아니다.

따라서 다음과 같이 표현하지 않는다.

```text
Mazak T03 실제 Wear = 182 μm
```

대신:

```text
Tool Health Demonstration

Reference Model:
PHM 2010

Estimated Wear:
182 μm

REFERENCE / ESTIMATED
```

처럼 명확히 분리한다.

---

# 35. WearZone 구조

추후 PHM 기능을 위해 procedural Tool에 다음 node를 만들어둘 수 있다.

```text
Tool
├── Body
├── CuttingEdge
└── WearZone
```

기본:

```text
WearZone.visible = false
```

Health UI 사용 시:

```text
WearZone.visible = true
```

실제 geometry를 깎지 않는다.

---

# 36. 3D UI Layout 추천

```text
┌──────────────────────────────────────────────┐
│ ForgeSync                                    │
├────────────────────────────┬─────────────────┤
│                            │ Machine State   │
│                            │                 │
│        3D MACHINE          │ ACTIVE          │
│                            │ RPM 4,821       │
│                            │ Load 58%        │
│                            │ Tool T03        │
│                            │ B 37.2°         │
│                            │                 │
├────────────────────────────┴─────────────────┤
│ Timeline                                     │
│                                              │
│ IDLE → START → CUTTING → TOOL CHANGE → ...  │
└──────────────────────────────────────────────┘
```

---

# 37. 3D에서 꼭 필요한 Interaction

v1:

```text
Orbit
Zoom
Reset Camera
Play/Pause
Replay Speed
Timeline Scrub
```

충분하다.

게임처럼 걸어다니는 WASD navigation은 필요 없다.

---

# 38. Component Click

v2에서 고려:

```text
Chuck 클릭
→ Spindle RPM / Load

Head 클릭
→ B Axis / XYZ

Tool 클릭
→ Tool Number / Health
```

이를 통해 3D가 제조 데이터 navigation UI 역할까지 할 수 있다.

---

# 39. 구현 순서 수정

## Step 01 — 기존 MTConnect 기능 Freeze

확인:

```text
Parser
Normalization
API
Replay
Dashboard
```

기존 테스트가 깨지지 않는지 확인한다.

---

## Step 02 — TwinState 추가

다음 필드부터 시작:

```text
execution
rpm
load
x
y
z
b
toolNumber
```

---

## Step 03 — TwinStateMapper

기존 MachineState를 TwinState로 변환한다.

---

## Step 04 — Procedural CNC Skeleton

Three.js로:

```text
Enclosure
MainChuck
Workpiece
MillingHead
BAxisPivot
ToolMount
```

만 만든다.

---

## Step 05 — MachineTwinModel interface

데이터 Binding에서 Three.js 생성 방식이 보이지 않도록 추상화한다.

---

## Step 06 — Spindle Animation

```text
RPM
↓
visual rotation
```

구현.

---

## Step 07 — B Axis Binding

```text
B angle
↓
BAxisPivot
```

구현.

---

## Step 08 — ToolFactory

```text
Turning
Drill
EndMill
FaceMill
```

4종 추가.

---

## Step 09 — Tool Registry

Tool Number와 Tool Type 매핑 추가.

확실하지 않은 경우 `UNKNOWN` 유지.

---

## Step 10 — Tool Change Visualization

Tool Number 변화 시 ToolMount 모델 교체.

---

## Step 11 — Replay Synchronization

Timeline을 움직이면:

```text
Telemetry
↓
TwinState
↓
3D
```

가 동일 timestamp로 이동하는지 검증한다.

---

## Step 12 — XYZ Axis Visualization

실제 데이터 활용 가능성과 좌표계를 확인한 후 순차 구현.

---

## Step 13 — MachiningScene UI

Scene inference 구현 이후:

```text
CUTTING
TOOL_CHANGE
IDLE
```

정도의 label을 3D와 연결.

---

## Step 14 — PHM Tool Health Overlay

Tool 3D 위에:

```text
Health
Wear
RUL
```

정보를 overlay.

---

## Step 15 — Model Upgrade

필요성이 생길 경우에만:

```text
Procedural
↓
GLB
```

교체.

---

# 40. 지금 당장 만들 최소 버전

최소 목표:

```text
Simplified CNC

Main Chuck
Milling Head
Tool
Workpiece
```

동작:

```text
RPM → Chuck 회전

B Axis → Milling Head 회전

Tool Number → Tool 교체

Replay → 시간 동기화
```

이 네 기능이면 ForgeSync 3D v1의 목적을 달성한다.

---

# 41. 완료 조건

3D v1은 다음 조건을 만족하면 DONE이다.

```text
[ ] Blender 없이 실행 가능
[ ] Three.js 기본 Geometry로 CNC가 표시됨
[ ] Machine Model과 Data Binding이 분리됨
[ ] TwinState가 Three.js의 유일한 제조 상태 입력임
[ ] NIST spindle RPM이 3D 회전에 반영됨
[ ] B-axis 데이터가 Head orientation에 반영됨
[ ] Tool Number 변경이 Tool Model 변경에 반영됨
[ ] Replay timeline과 3D가 동일 timestamp로 동작함
[ ] Workpiece가 simulated geometry임을 표시함
[ ] Observed / Inferred / Simulated provenance를 구분함
[ ] PHM 데이터가 Mazak의 실제 측정값처럼 표시되지 않음
```

---

# 42. 포트폴리오에서 설명하는 방법

면접에서:

> 왜 정교한 OEM CAD를 사용하지 않았나요?

답변:

```text
ForgeSync의 목적은 정적인 CNC 3D 모델을 보여주는 것이 아니라,
MTConnect에서 수집된 실제 제조 데이터를 물리적인 기계 동작으로
해석하는 것이었습니다.

따라서 초기 버전에서는 Three.js primitive geometry를 이용해
기계의 기능적 component hierarchy를 직접 구성했습니다.

Spindle, Chuck, B-axis, ToolMount 등을 독립적인 node로 설계했고,
MTConnect 데이터를 TwinState로 표준화한 뒤 Binding Layer를 통해
3D 모델과 연결했습니다.

덕분에 향후 OEM CAD 또는 GLB 모델을 사용하더라도
데이터 처리 및 Digital Twin 로직을 수정하지 않고
3D asset만 교체할 수 있도록 설계했습니다.
```

이 설명이 ForgeSync의 architecture 의도를 잘 보여준다.

---

# 43. 3D 관련 새로운 핵심 Architecture

최종적으로 다음 흐름을 목표로 한다.

```text
NIST MTConnect
       │
       ▼
Telemetry Parser
       │
       ▼
Normalized MachineState
       │
       ▼
MachiningRun / MachiningScene
       │
       ▼
TwinState
       │
       ▼
MachineTwinBinding
       │
       ▼
Three.js Model
       │
       ├── Procedural Model (v1)
       │
       └── GLB Model (future)
```

---

# 44. PHM까지 포함한 전체 구조

```text
                 ┌─────────────────────┐
                 │ NIST Mazak          │
                 │ MTConnect           │
                 └──────────┬──────────┘
                            │
                            ▼
                      MachineState
                            │
                            ▼
                     MachiningRun
                            │
                            ▼
                    MachiningScene
                            │
                            ▼
                       TwinState
                            │
             ┌──────────────┴──────────────┐
             │                             │
             ▼                             ▼
       Three.js Twin                 Process Analysis
             │                             │
             │                             ▼
             │                       AnomalyAssessment
             │
             │
             │       ┌───────────────────────┐
             │       │ PHM 2010              │
             │       └───────────┬───────────┘
             │                   │
             │                   ▼
             │              Wear / RUL Model
             │                   │
             └──────────────┬────┘
                            ▼
                     HealthAssessment
```

---

# 45. 최종 제품 메시지

기존:

```text
3D CNC를 보여주는 Digital Twin
```

보다 다음 메시지를 목표로 한다.

```text
ForgeSync는 실제 MTConnect 제조 데이터를
기계의 물리적 상태와 가공 장면으로 해석하고,
이를 Three.js 기반 Functional Digital Twin에서
시간 동기화하여 재생한다.

3D 모델은 데이터와 분리된 교체 가능한 표현 계층이며,
Tool Health 분석은 별도의 PHM reference model을 통해 확장된다.
```

---

# 46. 가장 중요한 개발 원칙

```text
정교한 모델을 먼저 만들지 않는다.

움직이는 의미 있는 모델을 먼저 만든다.
```

그리고:

```text
Visual Fidelity
      <
Functional Fidelity
```

ForgeSync v1에서는 이 원칙을 유지한다.

외형이 100% Mazak과 동일하지 않아도 된다.

하지만:

```text
Spindle 데이터가 Spindle을 움직이고,
B-axis 데이터가 B-axis를 움직이고,
Tool 데이터가 Tool을 바꾸고,
Replay 시간이 모든 상태를 동기화한다.
```

는 반드시 지켜야 한다.

이것이 ForgeSync 3D Digital Twin의 핵심이다.
