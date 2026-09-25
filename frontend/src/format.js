export function formatDateTime(value) {
  if (!value) return '';
  const s = String(value).replace('T', ' ');
  return s.length >= 16 ? s.slice(0, 16) : s;
}

export const RISK_COLORS = { 低: '#2e7d32', 中: '#ef6c00', 高: '#d32f2f', 极高: '#b71c1c', 未收录: '#6b7280' };

export function riskColor(level) {
  return RISK_COLORS[level] || '#6b7280';
}
