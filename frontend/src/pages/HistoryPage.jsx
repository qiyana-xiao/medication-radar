import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, useAuth } from '../api.js';
import { formatDateTime } from '../format.js';
import RiskBadge from '../components/RiskBadge.jsx';

export default function HistoryPage() {
  const { user } = useAuth();
  const isDoctor = user?.role === 'doctor';
  const [data, setData] = useState({ items: [], total: 0 });
  const [page, setPage] = useState(1);
  const [filter, setFilter] = useState('all');
  const [q, setQ] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const navigate = useNavigate();

  const load = async (p = 1, replace = false) => {
    setLoading(true);
    try {
      const params = new URLSearchParams({ page: p, pageSize: 10, filter, q });
      const res = await api(`/api/reports?${params}`);
      setData((old) => ({
        total: res.total,
        items: replace ? res.items : [...old.items, ...res.items],
      }));
      setPage(p);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    setPage(1);
    load(1, true);
  }, [filter]);

  const search = (e) => {
    e.preventDefault();
    setPage(1);
    load(1, true);
  };

  const toggleFavorite = async (id) => {
    try {
      await api(`/api/reports/${id}/favorite`, { method: 'POST' });
      setData((old) => ({
        ...old,
        items: old.items.map((it) => (it.id === id ? { ...it, favorite: !it.favorite } : it)),
      }));
    } catch (err) {
      setError(err.message);
    }
  };

  const remove = async (id) => {
    if (!confirm('确定删除这份报告吗？')) return;
    try {
      await api(`/api/reports/${id}`, { method: 'DELETE' });
      setData((old) => ({ ...old, total: old.total - 1, items: old.items.filter((it) => it.id !== id) }));
    } catch (err) {
      setError(err.message);
    }
  };

  // 医生端：按患者标识分组展示
  const groups = useMemo(() => {
    if (!isDoctor) return [];
    const map = new Map();
    for (const it of data.items) {
      const key = it.patient?.label?.trim() || '未标注患者';
      if (!map.has(key)) map.set(key, []);
      map.get(key).push(it);
    }
    return [...map.entries()];
  }, [data.items, isDoctor]);

  const renderCard = (it) => (
    <div key={it.id} className="history-card">
      <div className="history-main" onClick={() => navigate(`/report/${it.id}`)}>
        <div className="history-top">
          <RiskBadge level={it.riskLevel} score={it.score} />
          <span className="history-date">{formatDateTime(it.createdAt)}</span>
        </div>
        <div className="history-meta">
          {isDoctor && it.patient?.label?.trim() && (
            <span className="patient-label-chip">{it.patient.label.trim()}</span>
          )}
          {it.patient?.age ? `${it.patient.age}岁` : '年龄未知'}
          {it.patient?.gender ? ` · ${it.patient.gender}` : ''}
        </div>
        <div className="history-drugs">
          {it.medications.map((d, i) => (
            <span key={i} className="drug-chip">
              {d}
            </span>
          ))}
        </div>
      </div>
      <div className="history-actions">
        <button title={it.favorite ? '取消收藏' : '收藏'} onClick={() => toggleFavorite(it.id)}>
          {it.favorite ? '★' : '☆'}
        </button>
        <button onClick={() => navigate(`/report/${it.id}`)}>查看</button>
        <button className="danger" onClick={() => remove(it.id)}>
          删除
        </button>
      </div>
    </div>
  );

  return (
    <div className="page-inner">
      <div className="page-title">
        <h2>{isDoctor ? '分析记录' : '历史报告'}</h2>
        <p>
          {isDoctor
            ? `共 ${data.total} 份用药分析记录，按患者标识分组`
            : `共 ${data.total} 份分析报告`}
        </p>
      </div>

      <div className="toolbar">
        <div className="filter-tabs">
          {[
            ['all', '全部'],
            ['high', '高风险'],
            ['favorite', '收藏'],
          ].map(([key, label]) => (
            <button
              key={key}
              className={filter === key ? 'active' : ''}
              onClick={() => setFilter(key)}
            >
              {label}
            </button>
          ))}
        </div>
        <form className="search-box" onSubmit={search}>
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="按药物名称搜索…"
          />
          <button type="submit">搜索</button>
        </form>
      </div>

      {error && <p className="error">{error}</p>}

      {loading && data.items.length === 0 ? (
        <div className="empty-state">
          <h3>加载中…</h3>
        </div>
      ) : data.items.length === 0 ? (
        <div className="empty-state">
          <h3>还没有{isDoctor ? '分析记录' : '分析报告'}</h3>
          <p>
            去<Link to="/home">用药分析</Link>页{isDoctor ? '为患者生成第一份分析报告' : '生成第一份报告'}吧
          </p>
        </div>
      ) : isDoctor ? (
        <div className="history-groups">
          {groups.map(([label, items]) => (
            <div key={label} className="patient-group">
              <div className="group-header">
                <span className="group-icon">👤</span>
                <span className="group-name">{label}</span>
                <span className="group-count">{items.length} 份记录</span>
              </div>
              <div className="history-list">
                {items.map(renderCard)}
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="history-list">
          {data.items.map(renderCard)}
        </div>
      )}

      {data.items.length < data.total && (
        <button className="ghost-btn" onClick={() => load(page + 1)} disabled={loading}>
          {loading ? '加载中…' : '加载更多'}
        </button>
      )}
    </div>
  );
}
