import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { api, useAuth } from '../api.js';
import Report from '../components/Report.jsx';
import RiskBadge from '../components/RiskBadge.jsx';
import { formatDateTime } from '../format.js';

export default function ReportDetailPage() {
  const { id } = useParams();
  const { user } = useAuth();
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [seq, setSeq] = useState(null);

  useEffect(() => {
    api(`/api/reports/${id}`)
      .then(setData)
      .catch((err) => setError(err.message));
  }, [id]);

  useEffect(() => {
    let alive = true;
    api('/api/reports?page=1&pageSize=50')
      .then((list) => {
        if (!alive) return;
        const idx = (list.items || []).findIndex((it) => String(it.id) === String(id));
        if (idx >= 0) setSeq(list.total - idx);
      })
      .catch(() => {});
    return () => { alive = false; };
  }, [id]);

  const isDoctor = user?.role === 'doctor';
  const backLabel = isDoctor ? '分析记录' : '历史报告';

  if (error) {
    return (
      <div className="page-inner">
        <div className="empty-state">
          <h3>{error}</h3>
          <p>
            返回<Link to="/history">{backLabel}</Link>
          </p>
        </div>
      </div>
    );
  }
  if (!data) {
    return (
      <div className="page-inner">
        <div className="empty-state">
          <h3>加载中…</h3>
        </div>
      </div>
    );
  }

  const p = data.patient || {};
  const v = p.vitals || {};
  const rep = data.report || {};
  const cautionFindings =
    (rep.patientWarnings?.length || 0) +
    (rep.overdoseWarnings?.length || 0) +
    (rep.duplicates?.length || 0) +
    (rep.interactions?.length || 0);
  const hasCautionNote = data.riskLevel === '低' && cautionFindings > 0;
  const vitalsText = [
    v.systolic && v.diastolic ? `血压 ${v.systolic}/${v.diastolic} mmHg` : '',
    v.heartRate ? `心率 ${v.heartRate} 次/分` : '',
    v.fastingGlucose ? `空腹血糖 ${v.fastingGlucose} mmol/L` : '',
    v.creatinine ? `血肌酐 ${v.creatinine} μmol/L` : '',
    v.weight ? `体重 ${v.weight}kg` : '',
  ]
    .filter(Boolean)
    .join(' · ');

  return (
    <div className="page-inner">
      <div className="page-title">
        <div className="detail-head">
          <Link to="/history" className="back-btn">
            <span className="back-arrow">←</span> 返回{backLabel}
          </Link>
          <span className="detail-head-right">
            <RiskBadge
              level={data.riskLevel}
              score={data.score}
              note={hasCautionNote ? '有需关注项' : ''}
              noteColor={hasCautionNote ? '#ef6c00' : undefined}
            />
            <span className="history-date">{formatDateTime(data.createdAt)}</span>
          </span>
        </div>
        <h2>{seq ? `第 ${seq} 份分析报告` : '分析报告'}</h2>
        <p>
          {isDoctor && p.label?.trim() && <span className="patient-label-chip">{p.label.trim()}</span>}
          患者：{p.age || '未知'}岁 · {p.gender || '未知'}
          {p.conditions?.length ? ` · 患病：${p.conditions.join('、')}` : ''}
          {p.liver ? ` · 肝功能${p.liver}` : ''}
          {p.kidney ? ` · 肾功能${p.kidney}` : ''}
        </p>
        {vitalsText && <p className="detail-vitals">生命体征：{vitalsText}</p>}
        <div className="history-drugs">
          {data.medications.map((m, i) => (
            <span key={i} className="drug-chip">
              {m.name}
              {m.dose ? `（${m.dose}）` : ''}
            </span>
          ))}
        </div>
      </div>
      <Report report={data.report} userRole={user?.role} />
    </div>
  );
}
