import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api.js';
import { formatDateTime } from '../format.js';

const RISK_COLORS = { 低: '#2e7d32', 中: '#ef6c00', 高: '#d32f2f', 极高: '#b71c1c' };
const RISK_LABELS = { 低: '低风险', 中: '中风险', 高: '高风险', 极高: '极高风险' };

function TimelineItem({ report, isLast }) {
  const risk = report.riskLevel || '中';
  const score = report.score ?? 0;
  const color = RISK_COLORS[risk] || '#999';
  const drugs = report.medications || [];
  const patient = report.patient || {};

  return (
    <div className={`timeline-item ${isLast ? 'timeline-last' : ''}`}>
      <div className="timeline-dot" style={{ background: color }} />
      <div className="timeline-content">
        <div className="timeline-header">
          <span className="timeline-date">{formatDateTime(report.createdAt)}</span>
          <span className="timeline-risk" style={{ background: `${color}20`, color }}>
            {RISK_LABELS[risk] || risk} · {score}分
          </span>
        </div>
        <div className="timeline-body">
          {patient.conditions?.length > 0 && (
            <p className="timeline-conditions">
              患病：{patient.conditions.join('、')}
            </p>
          )}
          <div className="timeline-drugs">
            {drugs.slice(0, 5).map((m, i) => (
              <span key={i} className="drug-chip">
                {m}
              </span>
            ))}
            {drugs.length > 5 && <span className="drug-chip">+{drugs.length - 5}种</span>}
          </div>
        </div>
        <Link to={`/report/${report.id}`} className="timeline-link">
          查看完整报告 →
        </Link>
      </div>
    </div>
  );
}

export default function DashboardPage() {
  const [reports, setReports] = useState([]);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api('/api/reports?page=1&pageSize=50')
      .then((data) => {
        setReports(data.records || data.items || []);
        setLoading(false);
      })
      .catch((err) => {
        setError(err.message);
        setLoading(false);
      });
  }, []);

  if (loading) {
    return (
      <div className="page-inner">
        <div className="empty-state">
          <h3>加载中…</h3>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="page-inner">
        <div className="empty-state">
          <h3>{error}</h3>
        </div>
      </div>
    );
  }

  if (reports.length === 0) {
    return (
      <div className="page-inner">
        <div className="page-title">
          <h2>我的健康时间线</h2>
          <p>记录你的用药分析历史，追踪风险变化</p>
        </div>
        <div className="empty-state">
          <h3>还没有分析记录</h3>
          <p>
            去<Link to="/home">发起一次用药分析</Link>，开始追踪你的健康变化
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="page-inner">
      <div className="page-title">
        <h2>我的健康时间线</h2>
        <p>记录你的用药分析历史，追踪风险变化（最近 {reports.length} 条）</p>
      </div>

      <div className="timeline">
        {reports.map((report, index) => (
          <TimelineItem key={report.id} report={report} isLast={index === reports.length - 1} />
        ))}
      </div>
    </div>
  );
}
