// 品牌 logo：雷达扫描环 + 医疗十字 + 红色风险探测点
export default function Logo({ size = 26 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" aria-label="用药雷达">
      <defs>
        <linearGradient id="logo-sweep" x1="24" y1="24" x2="40" y2="10" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#1570ef" stopOpacity="0.4" />
          <stop offset="1" stopColor="#1570ef" stopOpacity="0.04" />
        </linearGradient>
      </defs>
      <circle cx="24" cy="24" r="21" stroke="#1570ef" strokeWidth="2.5" />
      <circle cx="24" cy="24" r="13" stroke="#1570ef" strokeWidth="1.6" opacity="0.5" />
      <circle cx="24" cy="24" r="5.5" stroke="#1570ef" strokeWidth="1.4" opacity="0.35" />
      <line x1="24" y1="3.5" x2="24" y2="44.5" stroke="#1570ef" strokeWidth="1.1" opacity="0.35" />
      <line x1="3.5" y1="24" x2="44.5" y2="24" stroke="#1570ef" strokeWidth="1.1" opacity="0.35" />
      <path d="M24 24 L24 3 A21 21 0 0 1 42.2 13.5 Z" fill="url(#logo-sweep)" />
      <path d="M21 15 h6 v6 h6 v6 h-6 v6 h-6 v-6 h-6 v-6 h6 z" fill="#1570ef" />
      <circle cx="37.5" cy="10.5" r="5.5" fill="#d32f2f" opacity="0.22" />
      <circle cx="37.5" cy="10.5" r="3" fill="#d32f2f" />
    </svg>
  );
}