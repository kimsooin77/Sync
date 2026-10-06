import { Navigate, NavLink, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { useState } from 'react';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Dashboard } from '../features/dashboard/Dashboard';
import { EmployeesPage } from '../features/employees/EmployeesPage';
import { IntegrationsPage } from '../features/integrations/IntegrationsPage';

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
    <div className="brand-mark">ES</div><p className="eyebrow">EMPLOYEE LIFECYCLE SYNC</p>
    <h1>관리자 로그인</h1><p className="muted">직원 동기화와 외부 계정 처리 현황을 관리합니다.</p>
    <label>관리자 아이디<input autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} required /></label>
    <label>비밀번호<input type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required /></label>
    {error && <div className="alert error" role="alert">{error}</div>}
    <button className="primary full" disabled={busy}>{busy ? '확인 중…' : '로그인'}</button>
    <p className="small muted">로그인과 관리자 요청은 세션 및 CSRF 보호를 사용합니다.</p>
  </form></main>;
}

function Shell() {
  const { user, logout, error, clearError } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [busy, setBusy] = useState(false);
  async function signOut() {
    if (busy) return;
    setBusy(true); clearError();
    try { await logout(); navigate('/login', { replace: true }); }
    catch (failure) { if (failure instanceof ApiError && failure.status === 401) navigate('/login', { replace: true }); }
    finally { setBusy(false); }
  }
  return <div className="app-shell">
    <aside className="sidebar">
      <div className="brand"><span className="brand-mark small-mark">ES</span><span>People Ops<small>SYNC CONSOLE</small></span></div>
      <nav aria-label="주 메뉴">
        <NavLink to="/" end className={({ isActive }) => isActive ? 'nav-item active' : 'nav-item'}><span>▦</span> 대시보드</NavLink>
        <NavLink to="/employees" className={({ isActive }) => isActive ? 'nav-item active' : 'nav-item'}><span>♙</span> 직원</NavLink>
        <NavLink to="/integrations" className={({ isActive }) => isActive ? 'nav-item active' : 'nav-item'}><span>⇄</span> 외부 연계</NavLink>
      </nav>
      <div className="sidebar-note"><span className="status-dot" /> 시스템 관리 콘솔</div>
    </aside>
    <section className="main-frame">
      <header className="topbar"><div><span className="crumb">관리자</span><span className="crumb-sep">/</span><span>{location.pathname === '/' ? '대시보드' : location.pathname === '/employees' ? '직원' : '외부 연계'}</span></div>
        <div className="user-menu"><span className="avatar">{user?.username.slice(0, 1).toUpperCase()}</span><span>{user?.username}</span><button className="text-button" disabled={busy} onClick={signOut}>로그아웃</button></div>
      </header>
      {error && <div className="global-error" role="alert">{error}<button onClick={clearError} aria-label="오류 닫기">×</button></div>}
      <main className="page-content"><Routes>
        <Route path="/" element={<Dashboard />} />
        <Route path="/employees" element={<EmployeesPage />} />
        <Route path="/integrations" element={<IntegrationsPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes></main>
      <footer className="footer">Employee Lifecycle Sync <span>관리자 콘솔 · same-origin session</span></footer>
    </section>
  </div>;
}

export function App() {
  const { user, loading } = useAuth();
  // Session state is checked once at launch; no token or password is stored in browser storage.
  if (loading) return <div className="loading-screen">세션 확인 중…</div>;
  if (!user) return <Routes><Route path="/login" element={<LoginPage />} /><Route path="*" element={<LoginPage />} /></Routes>;
  return <Shell />;
}
