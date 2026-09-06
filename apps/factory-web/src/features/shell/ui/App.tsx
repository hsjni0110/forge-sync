import { Link, Route, Routes } from "react-router-dom";

import { DashboardRoute } from "../../dashboard/ui/DashboardRoute";
import { FactoryRoute } from "../../factory3d/ui/FactoryRoute";
import type { FactorySceneLoader } from "../../factory3d/ui/factorySceneContract";
import type { TwinSessionFactory } from "../../twin/application/ports";
import type { ReplayControlClient } from "../../replay/application/ports";

const loadFactoryScene: FactorySceneLoader = () =>
  import("../../factory3d/ui/FactoryScene");

export function App({
  twinSessionFactory,
  replayControlClient,
  factorySceneLoader = loadFactoryScene,
}: {
  twinSessionFactory: TwinSessionFactory;
  replayControlClient?: ReplayControlClient;
  factorySceneLoader?: FactorySceneLoader;
}) {
  return (
    <div className="site-shell">
      <a className="skip-link" href="#main-content">
        본문으로 바로가기
      </a>
      <header className="site-header">
        <Link className="brand" to="/">
          ForgeSync
        </Link>
        <nav aria-label="주요 메뉴">
          <Link to="/">대시보드</Link>
          <Link to="/factory">공장 보기</Link>
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
