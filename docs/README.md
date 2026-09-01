# ForgeSync 개발 문서

이 디렉터리는 [ForgeSync PRD v1.4](./ForgeSync_PRD_v1.4_Spatial_Digital_Twin.md)를 실제 구현과 검증으로 전환하기 위한 실행 문서 모음이다. PRD가 제품의 **무엇과 왜**를 정의한다면, 아래 문서는 **어떤 경계로, 어떤 순서로, 무엇을 테스트하며 만들 것인지**를 정의한다.

## 문서 지도

| 문서 | 목적 |
|---|---|
| [개발 Step](./development/implementation-steps.md) | 테스트 가능한 최소 작업 단위와 순서, 완료 조건 |
| [엔지니어링 원칙](./architecture/engineering-principles.md) | Clean Architecture, DDD, 객체지향, 모듈 경계와 의존성 규칙 |
| [Raw 데이터 파이프라인](./data/raw-to-canonical-pipeline.md) | 원본 보존부터 Canonical Observation, Twin Projection까지의 데이터 계약 |
| [NIST Source Notice](./data/nist-source-notice.md) | 선택 원천, checksum, attribution과 사용 조건 |
| [Mazak01 Source Profile](./data/profiles/nist-mazak01-20161005/profile.md) | 실제 일별 raw와 Devices.xml에서 생성한 human-readable profile |
| [테스트 전략](./testing/test-strategy.md) | 계층별 테스트 범위, 품질 게이트, 실패 시나리오 |
| [코딩 및 리뷰 가이드](./development/coding-and-review-guide.md) | 메서드 분리, 네이밍, 복잡도 관리, 에이전트 리뷰/수정 절차 |
| [ADR-020 Raw Source Preservation](./adr/ADR-020-raw-source-preservation.md) | 원천 데이터를 변환 전에 불변 보존하기로 한 결정 |
| [Verification Ledger](./verification-ledger.md) | 주장, 근거, 검증 상태 추적 |

## 우선순위와 충돌 해결

1. 안전, 데이터 진실성, 출처 보존 규칙을 최우선으로 한다.
2. 제품 범위와 사용자 행동은 PRD를 따른다.
3. 구현 구조는 아키텍처 문서와 ADR을 따른다.
4. 작업 순서와 완료 기준은 개발 Step 문서를 따른다.
5. 충돌이나 새로운 중대한 결정은 임의로 숨기지 않고 ADR과 Verification Ledger에 남긴다.

`Step 01` 같은 Step 번호는 roadmap 문서에서 진행 순서를 표현하는 관리용 표기다. 코드, 설정, 데이터 계약, CLI, 파일 경로, runtime identity에는 사용하지 않는다. 구현 이름은 도메인 의미, 원천 identity, 관찰 기간과 version을 기준으로 한다.

## 모든 Step에 적용되는 공통 완료 조건

- 먼저 실패하는 테스트 또는 명시적인 검증 시나리오로 기대 동작을 고정한다.
- 정상 경로뿐 아니라 최소 1개의 경계값 또는 실패 경로를 테스트한다.
- 도메인 규칙은 UI, Controller, Framework Callback이 아니라 도메인 계층에 존재한다.
- 외부 시스템과 프레임워크는 Port/Adapter 뒤에 둔다.
- provenance, source time, ordering 정보를 변환 과정에서 유실하지 않는다.
- 관련 단위 테스트와 통합/계약 테스트가 통과한다.
- 사용자에게 보이는 동작은 인수 조건 또는 E2E 테스트로 연결한다.
- 새로 확인한 외부 사실이나 성능 수치는 Verification Ledger에 근거와 함께 반영한다.
- 문서와 코드가 다르면 Step 완료로 보지 않는다.

## 구현 시작점

첫 구현은 저장소 골격만 만드는 작업이 아니다. [Step 01](./development/implementation-steps.md#step-01--원천-데이터-불변-보존과-human-readable-profile)은 선택한 NIST 원천 데이터를 가져와 원본 그대로 보존하고, 해시와 출처를 기록한 뒤, 별도 파생물로 사람이 읽을 수 있는 프로파일을 생성하는 작은 end-to-end data discovery slice다.
