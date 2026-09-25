import { useEffect, useRef, useState } from 'react';
import { api, useAuth } from '../api.js';
import Pagination from '../components/Pagination.jsx';

const PMPH_LINK = 'https://pharmacy.pmphai.com/';
const KEY_SOURCE_LABELS = { database: '界面配置', env: '配置文件', none: '未配置' };

function AiKeyCard() {
  const [open, setOpen] = useState(false);
  const [status, setStatus] = useState(null);
  const [keyInput, setKeyInput] = useState('');
  const [msg, setMsg] = useState(null);
  const [err, setErr] = useState(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api('/api/admin/ai-key').then(setStatus).catch(() => {});
  }, []);

  const save = async () => {
    const value = keyInput.trim();
    if (!value) {
      setErr('请先填写密钥');
      return;
    }
    setBusy(true);
    setErr(null);
    setMsg(null);
    try {
      setStatus(await api('/api/admin/ai-key', { method: 'PUT', body: { key: value } }));
      setKeyInput('');
      setMsg('密钥已加密保存，立即生效');
    } catch (e) {
      setErr(e.message);
    } finally {
      setBusy(false);
    }
  };

  const clear = async () => {
    if (!window.confirm('确定清除界面配置的密钥吗？清除后将回退到配置文件中的密钥（如有）。')) return;
    setBusy(true);
    setErr(null);
    setMsg(null);
    try {
      setStatus(await api('/api/admin/ai-key', { method: 'DELETE' }));
      setKeyInput('');
      setMsg('已清除');
    } catch (e) {
      setErr(e.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="ai-key-card">
      <div className="ai-key-head">
        <strong>AI 服务设置</strong>
        <span className={`pill ${status?.configured ? 'pill-green' : 'pill-red'}`}>
          {status == null ? '检测中…' : status.configured ? '已配置' : '未配置'}
        </span>
        {status?.configured && status.masked && (
          <span className="ai-key-masked">
            当前密钥 {status.masked}（{KEY_SOURCE_LABELS[status.source] || status.source}）
          </span>
        )}
        <button className="btn-text" onClick={() => setOpen((v) => !v)}>
          {open ? '收起 ▲' : '配置 ▼'}
        </button>
      </div>
      {open && (
        <div className="ai-key-body">
          <p className="ai-key-hint">
            填写 DeepSeek API Key，用于 AI 用药分析报告生成。密钥经 AES-GCM 加密后保存到数据库，
            不会以明文出现在任何文件或接口中，保存后立即生效、无需重启。
          </p>
          <div className="ai-key-row">
            <input
              className="search-input"
              type="password"
              value={keyInput}
              onChange={(e) => setKeyInput(e.target.value)}
              placeholder="sk-…"
              autoComplete="new-password"
            />
            <button className="btn-primary-sm" disabled={busy} onClick={save}>
              {busy ? '处理中…' : '保存'}
            </button>
            {status?.configured && (
              <button className="btn-text danger" disabled={busy} onClick={clear}>
                清除
              </button>
            )}
          </div>
          {msg && <p className="ai-key-ok">{msg}</p>}
          {err && <p className="error">{err}</p>}
        </div>
      )}
    </div>
  );
}

function DetailModal({ drug, onClose }) {
  const sections = [
    ['基本信息', [
      ['药品名称', drug.name],
      ['别名 / 商品名', (drug.aliases || []).join('、')],
      ['分类', drug.category],
      ['剂型', drug.dosageForm],
      ['规格', drug.specification],
    ]],
    ['成分与性状', [
      ['主要成分', drug.ingredients],
      ['性状', drug.appearance],
    ]],
    ['适应症与用法', [
      ['适应症 / 功能主治', drug.indications],
      ['用法用量', drug.usageDosage],
    ]],
    ['安全性', [
      ['不良反应', drug.adverseReactions],
      ['禁忌', drug.contraindications],
      ['注意事项', drug.precautions],
      ['特殊人群用药', drug.specialPopulations],
    ]],
    ['药理信息', [
      ['药理毒理', drug.pharmacology],
      ['用药监测', drug.monitor],
      ['同类重复用药风险', drug.therapeuticDuplication],
      ['相互作用提示', drug.notes],
    ]],
  ];

  return (
    <div className="modal-mask" onClick={onClose}>
      <div className="modal drug-detail" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <div>
            <h3>{drug.name}</h3>
            <p className="drug-sub">
              {drug.category}
              {drug.dosageForm ? ` · ${drug.dosageForm}` : ''}
              {drug.specification ? ` · ${drug.specification}` : ''}
            </p>
          </div>
          <div className="modal-head-actions">
            <a
              className="nmpa-link"
              href={PMPH_LINK}
              target="_blank"
              rel="noopener noreferrer"
              title="前往人卫用药助手查询该药说明书详情"
            >
              人卫用药助手查此药 ↗
            </a>
            <button className="btn-text" onClick={onClose}>
              关闭 ×
            </button>
          </div>
        </div>

        {sections.map(([title, rows]) => (
          <div key={title} className="detail-section">
            <h4>{title}</h4>
            {rows
              .filter(([, v]) => v && String(v).trim())
              .map(([k, v]) => (
                <div key={k} className="detail-row">
                  <div className="detail-k">{k}</div>
                  <div className="detail-v">{v}</div>
                </div>
              ))}
          </div>
        ))}
      </div>
    </div>
  );
}

export default function KnowledgePage() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'admin';
  const [items, setItems] = useState([]);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(20);
  const [cats, setCats] = useState(['全部']);
  const [interactions, setInteractions] = useState([]);
  const [tab, setTab] = useState('meds');
  const [q, setQ] = useState('');
  const [qInput, setQInput] = useState('');
  const [cat, setCat] = useState('全部');
  const [detail, setDetail] = useState(null);
  const [error, setError] = useState(null);
  const searchTimer = useRef(null);

  useEffect(() => {
    api('/api/medications/stats')
      .then((stats) => {
        const list = stats.map((s) => s.category).filter((c) => c && c !== '未分类');
        setCats(['全部', ...list]);
      })
      .catch(() => {});
    api('/api/medications/interactions')
      .then(setInteractions)
      .catch(() => {});
  }, []);

  useEffect(() => {
    const t = setTimeout(() => setQ(qInput), 350);
    return () => clearTimeout(t);
  }, [qInput]);

  useEffect(() => {
    setPage(1);
  }, [q, cat]);

  useEffect(() => {
    let alive = true;
    const params = new URLSearchParams({ page, pageSize, q, cat });
    api(`/api/medications/page?${params}`)
      .then((res) => {
        if (!alive) return;
        setItems(res.items);
        setTotal(res.total);
        setTotalPages(res.totalPages);
      })
      .catch((err) => alive && setError(err.message));
    return () => {
      alive = false;
    };
  }, [page, pageSize, q, cat]);

  const remove = async (id, name) => {
    const reason = prompt(`确定停用「${name}」吗？请填写停用原因：`);
    if (reason === null) return;
    if (!reason.trim()) {
      alert('请填写停用原因');
      return;
    }
    try {
      await api(`/api/medications/${id}/disable`, {
        method: 'PATCH',
        body: { reason: reason.trim() },
      });
      const params = new URLSearchParams({ page, pageSize, q, cat });
      const res = await api(`/api/medications/page?${params}`);
      setItems(res.items);
      setTotal(res.total);
      setTotalPages(res.totalPages);
    } catch (err) {
      setError(err.message);
    }
  };

  const filteredInters = interactions.filter(
    (it) => !q || it.drugA.includes(q) || it.drugB.includes(q) || (it.effect || '').includes(q)
  );

  return (
    <div className="page-inner">
      <div className="page-title">
        <h2>{isAdmin ? '知识库管理' : '药品知识库'}</h2>
        <p>
          {isAdmin
            ? `共收录 ${total} 种药品档案与 ${interactions.length} 组已知相互作用，可维护药品资料（停用需填写原因，软删除机制）`
            : `共收录 ${total} 种药品档案与 ${interactions.length} 组已知相互作用，仅供查询参考，维护由管理员负责`}
        </p>
        <div className="nmpa-banner">
          <span className="nmpa-tag">官方权威</span>
          <div className="nmpa-banner-body">
            <span className="nmpa-banner-main">
              药品数据对接 <b>人卫用药助手</b>权威药品数据库（人民卫生出版社）
            </span>
            <a className="nmpa-note" href={PMPH_LINK} target="_blank" rel="noopener noreferrer">
              未收录本系统药品可前往人卫用药助手查询说明书详情 <em>（pharmacy.pmphai.com）</em>↗
            </a>
          </div>
        </div>
      </div>

      {isAdmin && <AiKeyCard />}

      <div className="toolbar">
        <div className="cat-chips">
          {cats.map((c) => (
            <button key={c} className={cat === c ? 'active' : ''} onClick={() => setCat(c)}>
              {c}
            </button>
          ))}
        </div>
        <input
          className="search-input"
          value={qInput}
          onChange={(e) => setQInput(e.target.value)}
          placeholder="搜索药名/别名/适应症…"
        />
      </div>

      <div className="toolbar">
        <div className="filter-tabs">
          <button className={tab === 'meds' ? 'active' : ''} onClick={() => setTab('meds')}>
            药品档案（{total}）
          </button>
          <button className={tab === 'inters' ? 'active' : ''} onClick={() => setTab('inters')}>
            相互作用规则（{interactions.length}）
          </button>
        </div>
        <a className="nmpa-portal-btn" href={PMPH_LINK} target="_blank" rel="noopener noreferrer">
          人卫用药助手查药 ↗
        </a>
      </div>

      {error && <p className="error">{error}</p>}

      {tab === 'meds' ? (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>药品名称</th>
                <th>分类</th>
                <th>剂型 / 规格</th>
                <th>适应症</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {items.map((m) => (
                <tr key={m.id} className="clickable-row" onClick={() => setDetail(m)}>
                  <td>
                    <strong>{m.name}</strong>
                    {m.aliases?.length > 0 && <div className="cell-sub">{m.aliases.slice(0, 3).join(' / ')}</div>}
                  </td>
                  <td>{m.category || '—'}</td>
                  <td>
                    {m.dosageForm || '—'}
                    {m.specification ? <div className="cell-sub">{m.specification}</div> : ''}
                  </td>
                  <td className="cell-wide">{(m.indications || '—').slice(0, 60)}{(m.indications || '').length > 60 ? '…' : ''}</td>
                  <td>
                    <button
                      className="btn-text"
                      onClick={(e) => {
                        e.stopPropagation();
                        setDetail(m);
                      }}
                    >
                      详情
                    </button>
                    {isAdmin && (
                      <button
                        className="btn-text danger"
                        onClick={(e) => {
                          e.stopPropagation();
                          remove(m.id, m.name);
                        }}
                      >
                        停用
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {items.length === 0 && <div className="table-empty">无匹配药品</div>}
          <div className="table-footer">
            <span className="pg-info">第 {page} 页 · 共 {totalPages} 页 · {total} 条记录</span>
            <Pagination page={page} totalPages={totalPages} onChange={setPage} />
          </div>
        </div>
      ) : (
        <div className="table-wrap">
          <table className="data-table">
            <thead>
              <tr>
                <th>组合</th>
                <th>严重度</th>
                <th>机制</th>
                <th>后果</th>
                <th>建议</th>
              </tr>
            </thead>
            <tbody>
              {filteredInters.map((it) => (
                <tr key={it.id}>
                  <td>
                    <strong>
                      {it.drugA} + {it.drugB}
                    </strong>
                  </td>
                  <td>
                    <span
                      className={
                        it.severity === '严重'
                          ? 'pill pill-red'
                          : it.severity === '中等'
                            ? 'pill pill-orange'
                            : 'pill pill-blue'
                      }
                    >
                      {it.severity}
                    </span>
                  </td>
                  <td className="cell-wide">{it.mechanism || '—'}</td>
                  <td className="cell-wide">{it.effect || '—'}</td>
                  <td className="cell-wide">{it.recommendation || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {filteredInters.length === 0 && <div className="table-empty">无匹配规则</div>}
        </div>
      )}

      {detail && <DetailModal drug={detail} onClose={() => setDetail(null)} />}
    </div>
  );
}
