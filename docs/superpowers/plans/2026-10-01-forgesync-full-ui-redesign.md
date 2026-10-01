# ForgeSync Full UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the existing ForgeSync interface with an accessible Carbon-based manufacturing analysis workstation while preserving routes, contracts, replay convergence, provenance, and 3D failure isolation.

**Architecture:** Keep all changes in Presentation. A Carbon-backed application shell owns global theme and displayed operational context; existing feature routes publish already-presented context and continue consuming their current ViewModels and ports. Route-specific SCSS composes desktop grid areas and explicit narrow-screen document order without moving domain logic into UI components.

**Tech Stack:** React 19.2, TypeScript 5.9, Vite 7, Vitest and Testing Library, Playwright, `@carbon/react@1.113.0`, `sass@1.93.2`, React Three Fiber

**Spec:** `docs/superpowers/specs/2026-10-01-forgesync-full-ui-redesign-design.md`

## Global Constraints

- Keep `/`, `/factory`, and `/data-quality` and the labels `대시보드`, `공장 보기`, and `데이터 품질`.
- Keep all backend APIs, public schemas, domain terms, replay ordering, provenance meaning, and frontend ViewModel boundaries unchanged.
- Use official `@carbon/react` components and its bundled Carbon icons; do not add another component or icon system.
- Use Pretendard, a cold neutral palette, cobalt blue as the only product accent, and semantic green, amber, and red only for real state.
- Use a 2 px radius for containers and controls; full pills are limited to non-interactive status labels.
- Provide light and dark themes, default to the operating-system preference, and persist an explicit user override.
- Keep motion at 120-180 ms, limit it to feedback and state change, and honor `prefers-reduced-motion`.
- Preserve 2D status, replay, alarms, and analysis when 3D loading, assets, WebGL, or runtime context fail.
- Never show missing or not-evaluated evidence as normal, zero, complete, or 100 percent.
- Use Korean standard domain labels first; expose contract names and technical identifiers in contextual help or evidence detail.
- Do not introduce an em dash or en dash in visible UI copy.
- Validate at 1440 x 900, 768 x 1024, and 390 x 844 in both themes.
- Every product behavior follows RED, GREEN, REFACTOR, with the focused failing test run before product code changes.

## File Structure

### New files

- `docs/adr/ADR-059-carbon-presentation-system.md`: records the approved UI dependency, theming boundary, and fallback decision.
- `apps/factory-web/src/styles/index.scss`: single global style entry point and ordered partial imports.
- `apps/factory-web/src/styles/_tokens.scss`: ForgeSync light and dark semantic tokens.
- `apps/factory-web/src/styles/_shell.scss`: application rail, context bar, navigation, and responsive shell.
- `apps/factory-web/src/styles/_shared.scss`: shared typography, status, loading, focus, controls, and evidence patterns.
- `apps/factory-web/src/styles/_dashboard.scss`: dashboard summary band, timeline, alarm, effectiveness, and Pareto layout.
- `apps/factory-web/src/styles/_factory.scss`: factory grid areas, scene, inspector, replay transport, and narrow stacking.
- `apps/factory-web/src/styles/_data-quality.scss`: evidence groups, measurements, table, and disclosures.
- `apps/factory-web/src/features/shell/ui/ThemeControl.tsx`: system, light, and dark theme selection and persistence.
- `apps/factory-web/src/features/shell/ui/OperationalContext.tsx`: Presentation-only context slot published by the active route.
- `apps/factory-web/src/features/shell/ui/OperationalContextBar.tsx`: selected machine and displayed connection, freshness, and replay state.
- `apps/factory-web/src/shared/presentation/processGlossary.ts`: stable Korean label, concise explanation, and contract-term mapping.
- `apps/factory-web/src/shared/presentation/TermHelp.tsx`: accessible glossary disclosure using Carbon tooltip behavior.
- `apps/factory-web/tests/e2e/responsive-workstation.spec.ts`: route hierarchy, themes, responsive order, and non-overlap checks.

### Modified files

- `apps/factory-web/package.json` and `package-lock.json`: exact Carbon and Sass dependencies.
- `apps/factory-web/src/main.tsx`: imports `styles/index.scss` instead of the legacy stylesheet.
- `apps/factory-web/src/features/shell/ui/App.tsx` and `App.test.tsx`: new shell, context bar, theme control, and preserved routes.
- `apps/factory-web/src/features/dashboard/ui/DashboardRoute.tsx` and test: route context, summary hierarchy, and Korean evidence copy.
- `apps/factory-web/src/features/shift-overview/ui/ShiftOverviewPanel.tsx` and test: one summary band and synchronized interval presentation.
- `apps/factory-web/src/features/downtime/ui/DowntimeParetoPanel.tsx` and test: ranked investigation actions without repeated cards.
- `apps/factory-web/src/features/alarm/ui/AlarmPanel.tsx` and test: compact, semantic alarm presentation.
- `apps/factory-web/src/features/factory3d/ui/FactoryRoute.tsx`, test, and `factoryLayoutCss.test.ts`: accessible document order, desktop grid, Carbon icons, bounded scene, and non-overlapping tools.
- `apps/factory-web/src/features/factory3d/ui/MachineInspectionAppearance.tsx` and test: dedicated inspection toolbar.
- `apps/factory-web/src/features/twin/ui/MachineDetailView.tsx` and test: inspector hierarchy, glossary help, and contextual evidence.
- `apps/factory-web/src/features/twin/ui/TwinConnectionStatus.tsx` and test: semantic context labels without decorative dots.
- `apps/factory-web/src/features/replay/ui/ReplayControls.tsx` and test: Korean terminology, Carbon icons, and compact transport.
- `apps/factory-web/src/features/data-quality/ui/DataQualityRoute.tsx` and test: evidence-first groups and measured versus not-evaluated disclosure.
- `apps/factory-web/tests/e2e/factory-failure.spec.ts` and `machine-detail.spec.ts`: updated accessible names with unchanged authoritative behaviors.
- `docs/development/implementation-steps.md` and `docs/verification-ledger.md`: redesign completion record and verified browser evidence.
- `apps/factory-web/src/styles.css`: remove after all selectors have migrated to focused SCSS partials.

## Review Focus

- A missing `matchMedia` or storage implementation must still render the system theme and leave the theme control usable; Task 1 tests this fallback.
- A route unmount or rapid navigation must not leave another route's machine or replay status in the top context bar; Task 2 tests reset and replacement.
- A replay publication cursor ahead of the Twin cursor must show synchronization, not stale KPI values; Task 3 retains and extends this boundary test.
- At 390 px, 3D tools, the part selector, replay controls, and navigation must not overlap or cause horizontal page overflow; Task 4 and Task 6 test bounding boxes and scroll width.
- A data-quality report with all derived groups `NOT_EVALUATED` must keep reasons visible and render no percentage or normal state; Task 5 tests this input.

---

### Task 1: Carbon Foundation, ADR, Theme, and Application Shell

**Files:**
- Create: `docs/adr/ADR-059-carbon-presentation-system.md`
- Create: `apps/factory-web/src/features/shell/ui/ThemeControl.tsx`
- Create: `apps/factory-web/src/styles/index.scss`
- Create: `apps/factory-web/src/styles/_tokens.scss`
- Create: `apps/factory-web/src/styles/_shell.scss`
- Create: `apps/factory-web/src/styles/_shared.scss`
- Modify: `apps/factory-web/package.json`
- Modify: `apps/factory-web/package-lock.json`
- Modify: `apps/factory-web/src/main.tsx`
- Modify: `apps/factory-web/src/features/shell/ui/App.tsx`
- Test: `apps/factory-web/src/features/shell/ui/App.test.tsx`

**Interfaces:**
- Consumes: the existing `App` ports and route structure.
- Produces: `ThemeControl`, DOM attribute `data-carbon-theme="white|g100"`, storage key `forgesync-theme`, and shell regions `ForgeSync navigation`, `운영 상황`, and `main-content`.

- [ ] **Step 1: Write the failing shell behavior tests**

  Extend `App.test.tsx` with `uses a workstation rail without changing primary routes`, `follows the system theme until the user chooses an override`, and `renders when matchMedia and localStorage are unavailable`. Assert the three route hrefs, a button named `화면 테마`, `data-carbon-theme="g100"` when the stubbed system preference is dark, persistence of `LIGHT`, and a usable default when browser APIs throw.

- [ ] **Step 2: Run the focused tests and verify RED**

  Run: `npm test -- --run src/features/shell/ui/App.test.tsx`

  Expected: assertions fail because the current header has no theme control, Carbon theme attribute, or workstation rail. A compile or fixture error does not count.

- [ ] **Step 3: Record the dependency decision and install exact packages**

  Write ADR-059 with context, decision, alternatives, Presentation-only dependency direction, React 19 evidence, Sass entry point, bundle-risk mitigation, and rollback to native controls if an essential Carbon component is incompatible. Then run from `apps/factory-web`:

  `npm install --save-exact @carbon/react@1.113.0`

  `npm install --save-dev --save-exact sass@1.93.2`

  Official evidence: `@carbon/react` 1.113.0 declares React and React DOM `^19.0.0` peer support and includes Carbon styles and icons.

- [ ] **Step 4: Implement the minimal shell and theme behavior**

  Implement `ThemeControl(): JSX.Element` with preference union `"SYSTEM" | "LIGHT" | "DARK"`. Use `matchMedia("(prefers-color-scheme: dark)")`, persist only explicit choice in `forgesync-theme`, apply Carbon `white` or `g100` once at the shell root, and clean up the media listener. Recompose `App` with a single-line desktop rail, compact narrow navigation, Carbon theme provider, and the existing routes. Import `styles/index.scss` from `main.tsx`.

- [ ] **Step 5: Run GREEN, refactor styles, and commit**

  Run: `npm test -- --run src/features/shell/ui/App.test.tsx`

  Run: `npm run typecheck && npm run lint && npm run build`

  Expected: all pass and the production build includes the Carbon/Sass entry without warnings treated as errors.

  Commit: `feat(web): add Carbon workstation shell and themes`

### Task 2: Operational Context Bar and Domain Glossary

**Files:**
- Create: `apps/factory-web/src/features/shell/ui/OperationalContext.tsx`
- Create: `apps/factory-web/src/features/shell/ui/OperationalContextBar.tsx`
- Create: `apps/factory-web/src/shared/presentation/processGlossary.ts`
- Create: `apps/factory-web/src/shared/presentation/TermHelp.tsx`
- Modify: `apps/factory-web/src/features/shell/ui/App.tsx`
- Modify: `apps/factory-web/src/features/dashboard/ui/DashboardRoute.tsx`
- Modify: `apps/factory-web/src/features/factory3d/ui/FactoryRoute.tsx`
- Modify: `apps/factory-web/src/features/data-quality/ui/DataQualityRoute.tsx`
- Modify: `apps/factory-web/src/features/twin/ui/TwinConnectionStatus.tsx`
- Modify: `apps/factory-web/src/features/replay/ui/ReplayControls.tsx`
- Test: `apps/factory-web/src/features/shell/ui/App.test.tsx`
- Test: `apps/factory-web/src/features/twin/ui/TwinConnectionStatus.test.tsx`
- Test: `apps/factory-web/src/features/replay/ui/ReplayControls.test.tsx`

**Interfaces:**
- Consumes: already-derived UI labels from each active route; no backend DTO.
- Produces: `OperationalContextSnapshot`, `useOperationalContextPublisher(snapshot)`, `PROCESS_GLOSSARY`, and `<TermHelp term="REPLAY_CURSOR" />`.

  `OperationalContextSnapshot` has `machineId: string` and three `{ label: string; tone: "neutral" | "positive" | "warning" | "critical" }` fields named `connection`, `freshness`, and `replay`.

- [ ] **Step 1: Write the failing terminology and lifecycle tests**

  Add assertions that the shell region `운영 상황` shows `Mazak01`, connection, freshness, and replay labels from the current route; navigate from `/factory` to `/data-quality` and assert stale factory context is replaced. Update replay expectations to `과거 데이터 재생`, `원천 관찰 시각`, `재생 발행 시각`, and `데이터 최신성`. Assert the help trigger for `트윈 버전` exposes “설비 상태가 갱신된 순서입니다.”

- [ ] **Step 2: Run focused tests and verify RED**

  Run: `npm test -- --run src/features/shell/ui/App.test.tsx src/features/twin/ui/TwinConnectionStatus.test.tsx src/features/replay/ui/ReplayControls.test.tsx`

  Expected: user-visible Korean labels, help, and route context assertions fail against the current English-heavy UI.

- [ ] **Step 3: Implement context publication and glossary disclosure**

  Implement `useOperationalContextPublisher(snapshot: OperationalContextSnapshot): void` with effect cleanup that restores neutral defaults only if the unmounting route still owns the published revision. Implement the context bar with semantic icon plus text, never decorative dots. Define glossary keys `REPLAY`, `REPLAY_CURSOR`, `TWIN_VERSION`, `FRESHNESS`, `PROVENANCE`, `OBSERVED`, `SIMULATED`, and `NOT_EVALUATED`, each with the exact primary label and explanation from the spec.

- [ ] **Step 4: Update route publishers and shared copy**

  Dashboard, factory, and data-quality routes publish already-presented context from their current hook state. Replace unexplained `Replay`, `Source Time`, `Replay Time`, `Twin Freshness`, `OBSERVED`, and `SIMULATED` primary labels with glossary-backed Korean labels while retaining contract terms inside evidence details and `data-*` attributes where tests or diagnostics require them.

- [ ] **Step 5: Run GREEN and commit**

  Run: `npm test -- --run src/features/shell/ui/App.test.tsx src/features/twin/ui/TwinConnectionStatus.test.tsx src/features/replay/ui/ReplayControls.test.tsx`

  Run: `npm run typecheck && npm run lint`

  Expected: all pass, including rapid route replacement and browser-API fallback.

  Commit: `feat(web): add operational context and glossary help`

### Task 3: Dashboard Interval Investigation Hierarchy

**Files:**
- Create: `apps/factory-web/src/styles/_dashboard.scss`
- Modify: `apps/factory-web/src/features/dashboard/ui/DashboardRoute.tsx`
- Modify: `apps/factory-web/src/features/dashboard/ui/DashboardRoute.test.tsx`
- Modify: `apps/factory-web/src/features/shift-overview/ui/ShiftOverviewPanel.tsx`
- Modify: `apps/factory-web/src/features/shift-overview/ui/ShiftOverviewPanel.test.tsx`
- Modify: `apps/factory-web/src/features/downtime/ui/DowntimeParetoPanel.tsx`
- Modify: `apps/factory-web/src/features/downtime/ui/DowntimeParetoPanel.test.tsx`
- Modify: `apps/factory-web/src/features/alarm/ui/AlarmPanel.tsx`
- Modify: `apps/factory-web/src/features/alarm/ui/AlarmPanel.test.tsx`

**Interfaces:**
- Consumes: existing `ShiftOverview`, `Alarm[]`, `DowntimeParetoReport`, and replay seek callbacks.
- Produces: regions named `분석 가능 상태`, `교대조 핵심 지표`, `설비 상태 구간`, `확인할 알람`, and `주요 정지 원인`; selection callbacks remain unchanged.

- [ ] **Step 1: Write the failing dashboard hierarchy tests**

  Extend existing tests to assert scan order through DOM position, a single summary band with no `.metric-card` children, semantic icons plus text for interrupted and alarm states, and one `시점 상세 보기` intent. Keep the existing test that waits when publication cursor is ahead and add an assertion that no previous KPI band is rendered while synchronizing.

- [ ] **Step 2: Run focused tests and verify RED**

  Run: `npm test -- --run src/features/dashboard/ui/DashboardRoute.test.tsx src/features/shift-overview/ui/ShiftOverviewPanel.test.tsx src/features/downtime/ui/DowntimeParetoPanel.test.tsx src/features/alarm/ui/AlarmPanel.test.tsx`

  Expected: summary-band structure and scan-order assertions fail while existing seek behavior remains green.

- [ ] **Step 3: Recompose the dashboard without changing data orchestration**

  Keep all existing effects, cursor guards, and client calls. Replace the eyebrow/card hierarchy with a compact route heading, analysis-readiness status, one KPI band, synchronized timeline, alarm group, and ranked downtime actions. Use Carbon skeletons and inline notifications for loading, mismatch, insufficient data, and failure.

- [ ] **Step 4: Add dashboard styles and retain boundary behavior**

  Implement desktop density and explicit one-column collapse below 768 px in `_dashboard.scss`. Use patterns plus text for `INTERRUPTED` and `UNKNOWN`. Do not add an overall quality or OEE score and do not weaken existing replay seek assertions.

- [ ] **Step 5: Run GREEN, refactor, and commit**

  Run the focused command from Step 2, then `npm run typecheck && npm run lint`.

  Expected: all tests pass, including loading, mismatch, cursor-wait, alarm identity, downtime seek, and no fabricated quality value.

  Commit: `feat(web): redesign shift investigation dashboard`

### Task 4: Factory Investigation Workspace and Bounded 3D Tools

**Files:**
- Create: `apps/factory-web/src/styles/_factory.scss`
- Modify: `apps/factory-web/src/features/factory3d/ui/FactoryRoute.tsx`
- Modify: `apps/factory-web/src/features/factory3d/ui/FactoryRoute.test.tsx`
- Modify: `apps/factory-web/src/features/factory3d/ui/factoryLayoutCss.test.ts`
- Modify: `apps/factory-web/src/features/factory3d/ui/MachineInspectionAppearance.tsx`
- Modify: `apps/factory-web/src/features/factory3d/ui/MachineInspectionAppearance.test.tsx`
- Modify: `apps/factory-web/src/features/twin/ui/MachineDetailView.tsx`
- Modify: `apps/factory-web/src/features/twin/ui/MachineDetailView.test.tsx`
- Modify: `apps/factory-web/src/features/replay/ui/ReplayControls.tsx`
- Modify: `apps/factory-web/src/features/replay/ui/ReplayControls.test.tsx`

**Interfaces:**
- Consumes: existing `MachineVisualState`, `MachineDetailViewModel`, `ReplayController`, scene loader, selection store, and callbacks.
- Produces: CSS grid areas `detail`, `scene`, `inspector`, and `transport`; document order `detail -> transport -> scene -> inspector` at narrow widths; unchanged accessible projection labels and replay commands.

- [ ] **Step 1: Write the failing workspace and narrow-order tests**

  Assert that the critical 2D summary precedes replay controls and the scene in document order, the desktop CSS names the four grid areas, the narrow rule stacks `detail`, `transport`, `scene`, `inspector`, and the inspection toolbar is outside the canvas stage. Replace the old hand-rolled-glyph assertion with Carbon icon accessible-name assertions. Retain WebGL, context-loss, 2D survival, selection identity, reduced-motion, and authoritative cursor tests.

- [ ] **Step 2: Run focused tests and verify RED**

  Run: `npm test -- --run src/features/factory3d/ui/FactoryRoute.test.tsx src/features/factory3d/ui/factoryLayoutCss.test.ts src/features/factory3d/ui/MachineInspectionAppearance.test.tsx src/features/twin/ui/MachineDetailView.test.tsx src/features/replay/ui/ReplayControls.test.tsx`

  Expected: document-order, named grid-area, toolbar placement, and Carbon-icon assertions fail for the current scene-first markup.

- [ ] **Step 3: Recompose markup and replace hand-rolled icons**

  Preserve hooks and state orchestration, but render regions in accessible narrow-screen order and position them with desktop grid areas. Use Carbon icons for 2D, 3D, split, reset, zoom, play, pause, start, and end controls. Keep labels `평면 보기`, `입체 보기`, and `평면과 입체 함께 보기`. Move inspection appearance controls into a dedicated toolbar below the bounded scene stage.

- [ ] **Step 4: Rebuild detail, evidence, replay, and responsive styles**

  Turn `MachineDetailView` compact mode into the right inspector with state first, measurements second, and contextual evidence last. Keep full 2D mode complete. Set stable scene dimensions, explicit overflow rules, and no fixed overlay controls below 768 px. At 390 px, buttons may wrap as groups but each label remains on one line and the page itself has no horizontal overflow.

- [ ] **Step 5: Run GREEN and commit**

  Run the focused command from Step 2, then `npm run typecheck && npm run lint && npm run build`.

  Expected: all pass, including WebGL failure and runtime context-loss isolation.

  Commit: `feat(web): redesign factory investigation workspace`

### Task 5: Data-Quality Evidence Workspace

**Files:**
- Create: `apps/factory-web/src/styles/_data-quality.scss`
- Modify: `apps/factory-web/src/features/data-quality/ui/DataQualityRoute.tsx`
- Modify: `apps/factory-web/src/features/data-quality/ui/DataQualityRoute.test.tsx`
- Modify: `apps/factory-web/src/features/twin/ui/MachineDetailView.tsx`
- Modify: `apps/factory-web/src/features/twin/ui/MachineDetailView.test.tsx`

**Interfaces:**
- Consumes: unchanged `DataQualityReport` and `DataQualityClient`.
- Produces: evidence groups `원천과 의미 매핑`, `수신과 순서`, `데이터 최신성`, `파생 분석 범위`, and `미해석 원천 항목`; link from Twin detail remains `/data-quality`.

- [ ] **Step 1: Write the failing evidence hierarchy tests**

  Assert the five named groups, primary Korean labels, glossary help, source profile before runtime scope, and original locator links. Add a fixture transformation where every derived group is `NOT_EVALUATED`; assert all reason strings remain visible and the route renders no percent, `정상`, or `100%` for those groups. Keep debounce and cursor-scope tests.

- [ ] **Step 2: Run focused tests and verify RED**

  Run: `npm test -- --run src/features/data-quality/ui/DataQualityRoute.test.tsx src/features/twin/ui/MachineDetailView.test.tsx`

  Expected: the new group names, ordering, and all-not-evaluated assertions fail against the current equal-card layout.

- [ ] **Step 3: Recompose evidence without changing calculations**

  Group existing report fields by evidence layer, use Carbon structured list or data table only for genuinely tabular unknown items, and use disclosures for long locator and processing metadata. Render `평가할 수 없음` plus the report reason for `NOT_EVALUATED`. Keep exact measured numerators and denominators and retain `종합 품질 점수 없음` as explanatory text, not a warning badge.

- [ ] **Step 4: Style desktop groups and narrow fallback**

  Use a two-region desktop layout for overview and evidence details, then one column below 768 px. Keep headers visible during horizontal table scrolling, provide a text alternative for every status icon, and avoid one bordered card per measurement.

- [ ] **Step 5: Run GREEN and commit**

  Run the focused command from Step 2, then `npm run typecheck && npm run lint`.

  Expected: all pass, including source-first load, cursor refresh, failure, zero denominator, and all-not-evaluated coverage.

  Commit: `feat(web): redesign data quality evidence view`

### Task 6: Browser Acceptance, Legacy CSS Removal, and Verification Record

**Files:**
- Create: `apps/factory-web/tests/e2e/responsive-workstation.spec.ts`
- Modify: `apps/factory-web/tests/e2e/factory-failure.spec.ts`
- Modify: `apps/factory-web/tests/e2e/machine-detail.spec.ts`
- Modify: `apps/factory-web/playwright.config.ts`
- Delete: `apps/factory-web/src/styles.css`
- Modify: `docs/development/implementation-steps.md`
- Modify: `docs/verification-ledger.md`

**Interfaces:**
- Consumes: all previous task UI contracts and existing E2E fixture routes.
- Produces: browser evidence at the three specified viewports in light and dark themes, verification entry `V-066`, and no legacy stylesheet.

- [ ] **Step 1: Write failing browser acceptance checks**

  Add table-driven checks for 1440 x 900, 768 x 1024, and 390 x 844. For each theme, assert the app root theme, one-line desktop navigation where applicable, `document.documentElement.scrollWidth === innerWidth`, critical 2D summary before 3D on narrow layouts, and pairwise non-overlap of navigation, replay transport, scene toolbar, and part selector bounding boxes. Preserve the WebGL-failure expectation that 2D and replay remain usable.

- [ ] **Step 2: Run the browser checks and verify RED**

  Run with the existing local E2E runtime: `./scripts/verify-e2e`

  Expected: at least the new responsive-order or non-overlap assertion fails before final layout cleanup. Infrastructure startup failure does not count as RED.

- [ ] **Step 3: Remove legacy CSS and close responsive gaps**

  Move any still-used selector from `styles.css` into the owning partial, delete `styles.css`, and use `rg` to confirm no import remains. Fix only issues exposed by the acceptance checks; do not change domain or adapter behavior.

- [ ] **Step 4: Run full verification and record evidence**

  Run: `npm --prefix apps/factory-web run verify`

  Run: `./scripts/verify`

  Run: `./scripts/verify-e2e`

  Run: `git diff --check`

  Expected: every command exits 0. Record RED, focused GREEN, post-refactor related verification, viewport/theme matrix, Carbon version, and any measured build-size change in `docs/development/implementation-steps.md`. Add `V-066` as `VERIFIED` only if the real-browser matrix and 3D failure-isolation checks pass; otherwise record `TO_VERIFY` with the exact blocker.

- [ ] **Step 5: Perform the anti-slop pre-flight and commit**

  Mechanically inspect visible UI strings for em dash and en dash, unexplained English contract labels, duplicate CTA intent, decorative status dots, and invented precision. Inspect both themes at the three viewports and confirm no overlap, contrast failure, clipped label, or horizontal overflow.

  Commit: `test(web): verify responsive ForgeSync workstation`

## Final Review Gate

After Task 6, run a whole-branch review against the design spec, ADR-059, AGENTS.md invariants, and the verification output. Reject completion if any test was weakened, any 3D failure removes 2D behavior, any friendly label changes domain meaning, or any command above was not actually run and reported.
