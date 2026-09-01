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
| V-005 | PHM2010은 7개 sensor channel을 제공한다 | TO_VERIFY | 공식 설명과 실제 archive profile을 분리해 확인 | 22 | - |
| V-006 | PHM2010 archive를 현재 재현 가능하게 받을 수 있다 | TO_VERIFY | URI, retrievedAt, checksum, integrity 기록 | 22 | - |
| V-007 | PHM2010 사용/재배포 조건이 프로젝트 방식과 호환된다 | TO_VERIFY | 공식 조건 확인 | 22 | - |
| V-008 | NASA Milling을 fallback으로 재현 가능하게 받을 수 있다 | TO_VERIFY | PHM 차단 시 동일 artifact/profile 절차 수행 | 22 | - |
| V-009 | Generic CNC GLB asset의 사용/재배포 조건이 허용된다 | TO_VERIFY | asset manifest와 license 원문 참조 | 13 | - |
| V-010 | Factory layout은 실제 NIST 공장 layout이다 | FALSE_NOT_CLAIMED | `SIMULATED_LAYOUT`로만 표시 | 14 | 2026-09-01 |
| V-011 | 3D spindle 회전은 물리적으로 정확한 속도다 | FALSE_NOT_CLAIMED | RPM에 반응하는 정규화된 visual cue로만 정의 | 15 | 2026-09-01 |
| V-012 | PHM/NASA model은 NIST Mazak01의 실제 RUL을 예측한다 | FALSE_NOT_CLAIMED | 별도 advisory channel과 field-level provenance 사용 | 24 | 2026-09-01 |
| V-013 | MQTT에서 global exactly-once를 보장한다 | FALSE_NOT_CLAIMED | QoS1 + 선언된 DB 경계 내 effectively-once만 주장 | 07 | 2026-09-01 |
| V-014 | 5/20 machine scene이 목표 성능을 만족한다 | TO_VERIFY | browser/environment별 FPS, frame time, heap 측정 | 26 | - |
| V-015 | WebGL/asset 실패에도 2D 핵심 기능이 동작한다 | TO_VERIFY | E2E-04 결과 링크 | 13/25 | - |
| V-016 | 실제 CNC/PLC/Safety PLC를 제어한다 | FALSE_NOT_CLAIMED | Virtual Controller만 허용 | 18 | 2026-09-01 |
| V-017 | 고정 Mazak01 raw를 component 혼동과 근거 없는 의미 추론 없이 Observation v2로 매핑할 수 있다 | VERIFIED | Mapping `2.0.0`, raw `6eec7a...ef2cf`; 115,991 parsed 중 52,996 mapped 및 v2 schema 검증, 62,973 unsupported, 22 unknown, invalid value 0. [재현 보고서](./data/mappings/nist-mazak01-observation-v2/mapping-report.md) | 04 | 2026-09-01 |
| V-018 | 동일한 Canonical Processing Run과 정렬 규칙은 같은 Replay event sequence/hash를 만든다 | VERIFIED | Observation output `aee293...9b17f`의 52,996건을 두 번 계획해 sequence hash `c1e806...d9bb6` 재현. [Replay 계획 보고서](./data/replay/nist-mazak01-20161005-observation-v2/replay-plan.md), 고정 Clock/실패 재시도 unit tests | 05 | 2026-09-01 |
| V-019 | Observation v2 MQTT QoS1 전달은 at-least-once이며 broker PUBACK 이후에만 replay publisher가 성공한다 | VERIFIED | Pinned Mosquitto `2.0.22@sha256:212f89...2b3c`; `./scripts/verify-mqtt`에서 Edge MQTT 5 payload/properties 전달, API duplicate 2회 전달, invalid version reject telemetry 검증. [MQTT 전달 계약](../contracts/mqtt/observation-delivery.md) | 06 | 2026-09-01 |

## 갱신 규칙

- 근거에는 가능한 경우 artifact hash, source URI, 확인 날짜, tool/version, test/report 경로를 포함한다.
- 웹페이지 설명만 확인한 것과 실제 archive/profile을 검증한 것을 같은 항목으로 합치지 않는다.
- performance 수치는 hardware/browser/build/scene 조건 없이 기록하지 않는다.
- 근거가 오래되거나 외부 상태가 바뀔 수 있으면 `Last checked`를 갱신하고 필요 시 `TO_VERIFY`로 되돌린다.
- 제품 README와 UI claim은 이 Ledger의 상태보다 강하게 표현할 수 없다.
