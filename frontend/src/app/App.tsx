import { Link, Navigate, NavLink, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { BrandSymbol } from '../components/BrandSymbol';
import { Dashboard } from '../features/dashboard/Dashboard';
import { EmployeesPage } from '../features/employees/EmployeesPage';
import { IntegrationsPage } from '../features/integrations/IntegrationsPage';
import { EmployeeDetailPage } from '../features/employees/EmployeeDetailPage';
import { IntegrationDetailPage } from '../features/integrations/IntegrationDetailPage';
import { DemoSettings } from '../features/demo/DemoSettings';
import { DemoProvider } from '../features/demo/DemoContext';

function LoginPage() {
  const { user, login, error, clearError } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  if (user) return <Navigate to="/" replace />;
  async function submit(event: React.FormEvent) {
    event.preventDefault(); clearError(); setBusy(true);
    try { await login(username, password); navigate('/', { replace: true }); }
    catch { /* context presents a safe message */ }
    finally { setBusy(false); }
  }
  return <main className="login-page"><form className="login-card" onSubmit={submit}>
    <BrandSymbol className="login-brand-symbol" /><p className="eyebrow">EMPLOYEE LIFECYCLE SYNC</p>
    <h1>관리자 로그인</h1><p className="muted">직원 동기화와 외부 계정 처리 현황을 관리합니다.</p>
    <div className="demo-credentials"><p>데모 계정 <strong>admin</strong> / <strong>1q2w3e4r!@#$</strong></p><button className="text-button" type="button" onClick={() => { setUsername('admin'); setPassword('1q2w3e4r!@#$'); clearError(); }}>데모 계정 입력</button></div>
    <label>관리자 아이디<input autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} required /></label>
    <label>비밀번호<input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required /></label>
    {error && <div className="alert error" role="alert">{error}</div>}
    <button className="primary full" disabled={busy}>{busy ? '확인 중…' : '로그인'}</button>
  </form></main>;
}

function Shell() {
  const { user, logout, error, clearError } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [busy, setBusy] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  async function signOut() {
    if (busy) return;
    setBusy(true); clearError();
    try { await logout(); navigate('/login', { replace: true }); }
    catch (failure) { if (failure instanceof ApiError && failure.status === 401) navigate('/login', { replace: true }); }
    finally { setBusy(false); }
  }
  return <div className={sidebarCollapsed ? 'app-shell sidebar-collapsed' : 'app-shell'}>
    <aside className="sidebar" data-testid="app-sidebar">
      <div className="brand">
        <Link className="brand-link" to="/" aria-label="대시보드로 이동" data-testid="brand-dashboard-link">
          <BrandSymbol />
          <span className="brand-name">Employee Sync</span>
        </Link>
      </div>
      <nav id="primary-navigation" aria-label="주 메뉴">
        <NavLink to="/" end aria-label="대시보드" title="대시보드" className={({ isActive }) => isActive ? 'nav-item active' : 'nav-item'}><span className="nav-icon" aria-hidden="true"><svg viewBox="0 0 24 24" focusable="false"><rect x="3.5" y="3.5" width="7" height="7" rx="1"/><rect x="13.5" y="3.5" width="7" height="7" rx="1"/><rect x="3.5" y="13.5" width="7" height="7" rx="1"/><rect x="13.5" y="13.5" width="7" height="7" rx="1"/></svg></span><span className="nav-label">대시보드</span></NavLink>
        <span className="nav-divider" aria-hidden="true" />
        <NavLink to="/employees" aria-label="직원 목록" title="직원 목록" className={({ isActive }) => isActive ? 'nav-item active' : 'nav-item'}><span className="nav-icon" aria-hidden="true"><svg viewBox="0 0 24 24" focusable="false"><circle cx="12" cy="7.5" r="3.5"/><path d="M5 20v-1.5a7 7 0 0 1 14 0V20z"/></svg></span><span className="nav-label">직원 목록</span></NavLink>
        <span className="nav-divider" aria-hidden="true" />
        <NavLink to="/integrations" aria-label="외부 연계" title="외부 연계" className={({ isActive }) => isActive ? 'nav-item active' : 'nav-item'}><span className="nav-icon"><svg data-testid="integration-nav-icon" data-icon="connection" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" focusable="false" aria-hidden="true"><path d="m10 13.5-1.2 1.2a3 3 0 0 1-4.2-4.2l3-3a3 3 0 0 1 4.2 0M14 10.5l1.2-1.2a3 3 0 0 1 4.2 4.2l-3 3a3 3 0 0 1-4.2 0M9 15l6-6" /></svg></span><span className="nav-label">외부 연계</span></NavLink>
        <span className="nav-divider" aria-hidden="true" />
        <NavLink to="/scenarios" end aria-label="연계 시나리오" title="연계 시나리오" className={({ isActive }) => isActive ? 'nav-item active' : 'nav-item'}><span className="nav-icon" aria-hidden="true"><svg viewBox="0 0 24 24" focusable="false"><circle cx="12" cy="12" r="3"/><path d="m19.4 15 .1.1a1.7 1.7 0 1 1-2.4 2.4l-.1-.1a1.7 1.7 0 0 0-2.9 1.2v.2a1.7 1.7 0 1 1-3.4 0v-.2a1.7 1.7 0 0 0-2.9-1.2l-.1.1a1.7 1.7 0 1 1-2.4-2.4l.1-.1a1.7 1.7 0 0 0-1.2-2.9H4a1.7 1.7 0 1 1 0-3.4h.2a1.7 1.7 0 0 0 1.2-2.9l-.1-.1a1.7 1.7 0 1 1 2.4-2.4l.1.1a1.7 1.7 0 0 0 2.9-1.2V2a1.7 1.7 0 1 1 3.4 0v.2a1.7 1.7 0 0 0 2.9 1.2l.1-.1a1.7 1.7 0 1 1 2.4 2.4l-.1.1a1.7 1.7 0 0 0 1.2 2.9h.2a1.7 1.7 0 1 1 0 3.4h-.2a1.7 1.7 0 0 0-1.2 2.9Z"/></svg></span><span className="nav-label">연계 시나리오</span></NavLink>
      </nav>
    </aside>
    <DemoProvider><section className="main-frame">
      <header className="topbar">
        <button className="sidebar-toggle" type="button" aria-label={sidebarCollapsed ? '메뉴 펼치기' : '메뉴 접기'} title={sidebarCollapsed ? '메뉴 펼치기' : '메뉴 접기'} aria-expanded={!sidebarCollapsed} aria-controls="primary-navigation" onClick={() => setSidebarCollapsed((value) => !value)}>
          <svg aria-hidden="true" viewBox="0 0 24 24" focusable="false">
            {sidebarCollapsed
              ? <path data-testid="sidebar-toggle-chevron-right" d="m9 18 6-6-6-6" />
              : <path data-testid="sidebar-toggle-chevron-left" d="m15 18-6-6 6-6" />}
          </svg>
        </button>
        <div className="breadcrumb"><span className="crumb">관리자</span><span className="crumb-sep">/</span><span>{location.pathname === '/' ? '대시보드' : location.pathname === '/scenarios' ? '연계 시나리오' : /^\/employees\/[^/]+$/.test(location.pathname) ? '직원 상세' : location.pathname === '/employees' ? '직원' : /^\/integrations\/[^/]+$/.test(location.pathname) ? `작업 #${location.pathname.split('/').pop()}` : '외부 연계'}</span></div>
        <div className="topbar-spacer" />
        <div className="user-menu"><span className="avatar">{user?.username.slice(0, 1).toUpperCase()}</span><span>{user?.username}</span><button className="text-button" disabled={busy} onClick={signOut}>로그아웃</button></div>
      </header>
      {error && <div className="global-error" role="alert">{error}<button onClick={clearError} aria-label="오류 닫기">×</button></div>}
      <main className="page-content"><Routes>
        <Route path="/" element={<Dashboard />} />
        <Route path="/employees" element={<EmployeesPage />} />
        <Route path="/employees/:employeeId" element={<EmployeeDetailPage />} />
        <Route path="/integrations" element={<IntegrationsPage />} />
        <Route path="/integrations/:taskId" element={<IntegrationDetailPage />} />
        <Route path="/scenarios" element={<DemoSettings />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes></main>
      <footer className="footer">Employee Lifecycle Sync <span>직원 정보 동기화 · 관리자 화면</span></footer>
    </section></DemoProvider>
  </div>;
}

export function App() {
  const { user, loading } = useAuth();
  // Session state is checked once at launch; no token or password is stored in browser storage.
  if (loading) return <div className="loading-screen">세션 확인 중…</div>;
  if (!user) return <Routes><Route path="/login" element={<LoginPage />} /><Route path="*" element={<LoginPage />} /></Routes>;
  return <Shell />;
}
