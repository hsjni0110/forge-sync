import { Link, Route, Routes } from "react-router-dom";

import type { TwinSessionFactory } from "../../twin/application/ports";
import { MachineDetailRoute } from "../../twin/ui/MachineDetailRoute";

export function App({
  twinSessionFactory,
}: {
  twinSessionFactory: TwinSessionFactory;
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
          <Link to="/machines/Mazak01">설비 상세</Link>
        </nav>
      </header>
      <main id="main-content" className="app-shell">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route
            path="/machines/:machineId"
            element={<MachineDetailRoute sessionFactory={twinSessionFactory} />}
          />
          <Route path="*" element={<NotFound />} />
        </Routes>
      </main>
    </div>
  );
}

function Home() {
  return (
    <section className="home-page">
      <p className="eyebrow">제조 운영 디지털 트윈</p>
      <h1>ForgeSync</h1>
      <p>설비의 현재 상태와 데이터 출처를 한눈에 확인하세요.</p>
      <Link className="primary-link" to="/machines/Mazak01">
        Mazak01 설비 상세 보기
      </Link>
    </section>
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
