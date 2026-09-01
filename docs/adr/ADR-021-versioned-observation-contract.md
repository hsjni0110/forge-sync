# ADR-021: Versioned Canonical Observation Contract

- 상태: Accepted
- 날짜: 2026-09-01
- 관련 PRD: 19~25, 120

## Context

Edge와 Factory API는 서로 다른 언어로 구현되지만 SAMPLE, EVENT, CONDITION의 의미와 원천
provenance를 동일하게 해석해야 한다. 선택한 NIST profile에는 수치나 Event 값 대신
`UNAVAILABLE`을 보고한 레코드가 있으며, Canonical 생성 시점에는 아직 Replay identity가 없다.
복제 DTO나 느슨한 역직렬화는 category 변경, 시각 혼합, provenance 유실을 조기에 발견하지 못한다.

## Decision

JSON Schema Draft 2020-12의 `ObservationEnvelope` v1.0.0을 단일 public contract로 사용한다. Edge
producer와 Factory API consumer는 동일 schema와 shared fixtures를 각 런타임의 validator로
검증한다. v1 consumer는 정확한 schema version만 허용하고 모든 object의 미정의 field를 거절한다.

SAMPLE, EVENT, CONDITION payload는 서로 바꿀 수 없는 variant다. SAMPLE과 EVENT의 원천 값이
`UNAVAILABLE`이면 category를 유지하고 availability만 기록하며 value와 unit을 만들지 않는다.
CONDITION의 `UNAVAILABLE`은 Condition level이며 Alarm이 아니다.

원천 관찰 시각과 선택적 Replay 정보를 분리한다. Replay 정보가 있으면 session, sequence,
publication time을 모두 요구하고, 아직 Replay하지 않은 Observation에는 그 값을 만들지 않는다.
Provenance는 NIST source set/artifact와 Raw Record/mapping version을 구조적으로 분리해 기록한다.

## Consequences

- producer drift, category-payload 불일치, 잘못된 단위와 불완전한 Replay identity가 경계에서 거절된다.
- 새 optional field도 기존 v1 consumer에는 호환되지 않으므로 새 schema version과 명시적 전환이 필요하다.
- 지원 metric과 Event vocabulary는 PRD의 v1 대표 목록으로 제한되며 나머지 DataItem은 Semantic
  Mapping에서 근거 없이 Canonical로 만들지 않는다.

## Rejected alternatives

- 알 수 없는 field 허용: 오타와 producer/consumer drift를 숨긴다.
- `UNAVAILABLE`을 CONDITION으로 변환하거나 버림: 원천 category 또는 Canonical 이력을 잃는다.
- Replay field를 개별 optional로 둠: 순서 identity가 부분적으로 존재하는 상태를 허용한다.
- 언어별 DTO를 독립 관리: 같은 이름의 field가 서로 다른 제약으로 진화할 수 있다.

## Verification

- 세 variant와 unavailable 경계 fixture를 Python과 Java validator가 모두 수락한다.
- identity, provenance, timezone, payload, unit, Replay group, schema version 또는 field 정책을 위반한
  shared fixture를 두 validator가 모두 거절한다.
- Edge model로 생성한 SAMPLE fixture를 Factory API가 변환 없이 수락한다.
