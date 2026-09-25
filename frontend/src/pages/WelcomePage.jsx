import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, useAuth } from '../api.js';

function WelcomeLogo() {
  return (
    <svg viewBox="0 0 120 120" width="132" height="132" aria-label="用药雷达">
      <defs>
        <linearGradient id="wl-cross" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#7db8ff" />
          <stop offset="1" stopColor="#2b8fff" />
        </linearGradient>
      </defs>
      <circle cx="60" cy="60" r="56" fill="none" stroke="rgba(125,184,255,0.55)" strokeWidth="2.5" />
      <circle cx="60" cy="60" r="56" fill="none" stroke="rgba(125,184,255,0.18)" strokeWidth="7" />
      <circle cx="60" cy="60" r="40" fill="none" stroke="rgba(125,184,255,0.4)" strokeWidth="1.6" />
      <circle cx="60" cy="60" r="25" fill="none" stroke="rgba(125,184,255,0.28)" strokeWidth="1.2" />
      <path
        d="M53 42h14v11h11v14H67v11H53V67H42V53h11z"
        fill="url(#wl-cross)"
        stroke="rgba(255,255,255,0.35)"
        strokeWidth="1"
      />
      <line x1="60" y1="60" x2="108" y2="22" stroke="rgba(125,184,255,0.5)" strokeWidth="1.4" strokeDasharray="3 3" />
      <circle cx="103" cy="18" r="6.5" fill="#ff5252">
        <animate attributeName="opacity" values="1;0.55;1" dur="2s" repeatCount="indefinite" />
      </circle>
      <circle cx="26" cy="88" r="3.5" fill="#ffd54f" />
      <circle cx="97" cy="80" r="2.6" fill="#4dffb8" />
    </svg>
  );
}

export default function WelcomePage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [stats, setStats] = useState({ drugs: 0, interactions: 0, known: 18 });

  useEffect(() => {
    api('/api/medications/stats')
      .then((rows) => setStats((s) => ({ ...s, drugs: rows.reduce((a, r) => a + r.n, 0) })))
      .catch(() => {});
    api('/api/medications/interactions')
      .then((rows) => setStats((s) => ({ ...s, interactions: rows.length })))
      .catch(() => {});
  }, []);

  const enter = () => navigate(user ? '/home' : '/login');

  return (
    <div className="welcome">
      <div className="welcome-grid" />
      <div className="welcome-rings">
        <div className="radar-sweep" />
        <span className="radar-dot d1" />
        <span className="radar-dot d2" />
        <span className="radar-dot d3" />
      </div>

      <div className="welcome-core">
        <div className="welcome-badge">MEDICATION SAFETY RADAR</div>
        <div className="welcome-logo">
          <WelcomeLogo />
        </div>
        <h1 className="welcome-title">用药雷达</h1>
        <p className="welcome-subtitle">老年多重用药安全分析平台</p>
        <div className="welcome-divider" />

        <div className="welcome-stats">
          <div className="welcome-stat">
            <b>{stats.drugs > 0 ? `${stats.drugs}+` : '—'}</b>
            <span>药品档案</span>
          </div>
          <div className="welcome-stat">
            <b>{stats.interactions > 0 ? `${stats.interactions}` : '—'}</b>
            <span>已知相互作用</span>
          </div>
          <div className="welcome-stat">
            <b>AI</b>
            <span>智能风险报告</span>
          </div>
          <div className="welcome-stat">
            <b>人卫助手</b>
            <span>权威药品数据</span>
          </div>
        </div>

        <button className="welcome-enter" onClick={enter}>
          进入系统
        </button>
        <p className="welcome-hint">点击进入 · 为家中长辈做一次用药安全体检</p>
      </div>

      <div className="welcome-footer">
        用药雷达 · 本平台报告由 AI 生成，仅供用药安全参考，不构成诊疗意见 · 请务必咨询医生或药师
      </div>
    </div>
  );
}
