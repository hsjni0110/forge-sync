# ADR-059: Carbon-based Presentation System

- Status: Accepted
- Date: 2026-10-01
- Related: [Full UI redesign design](../superpowers/specs/2026-10-01-forgesync-full-ui-redesign-design.md)

## Context

ForgeSync의 세 화면은 같은 제조 운영 정보를 보여 주지만, 공통 색상·간격·상태 표현과 표준 컨트롤이
일관되지 않았다. 설비 엔지니어가 장시간 사용하는 데스크톱 화면을 우선하면서도 현장 작업자가 상태를
빠르게 구분할 수 있는 명확한 계층이 필요하다. 이 변경은 Presentation Context에만 해당하며 Twin,
Replay, Alarm, Data Quality의 의미와 데이터 계약은 바꾸지 않는다.

React 19 호환성과 유지되는 디자인 토큰, 접근 가능한 표준 컨트롤, 밝은·어두운 테마가 필요하다.
`@carbon/react` 1.113.0은 React와 React DOM `^19.0.0` peer 범위를 선언하고 Carbon 스타일과 아이콘을
함께 제공한다. ForgeSync의 프런트엔드는 현재 React 19를 사용한다.

## Decision

- Presentation Context의 디자인 시스템으로 `@carbon/react` 1.113.0을 정확한 버전으로 고정한다.
- Sass 진입점은 `src/styles/index.scss` 하나로 두고 `sass` 1.93.2를 개발 의존성으로 고정한다.
- Carbon은 버튼, 선택, 피드백과 테마 기반을 제공한다. ForgeSync의 작업공간 레이아웃과 제조 운영
  계층은 별도 Sass 모듈에서 Carbon 토큰을 사용해 구성한다.
- 애플리케이션 루트에는 `white` 또는 `g100` 테마를 한 번만 적용한다. 초기값은 운영체제 설정을
  따르며, 사용자가 고른 `LIGHT` 또는 `DARK`만 `forgesync-theme`에 저장한다.
- 이 의존성은 Presentation 안쪽으로만 향한다. Domain, Application, Adapter와 versioned contract는
  Carbon을 참조하지 않는다.
- 초기 번들 증가는 전체 Carbon 컴포넌트를 무분별하게 감싸지 않고 실제 사용하는 모듈만 import하며,
  production build 결과를 확인해 관리한다.
- 필수 Carbon 컴포넌트가 React 19 또는 ForgeSync의 접근성 요구와 호환되지 않으면 해당 컨트롤을
  native HTML로 되돌린다. 도메인 계약이나 라우트는 롤백 대상이 아니다.

## Alternatives Considered

- 기존 CSS만 확장: 의존성은 늘지 않지만 접근 가능한 표준 컨트롤과 테마 토큰을 계속 자체 유지해야 한다.
- Material UI: 완성도 높은 선택지지만 이번 제조 워크스테이션의 조밀한 정보 구조와 Carbon 토큰을
  활용하는 방향보다 시각적 조정 범위가 크다.
- 모든 컴포넌트를 자체 제작: 시각적 자유도는 높지만 접근성, 상호작용, 테마 회귀 위험이 커진다.

## Consequences

- 공통 토큰과 컨트롤을 사용해 세 화면의 상태와 상호작용이 일관된다.
- Carbon과 Sass가 프런트엔드 빌드 의존성 및 번들에 추가된다.
- ForgeSync 전용 레이아웃은 Carbon 예제를 그대로 복제하지 않고 별도 Presentation 스타일로 유지한다.

## Verification

- App 단위 테스트가 기존 라우트, 시스템 테마, 명시적 사용자 선택 저장, 브라우저 API 장애 fallback을
  검증한다.
- typecheck, lint와 production build가 React 19 및 Sass 진입점 호환성을 검증한다.
- 최종 Chromium E2E가 밝은·어두운 테마와 세 기준 viewport의 핵심 흐름을 검증한다.
