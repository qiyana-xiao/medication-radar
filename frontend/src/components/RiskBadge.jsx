import { riskColor } from '../format.js';

export default function RiskBadge({ level, score, size = 'md', note, noteColor }) {
  const color = riskColor(level);
  const label = level === '未收录' ? '未收录' : `${level}风险`;
  return (
    <span className={`risk-badge ${size === 'sm' ? 'risk-badge-sm' : ''}`} style={{ borderColor: `${color}66`, color, background: `${color}14` }}>
      {label}
      {score != null && level !== '未收录' ? ` · ${score}分` : ''}
      {note ? <span style={{ color: noteColor || 'inherit', fontWeight: 400 }}>（{note}）</span> : null}
    </span>
  );
}
