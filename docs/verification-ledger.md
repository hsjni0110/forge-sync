# ForgeSync Verification Ledger

외부 사실, 데이터/asset 상태, 성능 수치와 제품 주장을 근거와 함께 추적한다. `VERIFIED`는 재현 가능한 근거가 있을 때만 사용한다.

## 상태 정의

| 상태 | 의미 |
|---|---|
| VERIFIED | 근거 위치와 재현 절차가 있으며 현재 범위에서 확인됨 |
| TO_VERIFY | 주장 후보이나 아직 직접 확인하지 않음 |
| BLOCKED | 확인 시도와 차단 이유가 기록됨 |
| FALSE_NOT_CLAIMED | 사실이 아니거나 제품이 주장하지 않기로 명시함 |
| OPTIONAL | MVP 필수 근거가 아님 |

## Ledger

| ID | Claim | Status | Evidence / Action | Roadmap Reference | Last checked |
|---|---|---|---|---|---|
| V-001 | NIST Mazak01 metadata는 선택한 Devices.xml에 존재한다 | VERIFIED | Pinned Devices.xml `bb54e4...f68166`, [source profile](./data/profiles/nist-mazak01-20161005/profile.md)에서 65 DataItems 확인 | 01 | 2026-09-01 |
| V-002 | 선택한 NIST raw grammar를 parser가 처리할 수 있다 | VERIFIED | Pinned raw `6eec7a...ef2cf`, 115,991 records 중 invalid 0; unknown 22는 보존·보고 | 01 | 2026-09-01 |
| V-003 | 저장된 NIST raw byte는 수집 byte와 동일하다 | VERIFIED | 4,609,711 bytes와 SHA-256 lock 검증, byte-preservation/partial-download tests | 01 | 2026-09-01 |
| V-004 | NIST source/fixture의 사용 및 재배포 조건이 프로젝트 방식과 호환된다 | VERIFIED | [NIST source notice](./data/nist-source-notice.md); actual raw는 Git 제외, attribution 유지 | 01 | 2026-09-01 |
| V-005 | PHM2010은 7개 sensor channel을 제공한다 | TO_VERIFY | 공식 설명과 실제 archive profile을 분리해 확인 | 26 | - |
| V-006 | PHM2010 archive를 현재 재현 가능하게 받을 수 있다 | TO_VERIFY | URI, retrievedAt, checksum, integrity 기록 | 26 | - |
| V-007 | PHM2010 사용/재배포 조건이 프로젝트 방식과 호환된다 | TO_VERIFY | 공식 조건 확인 | 26 | - |
| V-008 | NASA Milling을 fallback으로 재현 가능하게 받을 수 있다 | TO_VERIFY | PHM 차단 시 동일 artifact/profile 절차 수행 | 26 | - |
| V-009 | Generic CNC GLB asset의 사용/재배포 조건이 허용된다 | TO_VERIFY | 외부 GLB는 아직 선택/커밋하지 않음. v1 manifest는 procedural primitive의 `NOASSERTION`/`TO_VERIFY`를 명시 | 13 | 2026-09-03 |
| V-010 | Factory layout은 실제 NIST 공장 layout이다 | FALSE_NOT_CLAIMED | Mazak01 scene binding과 `/factory`에서 `SIMULATED_LAYOUT`로만 표시 | 14 | 2026-09-03 |
| V-011 | 3D spindle 회전은 물리적으로 정확한 속도다 | FALSE_NOT_CLAIMED | `clamp(rpm / 500, 0, 4 rad/s)`인 비물리적 visual cue로 정의하고 `/factory`에 명시. 정규화 unit test와 실제 NIST RPM E2E로 변화만 검증 | 15 | 2026-09-03 |
| V-012 | PHM/NASA model은 NIST Mazak01의 실제 RUL을 예측한다 | FALSE_NOT_CLAIMED | 별도 advisory channel과 field-level provenance 사용 | 30/32 | 2026-09-01 |
| V-013 | MQTT에서 global exactly-once를 보장한다 | FALSE_NOT_CLAIMED | QoS1 + 선언된 DB 경계 내 effectively-once만 주장 | 07 | 2026-09-01 |
| V-014 | 5/20 machine scene이 목표 성능을 만족한다 | TO_VERIFY | browser/environment별 FPS, frame time, heap 측정 | 39 | - |
| V-015 | WebGL/asset 실패에도 2D 핵심 기능이 동작한다 | VERIFIED | GLB 크기/SHA-256 검증과 404/invalid fallback, bundle/WebGL2 capability failure, Canvas fallback 오탐 방지 component tests. 실제 PostgreSQL/Mosquitto/API/Web/Chromium `./scripts/verify-e2e`에서 첫 scene frame과 context 차단 시 2D snapshot 유지를 확인 | 13/38 | 2026-09-03 |
| V-016 | 실제 CNC/PLC/Safety PLC를 제어한다 | FALSE_NOT_CLAIMED | Virtual Controller만 허용 | 34 | 2026-09-01 |
| V-017 | 고정 Mazak01 raw를 component 혼동과 근거 없는 의미 추론 없이 Observation v2로 매핑할 수 있다 | VERIFIED | Mapping `2.0.0`, raw `6eec7a...ef2cf`; 115,991 parsed 중 52,996 mapped 및 v2 schema 검증, 62,973 unsupported, 22 unknown, invalid value 0. [재현 보고서](./data/mappings/nist-mazak01-observation-v2/mapping-report.md) | 04 | 2026-09-01 |
| V-018 | 동일한 Canonical Processing Run과 정렬 규칙은 같은 Replay event sequence/hash를 만든다 | VERIFIED | Observation output `aee293...9b17f`의 52,996건을 두 번 계획해 sequence hash `c1e806...d9bb6` 재현. [Replay 계획 보고서](./data/replay/nist-mazak01-20161005-observation-v2/replay-plan.md), 고정 Clock/실패 재시도 unit tests | 05 | 2026-09-01 |
| V-019 | Observation v2 MQTT QoS1 전달은 at-least-once이며 broker PUBACK 이후에만 replay publisher가 성공한다 | VERIFIED | Pinned Mosquitto `2.0.22@sha256:212f89...2b3c`; `./scripts/verify-mqtt`에서 Edge MQTT 5 payload/properties 전달, API duplicate 2회 전달, invalid version reject telemetry 검증. [MQTT 전달 계약](../contracts/mqtt/observation-delivery.md) | 06 | 2026-09-01 |
| V-020 | Inbox와 Canonical Observation은 선언된 PostgreSQL transaction 경계 안에서 effectively-once로 처리된다 | VERIFIED | Pinned PostgreSQL 17 + TimescaleDB `2.29.2@sha256:bc8527...af2d`; `./scripts/verify-database`에서 동일 Inbox identity 100개 동시 주입 시 acceptance/history 1건과 history 실패 시 Inbox rollback 확인. [ADR-025](./adr/ADR-025-postgres-ingestion-transaction.md) | 07 | 2026-09-02 |
| V-021 | 늦거나 순서가 낮은 Observation은 history를 보존하면서 Latest Observation과 TwinVersion을 rollback하지 않는다 | VERIFIED | Pure ordering unit tests와 실제 PostgreSQL concurrent integration test에서 same-session sequence, cross-session source time, source key tie-break, DataItem 분리, projection 실패 전체 rollback 확인. [ADR-026](./adr/ADR-026-latest-observation-ordering.md) | 08 | 2026-09-02 |
| V-022 | Equipment State는 current Latest Observation만으로 결정되고 freshness는 historical source time이 아닌 projected wall clock으로 계산된다 | VERIFIED | 고정 시각 unit tests에서 2초/10초 경계와 unavailable/Condition 집계를 검증하고, `./scripts/verify-database`에서 state/version 원자성, out-of-order 무변경, state 저장 실패 전체 rollback 확인. [ADR-027](./adr/ADR-027-equipment-state-and-freshness.md) | 09 | 2026-09-02 |
| V-023 | Versioned Operational Twin REST snapshot은 한 읽기 일관성 경계에서 TwinVersion, freshness, P0 값과 field-level provenance를 제공한다 | VERIFIED | Twin v1 schema/golden/API tests와 `./scripts/verify-database`에서 실제 PostgreSQL Projection의 state/version 및 Canonical provenance 보존 확인. 복수 spindle은 primary를 추측하지 않고 모두 반환. [ADR-028](./adr/ADR-028-versioned-operational-twin-snapshot.md), [Twin contract](../contracts/twin/README.md) | 10 | 2026-09-02 |
| V-024 | 2D Machine Detail은 실제 replay 업데이트를 표시하고 WebSocket 단절 뒤 STALE을 알린 후 권위 REST snapshot version/value로 수렴한다 | VERIFIED | Canonical Observation output `aee293...9b17f`에서 고정한 세 record와 실제 PostgreSQL/Mosquitto/API/Web/Chromium을 `./scripts/verify-e2e`로 검증. P0 component tests와 [ADR-030](./adr/ADR-030-accessible-machine-detail-client.md) 참조 | 11/12 | 2026-09-02 |
| V-025 | 3D Mazak01과 오른쪽 Machine Detail은 같은 live Twin identity와 version을 표현한다 | VERIFIED | 순수 Twin→MachineVisualState mapping, renderer import boundary, v4→v5 shared-session component test와 실제 PostgreSQL/Mosquitto/API/Web/Chromium `./scripts/verify-e2e`에서 선택 label/panel의 Mazak01 Twin v6 일치 확인 | 14 | 2026-09-03 |
| V-026 | 3D visual spindle은 ACTIVE와 양수 RPM에서만 동작하고 STOPPED/STALE/reduced-motion에서 정지한다 | VERIFIED | 고정 Canonical lines 439/441/442/462/587/1206과 checksum `aee293...9b17f`를 실제 PostgreSQL/Mosquitto/API/Web/Chromium `./scripts/verify-e2e`로 전달해 v2~v6 활성/정지/STALE/복구를 검증. visual policy와 접근성 component tests 포함 | 15 | 2026-09-03 |
| V-027 | Step 15 관찰용 fixture는 실제 ReplayClock이 아니며 여러 실행/RPM 변화를 제공한다 | VERIFIED | Canonical checksum `aee293...9b17f`, source range `2016-10-05T09:18:27.292Z`—`10:18:22.999Z`에서 path execution/spindle RPM 337건 선택과 1.5초 관찰용 간격을 unit test로 고정. Step 17 `run-local`에서는 사용하지 않음 | 15 | 2026-09-04 |
| V-028 | Step 01~15의 NIST Operational Twin과 2D/3D visual 의미는 Replay Cursor 계약 추가 후에도 유지된다 | VERIFIED | Regression manifest `843849...471a`가 Twin `b7eda5...c83`, patch `9796a4...f674`, E2E scenario `bc993c...3863`과 Canonical output `aee293...9b17f`를 고정한다. `./scripts/verify`, `./scripts/verify-database`, `./scripts/verify-e2e` 통과. [ADR-032](./adr/ADR-032-observed-process-analytics-boundary.md) | 16/17 | 2026-09-04 |
| V-029 | Replay pause/resume/speed/seek는 같은 session revision과 권위 Replay Cursor를 사용하며 2D/3D가 독립 business clock을 만들지 않는다 | VERIFIED | 고정 Clock Edge tests, Factory API control/contract tests, PostgreSQL history·active-session fence integration tests, Web optimistic rollback·time-label·animation-freeze tests와 실제 PostgreSQL/Mosquitto/API/Web/Chromium `./scripts/verify-e2e`로 snapshot version 수렴을 확인. `./scripts/verify`, `./scripts/verify-database`, `./scripts/verify-mqtt`, `./scripts/verify-e2e` 통과. [ADR-033](./adr/ADR-033-replay-control-cursor-and-seek.md), [Replay contract](../contracts/replay/README.md) | 17 | 2026-09-04 |
| V-030 | 같은 Canonical Observation 범위와 segmentation rule은 추적 가능한 동일 Machining Run 경계와 hash를 만들며 late input은 이전 결과를 덮어쓰지 않는다 | VERIFIED | Pure policy/application tests, producer/independent-consumer contract tests, fixture `a0ebee...43192`, 실제 PostgreSQL의 멱등·새 version·rollback integration tests. `./scripts/verify`, `./scripts/verify-database`, `./scripts/verify-e2e` 통과. [ADR-034](./adr/ADR-034-deterministic-machining-run-segmentation.md), [Machining Run contract](../contracts/process-analytics/README.md) | 18 | 2026-09-04 |
| V-031 | 완료 Machining Run의 Cycle Feature `1.0.0`은 unknown 구간을 채우지 않는 시간 가중 계산과 per-channel provenance를 결정적으로 보존한다 | VERIFIED | 10초 golden/coverage/unit mismatch pure tests, producer와 독립 Python consumer가 schema `70f839...083f` 및 fixture `1c0f09...b107` 검증, 실제 PostgreSQL의 atomic query/reuse/late-version/rollback integration tests. `./scripts/verify`, `./scripts/verify-database`, `./scripts/verify-e2e` 통과. [ADR-035](./adr/ADR-035-versioned-cycle-feature-projection.md), [Cycle Feature contract](../contracts/process-analytics/README.md) | 19 | 2026-09-04 |
| V-032 | Anomaly Assessment `1.0.0`은 동일 설비·프로그램·Cycle Feature version의 엄격히 이전 run만 사용해 median/IQR 차이를 설명하며 높은 score를 Fault, Alarm, Advisory 또는 command로 승격하지 않는다 | VERIFIED | Pure baseline/assessment와 application ordering/reuse tests, producer와 독립 Python consumer가 schema `e8f832...58174` 및 fixture `57bccf...8fbc` 검증, 실제 PostgreSQL의 V007 atomic query/reuse/source-preservation/rollback integration tests. `./scripts/verify`, `FORGESYNC_DATABASE_PORT=15433 ./scripts/verify-database`, `./scripts/verify-e2e`, `git diff --check` 통과. [ADR-036](./adr/ADR-036-explainable-cycle-baseline-and-anomaly-assessment.md), [Anomaly Assessment contract](../contracts/process-analytics/README.md) | 20 | 2026-09-05 |

## Step 21 공정 UI 검증 (2026-09-05)

- **V-033 — VERIFIED**: Step 21의 실제 seek 이후 2D/3D/공정 분석이 같은 권위 Cursor/TwinVersion에
  수렴한다. `./scripts/verify-e2e`의 Chromium 시나리오 4개가 통과했으며, 확장 Replay 시나리오는
  분석 상세, 키보드 근거 탐색, marker seek를 검증한다. 스크립트는 broker drop counter 불변과
  활성 세션의 0번부터 최종 Twin cursor까지 Canonical history 연속성도 확인한다.
- RED: cross-DataItem cursor 42→41 회귀, 분석 POST preflight 403, 사람이 읽을 수 있는
  신뢰도·차이 설명 누락을 각각 실제 실패로 확인했다.
- GREEN: 해당 PostgreSQL 회귀, CORS allowlist, 공정 설명 테스트를 수정 후 통과했다.
  `./scripts/verify`의 Python/Java/TypeScript 품질 게이트와 프런트엔드 104개 테스트도 통과했다.
  Node v23.11.0은 저장소의 선언 범위 `>=22.13 <23` 밖이라는 engine 경고가 남는다.
- 브라우저의 커서 비교나 실패 assertion을 약화하지 않았다. worker 세대 경합 회귀와 유한
  60,000건/128 MiB broker queue 경계는 [ADR-038](./adr/ADR-038-seek-delivery-backpressure.md)에
  기록했다. PUBACK를 DB acceptance 또는 exactly-once로 주장하지 않는다.

## 갱신 규칙

- 근거에는 가능한 경우 artifact hash, source URI, 확인 날짜, tool/version, test/report 경로를 포함한다.
- 웹페이지 설명만 확인한 것과 실제 archive/profile을 검증한 것을 같은 항목으로 합치지 않는다.
- performance 수치는 hardware/browser/build/scene 조건 없이 기록하지 않는다.
- 근거가 오래되거나 외부 상태가 바뀔 수 있으면 `Last checked`를 갱신하고 필요 시 `TO_VERIFY`로 되돌린다.
- 제품 README와 UI claim은 이 Ledger의 상태보다 강하게 표현할 수 없다.
