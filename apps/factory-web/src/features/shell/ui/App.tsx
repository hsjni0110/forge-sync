import { Link, NavLink, Route, Routes } from "react-router-dom";

import { DashboardRoute } from "../../dashboard/ui/DashboardRoute";
import { FactoryRoute } from "../../factory3d/ui/FactoryRoute";
import type { FactorySceneLoader } from "../../factory3d/ui/factorySceneContract";
import type { TwinSessionFactory } from "../../twin/application/ports";
import type { ReplayControlClient } from "../../replay/application/ports";
import type { ToolChangeClient } from "../../tool-change/application/ports";
import type { ObservedToolpathClient } from "../../toolpath/application/ports";
import type { DowntimeParetoClient } from "../../downtime/application/ports";
import type { OperationalEffectivenessClient } from "../../effectiveness/application/ports";

const loadFactoryScene: FactorySceneLoader = () =>
  import("../../factory3d/ui/FactoryScene");

export function App({
  twinSessionFactory,
  replayControlClient,
  factorySceneLoader = loadFactoryScene,
  toolChangeClient,
  observedToolpathClient,
  downtimeParetoClient,
  operationalEffectivenessClient,
}: {
  twinSessionFactory: TwinSessionFactory;
  replayControlClient?: ReplayControlClient;
  factorySceneLoader?: FactorySceneLoader;
  toolChangeClient?: ToolChangeClient;
  observedToolpathClient?: ObservedToolpathClient;
  downtimeParetoClient?: DowntimeParetoClient;
  operationalEffectivenessClient?: OperationalEffectivenessClient;
}) {
  return (
    <div className="site-shell">
      <a className="skip-link" href="#main-content">
        본문으로 바로가기
      </a>
      <header className="site-header">
        <Link className="brand" to="/">
          <span className="brand-mark" aria-hidden="true">FS</span>
          <span className="brand-lockup">
            <strong>ForgeSync</strong>
            <small>Operational Twin</small>
          </span>
        </Link>
        <nav aria-label="주요 메뉴">
          <NavLink to="/" end>대시보드</NavLink>
          <NavLink to="/factory">공장 보기</NavLink>
        </nav>
      </header>
      <main id="main-content" className="app-shell">
        <Routes>
          <Route
            path="/"
            element={
              <DashboardRoute
                twinSessionFactory={twinSessionFactory}
                replayControlClient={replayControlClient}
                downtimeParetoClient={downtimeParetoClient}
                operationalEffectivenessClient={operationalEffectivenessClient}
              />
            }
          />
          <Route
            path="/factory"
            element={
              <FactoryRoute
                sessionFactory={twinSessionFactory}
                replayControlClient={replayControlClient}
                sceneLoader={factorySceneLoader}
                toolChangeClient={toolChangeClient}
                observedToolpathClient={observedToolpathClient}
              />
            }
          />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </main>
    </div>
  );
}

function NotFound() {
  return (
    <section className="page-message">
      <h1>페이지를 찾을 수 없습니다</h1>
      <p>요청한 ForgeSync 화면이 존재하지 않습니다.</p>
      <Link to="/">홈으로 돌아가기</Link>
    </section>
  );
}
