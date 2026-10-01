# ForgeSync Full UI Redesign Design

Date: 2026-10-01

## 1. Decision Summary

ForgeSync will receive a full visual redesign for manufacturing engineers who investigate causes on
desktop workstations. The same interface must also let shop-floor operators recognize equipment and
data states quickly from narrower or more distant displays.

The redesign will replace the current color and layout system while preserving product behavior,
URLs, public contracts, data meaning, provenance, replay ordering, and frontend feature boundaries.
It will use the official Carbon React component system as its interaction and accessibility
foundation, with a ForgeSync-specific visual theme rather than Carbon's default appearance.

The design dials are:

- `DESIGN_VARIANCE: 4`: structured, offset only where it improves scan order
- `MOTION_INTENSITY: 3`: direct state and interaction feedback, no decorative motion
- `VISUAL_DENSITY: 7`: dense enough for engineering analysis without turning every value into a card

## 2. Goals

1. Make the first required action and the current operational situation clear on every route.
2. Support the analysis flow: understand the interval, select a moment, investigate the cause, and
   inspect the evidence.
3. Explain ForgeSync terminology without replacing or weakening the ubiquitous language.
4. Preserve an accessible 2D workflow when 3D loading, rendering, or assets fail.
5. Provide equivalent information hierarchy and contrast in light and dark themes.
6. Remove repeated bordered panels, competing labels, and overlapping narrow-screen controls.

## 3. Non-Goals

- Changing route slugs or the primary navigation labels
- Changing backend APIs, public schemas, or domain terminology
- Changing the meaning of freshness, replay cursor, twin version, provenance, or evidence state
- Making 3D the source of authoritative equipment state
- Adding a new microservice, data projection, infrastructure dependency, or business capability
- Inventing live values, quality scores, alarm meaning, or physical accuracy not supported by source
  evidence
- Rewriting the application into a different frontend framework

## 4. Users and Operating Context

### Primary user: manufacturing engineer

The primary user works on a desktop workstation and needs to compare time ranges, move the replay
cursor, inspect equipment and process state, and verify how a value was derived. The interface must
support sustained investigation without hiding density behind excessive navigation.

### Secondary user: shop-floor operator

The secondary user needs rapid recognition of connection, freshness, execution, warning, fault, and
replay states. Critical summaries must remain readable on narrow displays and at a glance. Color may
reinforce a state but may not be its only cue.

## 5. Design Foundation

### 5.1 Component system

Use the official Carbon React components and Carbon Icons for standard controls, navigation,
disclosures, tooltips, loading states, inline notifications, and data presentation. Do not mix a
second component or icon system into the same application.

Carbon supplies interaction behavior and accessibility semantics. ForgeSync owns the product theme,
information architecture, domain language, 3D workspace, and evidence presentation.

Before implementation, verify that the selected Carbon package versions support the repository's
React and build tool versions. A compatibility conflict must be resolved in the implementation plan,
not hidden by copying Carbon CSS or components into the repository.

### 5.2 Visual language

- Use a cold neutral gray scale with cobalt blue as the single product accent.
- Reserve green, amber, and red for semantic state only.
- Use a 2 px radius for containers and controls. Status tags may use a full pill only when the shape
  communicates that the element is a non-interactive label.
- Keep Pretendard as the typeface because it provides consistent Hangul, Latin, and digit rendering
  from a self-hosted asset.
- Use tabular numbers for RPM, time, counts, versions, percentages, and sequences.
- Prefer bands, grouped regions, sparse dividers, and whitespace over repeated card containers.
- Avoid gradients, glow, glass effects, decorative status dots, and non-semantic animation.

### 5.3 Theme behavior

Provide light and dark themes. The initial theme follows the operating system preference, and a
global control lets the user override it. A route uses one coherent theme at a time. Individual
sections do not invert independently.

Light mode prioritizes long-form analysis in office environments. Dark mode prioritizes low-light
control-room and 3D viewing conditions. Both modes must preserve the same information hierarchy and
meet WCAG AA contrast.

### 5.4 Motion

Use 120-180 ms transitions only for hover, pressed, focus, disclosure, selection, and state-change
feedback. Animate transform and opacity only where possible. Respect `prefers-reduced-motion`; no
essential meaning may depend on motion.

## 6. Information Architecture

Keep these routes and primary navigation labels:

- `/`: `대시보드`
- `/factory`: `공장 보기`
- `/data-quality`: `데이터 품질`

### 6.1 Application shell

Desktop uses a persistent left navigation rail and a top context bar.

The navigation rail contains the ForgeSync identity and the three primary destinations. The context
bar shows the selected machine, connection status, data freshness, replay state, and theme control.
Only actual semantic states receive status treatments.

On narrow screens, navigation collapses into a compact header or drawer. Critical equipment and data
states remain above 3D content and do not depend on opening the navigation.

### 6.2 Dashboard

The dashboard answers what happened over the observed interval and what requires attention. Its scan
order is:

1. Whether the data is currently suitable for analysis
2. Distribution of active, ready, stopped, interrupted, and unknown time
3. Open or relevant alarms
4. Largest observed losses
5. Time intervals that require investigation

Metrics form a single summary band instead of a row of equal cards. The synchronized state timeline
follows the summary. Alarm and downtime selections take the user to the matching replay moment in the
factory route.

### 6.3 Factory view

The factory route is the moment-level investigation workspace.

- Center workspace: selected 2D or 3D equipment representation
- Right inspector: execution, RPM, tool, program, selected component, and related evidence
- Bottom transport: replay controls and the shared authoritative timeline
- Evidence drawer: source and lineage, source time, replay time, projected time, twin version, and
  relevant processing or mapping versions

The 2D view remains usable at all times. The 3D view is a replaceable projection that helps interpret
physical position and motion. A 3D failure must not remove replay, alarms, analysis, or 2D equipment
state.

On narrow screens, content stacks in this order: critical 2D summary, replay controls, 3D view,
inspector, evidence. 3D controls use a dedicated tool region and never overlay or collide with
selection controls.

### 6.4 Data quality

The data-quality route answers why a displayed value can or cannot be trusted. It groups evidence by
meaning rather than presenting a single overall score:

1. Source profile and semantic mapping
2. Runtime validity, ordering, and duplication
3. Twin freshness
4. Derived process and feature coverage
5. Unknown or unsupported source items and locators

Measured and not-evaluated states remain distinct. Missing evidence is never displayed as normal,
complete, or 100 percent.

## 7. Terminology Experience

The UI follows `docs/도메인 용어.md`. It does not invent synonyms for different domain concepts.

### 7.1 Three-level disclosure

1. Primary labels use the Korean standard name.
2. A concise help description explains unfamiliar concepts in operational language.
3. Contract names and technical identifiers appear in the evidence detail.

Initial presentation mappings include:

| Current or technical label | Primary UI label | Short explanation |
| --- | --- | --- |
| Replay | 과거 데이터 재생 | 기록된 관찰을 원래 순서에 따라 다시 보여줍니다. |
| Replay Cursor | 재생 위치 | 모든 화면이 함께 가리키는 재생 시점입니다. |
| Twin Version | 트윈 버전 | 설비 상태가 갱신된 순서입니다. |
| Freshness | 데이터 최신성 | ForgeSync에 마지막으로 반영된 뒤 지난 시간을 나타냅니다. |
| Provenance | 출처와 계보 | 값의 원천과 변환 과정을 보여줍니다. |
| Observed | 관찰에서 확인 | 원천 데이터에서 직접 확인하거나 재구성한 내용입니다. |
| Simulated | 작성된 표현 | 실제 측량이나 관찰이 아닌 화면 구성을 위한 표현입니다. |
| Not evaluated | 평가할 수 없음 | 현재 근거로는 값을 계산하거나 판정할 수 없습니다. |

`REAL`, `SIMULATED`, and `OBSERVED` do not appear as unexplained standalone badges. The primary text
states the meaning, such as `실측 관찰값`, `작성된 3D 배치`, or `관찰에서 계산한 값`. The contract or
evidence classification may be shown secondarily.

### 7.2 Central glossary model

Presentation owns a central glossary model containing the primary label, concise explanation, and
contract term. Feature UI consumes this model instead of duplicating prose. The glossary is not a
domain model and does not alter contract names.

## 8. Interaction Flow

The shared investigation flow is:

```text
상황 파악 -> 시점 선택 -> 원인 분석 -> 근거 확인
```

Dashboard timeline markers, alarm entries, and downtime causes use the existing replay seek path.
After navigation or seek, 2D, 3D, alarm, and analysis consumers wait for the same authoritative
cursor and twin version. The interface does not show mixed-version results during convergence.

The evidence drawer is contextual. It opens for the selected metric, state, alarm, interval, or 3D
cue and displays only the evidence needed to interpret that selection.

## 9. State Model and Error Handling

Every major region supports a complete state cycle.

### Loading

Use structural skeletons that reserve the final layout dimensions. The 3D canvas and inspector keep
stable dimensions to prevent layout shift.

### Empty

State why no data exists and identify the next valid action. Do not use generic empty-state
illustrations or imply a successful zero value.

### Reconnecting or resynchronizing

Show that the last received value remains visible and identify it as historical. Keep the
authoritative resync behavior unchanged.

### Stale

Warn that the display must not be used as a real-time indication. Stop or mute live-looking 3D
motion according to the existing presentation policy.

### Replay completed

Present completion as a neutral lifecycle state, distinct from lag, disconnection, and failure.

### Version mismatch

Do not combine values from different authoritative versions. Show a synchronization state until the
consumers converge or a stable error if convergence fails.

### 3D unavailable

Explain whether loading, asset, or rendering failed when that distinction is safe to expose. Keep
the 2D state, replay controls, alarm interaction, and analysis available.

### Not evaluated

State which evidence or denominator is missing. Never substitute normal, zero, or complete.

All warning and fault states use an icon, a heading, and concise text in addition to color.

## 10. Frontend Boundaries

The redesign remains in Presentation.

- The application shell stays under `features/shell/ui`.
- Route compositions stay in their existing feature UI directories.
- Shared presentation primitives and glossary data may live under `shared/presentation` when at least
  two feature consumers exist.
- Existing application ViewModels remain the route-facing data boundary.
- 3D continues to consume `MachineVisualState` and must not import backend DTOs.
- Domain and application layers must not import Carbon, React components, browser APIs, or theme
  concepts.
- External failures continue to be translated at adapter and application boundaries before UI
  presentation.

## 11. Delivery Slices

Implementation will proceed in reviewable vertical behavior slices:

1. Carbon compatibility, global tokens, theme behavior, and application shell
2. Shared status presentation, glossary help, and evidence disclosure
3. Dashboard hierarchy and interval investigation entry points
4. Factory workspace, replay transport, inspector, and non-overlapping 3D controls
5. Data-quality evidence hierarchy
6. Narrow-screen, keyboard, screen-reader, contrast, and cross-theme refinement

Each slice follows RED, GREEN, REFACTOR. Product code changes begin only after a public behavior test
or explicit reproducible scenario fails for the intended reason.

## 12. Verification and Acceptance

### 12.1 Functional acceptance

- Existing routes, navigation labels, and deep links continue to work.
- Dashboard alarm, interval, and downtime selections use the existing replay seek path.
- 2D, 3D, alarm, and analysis views converge on the same replay cursor and twin version.
- 3D failure leaves all critical 2D and replay behaviors usable.
- Data-quality states do not collapse measured, missing, and not-evaluated evidence.

### 12.2 Presentation acceptance

- The first viewport identifies the selected machine, connection, freshness, and replay state.
- Primary actions and critical warnings remain visible and readable at supported desktop and narrow
  widths.
- No 3D, selection, replay, or navigation controls overlap at narrow widths.
- Light and dark themes preserve hierarchy and WCAG AA contrast.
- Status is understandable without color.
- Technical identifiers are available without dominating the primary scan path.
- Loading, empty, reconnecting, stale, completed, version-mismatch, 3D-failure, and not-evaluated
  states have distinct copy and presentation.

### 12.3 Automated and browser verification

- Component tests cover accessible names, keyboard order, disclosures, and state-specific copy.
- Theme tests verify explicit and system-preference selection behavior.
- Existing architecture tests continue to enforce feature and 3D adapter boundaries.
- Existing E2E scenarios continue to cover replay convergence and 3D failure isolation.
- Browser checks cover desktop analysis width and narrow shop-floor width in both themes.
- Browser acceptance uses 1440 x 900 for the primary desktop workstation, 768 x 1024 for the narrow
  shop-floor layout, and 390 x 844 for the minimum mobile layout.
- The final change runs `./scripts/verify` and the relevant real-browser E2E suite.

## 13. Risks and Mitigations

### Carbon compatibility and bundle cost

Verify React and Vite compatibility before product code changes. Import only used components and
styles, and measure the production build rather than assuming the effect.

### Visual redesign hiding semantic regressions

Write behavior-first tests around existing labels, states, and transitions before replacing
components. Preserve ViewModels and domain classifications.

### Dense analysis becoming card-heavy

Use cards only for interactive or elevated regions. Metrics use bands and grouped sections;
evidence uses disclosures and inspector regions.

### Terminology simplification changing meaning

Map every friendly label back to the ubiquitous language and keep the contract term in contextual
help or evidence. Any change to data meaning or ownership requires a separate ADR decision.

### 3D dominating narrow layouts

Keep 2D status and replay controls earlier in document order. Give 3D a bounded region and a separate
control toolbar with explicit responsive tests.

## 14. Pre-Implementation Decisions

The implementation plan must resolve these technical details using current repository and official
package evidence:

1. Record the Carbon dependency and theme integration decision in an ADR before product code imports
   the new package.
2. Exact Carbon package versions compatible with React 19 and the current Vite toolchain
3. Theme token integration strategy and whether generated Carbon CSS or Sass entry points best fit
   the existing build
4. Icon migration inventory and the smallest first vertical slice

These decisions may refine implementation mechanics but may not change the approved product scope,
domain meanings, or acceptance criteria without another design review.
