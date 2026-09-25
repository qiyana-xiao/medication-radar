import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { api, auth } from '../api.js';
import Logo from '../components/Logo.jsx';

export default function LoginPage() {
  const [mode, setMode] = useState('login');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState('patient');
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const path = mode === 'login' ? '/api/auth/login' : '/api/auth/register';
      const body = mode === 'login' ? { username, password } : { username, password, role };
      const data = await api(path, { method: 'POST', body });
      auth.login(data.token);
      localStorage.setItem('mradar_user', JSON.stringify(data.user));
      const fallback = data.user.role === 'admin' ? '/knowledge' : '/home';
      navigate(location.state?.from || fallback, { replace: true });
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-brand">
          <Logo size={44} />
          <h1>用药雷达</h1>
        </div>
        <p className="login-sub">老年多重用药安全分析平台</p>

        <div className="login-tabs">
          <button className={mode === 'login' ? 'active' : ''} onClick={() => setMode('login')}>
            登录
          </button>
          <button className={mode === 'register' ? 'active' : ''} onClick={() => setMode('register')}>
            注册
          </button>
        </div>

        <form onSubmit={submit}>
          <div className="field">
            <label>用户名</label>
            <input
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="2-20 位字母/数字/中文"
              autoComplete="username"
            />
          </div>
          <div className="field">
            <label>密码</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="至少 6 位"
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            />
          </div>
          {mode === 'register' && (
            <div className="field">
              <label>我的身份</label>
              <div className="role-select">
                <label className="role-option">
                  <input type="radio" name="role" value="patient" checked={role === 'patient'} onChange={() => setRole('patient')} />
                  <span>患者 / 家属</span>
                </label>
                <label className="role-option">
                  <input type="radio" name="role" value="doctor" checked={role === 'doctor'} onChange={() => setRole('doctor')} />
                  <span>医生 / 药师</span>
                </label>
              </div>
            </div>
          )}
          <button type="submit" className="submit-btn" disabled={loading}>
            {loading ? '请稍候…' : mode === 'login' ? '登录' : '注册并登录'}
          </button>
          {error && <p className="error">{error}</p>}
        </form>

        <p className="login-hint">未登录也可以直接使用「用药分析」（游客模式不保存历史）</p>
      </div>
    </div>
  );
}
