import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { api, useAuth, auth } from '../api.js';
import Logo from './Logo.jsx';

const ROLE_LABEL = { patient: '患者', doctor: '医生', admin: '管理员' };

export default function Layout() {
  const { user } = useAuth();
  const navigate = useNavigate();

  const logout = async () => {
    try {
      await api('/api/auth/logout', { method: 'POST' });
    } finally {
      auth.logout();
      localStorage.removeItem('mradar_user');
      navigate('/login');
    }
  };

  const isAdmin = user?.role === 'admin';
  const isDoctor = user?.role === 'doctor';
  const isPatientOrDoctor = user?.role === 'patient' || user?.role === 'doctor';

  return (
    <div className="shell">
      <header className="navbar">
        <div className="navbar-inner">
          <NavLink to="/" className="brand">
            <Logo size={26} /> 用药雷达
          </NavLink>
          <nav className="nav-links">
            {!isAdmin && (
              <>
                <NavLink to="/home">用药分析</NavLink>
                <NavLink to="/history">{isDoctor ? '分析记录' : '历史报告'}</NavLink>
                {!isDoctor && <NavLink to="/dashboard">健康时间线</NavLink>}
                {isDoctor && <NavLink to="/knowledge">药品知识库</NavLink>}
              </>
            )}
            {isAdmin && <NavLink to="/knowledge">知识库管理</NavLink>}
            <a
              href="https://pharmacy.pmphai.com/"
              target="_blank"
              rel="noreferrer"
              className="nav-external"
              title="人卫用药助手——人民卫生出版社权威药品说明书数据库"
            >
              人卫用药助手查药 ↗
            </a>
          </nav>
          <div className="nav-user">
            {user ? (
              <>
                <span className="user-badge">
                  {user.username}
                  <em>{ROLE_LABEL[user.role] || user.role}</em>
                </span>
                <button className="btn-text" onClick={logout}>
                  退出
                </button>
              </>
            ) : (
              <NavLink to="/login" className="btn-primary-sm">
                登录 / 注册
              </NavLink>
            )}
          </div>
        </div>
      </header>
      <main className="page">
        <Outlet />
      </main>
      <footer className="footer">
        用药雷达 · 本平台报告由 AI 生成，仅供用药安全参考，不构成诊疗意见 · 请务必咨询医生或药师
      </footer>
    </div>
  );
}
