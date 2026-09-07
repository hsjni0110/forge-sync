# ADR-022: Explicit Metadata-Checked Canonical Mapping

- 상태: Accepted
- 날짜: 2026-09-01
- 관련 PRD: 19~25, 120

## Context

고정된 Mazak01 raw artifact에는 이름이 같은 component와 같은 종류의 metric이 여러 개 있다.
이름이나 값의 모양으로 의미를 추측하면 서로 다른 component의 측정값을 합치거나 source에 없는
ordering 정보를 만들 수 있다.

## Decision

source set, machine, Devices artifact, raw artifact에 묶인 versioned JSON mapping table을 사용한다.
실행 전에 각 mapping entry의 component id, DataItem id, name, category, type, subtype, unit이 고정 Devices.xml
catalog와 정확히 일치하는지 검증한다. mapping table에 명시된 DataItem만 Canonical로 만들고,
catalog에는 있지만 mapping이 없는 항목과 catalog에 없는 항목을 각각 `UNSUPPORTED_DATA_ITEM`,
`UNKNOWN_DATA_ITEM`으로 보존·집계한다.

Observation v2의 component identity로 구분되는 RPM, feed, position, load, temperature와 대표 Event를
명시적으로 매핑한다. Catalog-known Condition은 source type과 category를 유지하며 Alarm으로 변환하지
않는다. 같은 component의 같은 target으로 충돌하는 `Cload`/`Sload` 중 `Cload`는 보류한다.
`PART_COUNT`는 telemetry Event이며 ProductionResult가 아니다.

`rawRecordId`와 `sourceEventKey`는 immutable artifact identity와 byte range를 결합한다. `eventId`는
mapping version과 `rawRecordId`를 입력으로 한 UUIDv5로 결정적으로 생성한다. source에 없는 sequence,
agent, replay 정보를 만들지 않는다. L2 output은 processing run별 새 디렉터리에 기록하고, 동일 run이
이미 있으면 checksum이 모두 같은 경우에만 재사용한다.

## Consequences

- mapping 변경은 version과 processing run을 바꾸며 기존 L0/L1/L2 결과를 덮어쓰지 않는다.
- Canonical coverage는 catalog-match 비율이 아니라 실제 명시 mapping 비율로 보고된다.
- 지원하지 않는 항목도 원본 byte locator와 함께 검토 가능하다.
- 같은 component/category/target channel을 둘 이상 선언하면 mapping 시작 전에 실패한다.
- subType만 다른 형제 DataItem, catalog가 단위를 선언하지 않는 SAMPLE, catalog 단위를 가진 EVENT의
  처리는 [ADR-049](./ADR-049-accumulated-time-and-operating-signal-mapping.md)가 이 결정을 확장한다.

## Rejected alternatives

- DataItem 이름이나 component path만으로 component를 추론: C와 C2처럼 이름이 같은 component가 있다.
- 모든 catalog-known record를 Canonical로 통과: 지원 vocabulary 밖 값을 canonical metric처럼 보이게 한다.
- 입력 순서를 source sequence로 기록: source가 제공하지 않은 ordering identity를 만든다.
- mapping 결과를 기존 raw/profile에 병합: 데이터 계층과 processing run provenance를 훼손한다.

## Verification

- golden fixture로 RPM, feed, execution, tool, program과 SAMPLE/EVENT/CONDITION category 보존을 검증한다.
- unknown, unsupported, invalid value와 metadata drift가 Canonical 결과로 만들어지지 않는지 검증한다.
- 모든 생성 Observation을 공용 Observation v2 JSON Schema로 검증한다.
- 같은 입력과 mapping version의 UUID, output bytes, manifest checksum이 재실행에서 동일한지 검증한다.
- 실제 pinned artifact의 결과는
  [mapping report](../data/mappings/nist-mazak01-observation-v2/mapping-report.md)에 기록한다.
