# ADR-020: Canonical 변환 전 Raw Source 불변 보존

- 상태: Accepted
- 날짜: 2026-09-01
- 관련 PRD: 4, 6, 19~25, 97~106, 120, 122

## Context

ForgeSync는 NIST 제조 데이터의 의미와 provenance를 보존해야 한다. 파서와 mapping은 프로젝트 진행 중 변경될 수 있으며, 잘못된 초기 가정으로 원천을 덮어쓰면 수정된 규칙으로 재처리하거나 결과를 감사할 수 없다.

## Decision

외부 source byte를 `SourceArtifact`로 먼저 불변 저장하고 checksum과 수집 manifest를 기록한다. 파싱된 RawRecord, Canonical Observation, Twin Projection은 각각 별도 파생 계층으로 관리하며 이전 계층을 덮어쓰지 않는다.

Canonical Observation은 원본을 추적할 수 있도록 `rawRecordId`, `mappingVersion`, provenance를 가진다. 파싱 또는 mapping이 실패해도 원본 artifact는 보존한다. 수정된 parser/mapping을 적용할 때는 새 Processing Run을 만든다.

## Consequences

장점:

- parser/schema 변경 후 결정적으로 재처리할 수 있다.
- 데이터 의미와 출처에 관한 주장을 원본까지 추적할 수 있다.
- invalid/unknown 데이터도 조용히 유실되지 않는다.

비용:

- 원본과 파생 데이터의 저장 공간이 별도로 필요하다.
- manifest, checksum, retention, license 관리가 필요하다.
- Raw 저장 성공과 Canonical 처리 성공을 별도 상태로 운영해야 한다.

## Rejected alternatives

- 수집 즉시 canonical 형태만 저장: 초기 mapping 오류를 복구할 근거가 사라진다.
- raw payload를 application log에만 남김: 완전성, 보존 기간, 접근 제어, 재처리를 보장할 수 없다.
- raw와 projection을 같은 레코드에서 갱신: 불변 원본과 가변 현재 상태의 lifecycle이 충돌한다.

## Verification

- Golden fixture ingest 전후 byte와 SHA-256이 같다.
- 의도적으로 parser를 실패시킨 뒤에도 SourceArtifact를 조회할 수 있다.
- mappingVersion을 바꿔 재처리해도 기존 artifact와 이전 processing 결과는 변경되지 않는다.
