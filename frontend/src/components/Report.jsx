import { formatDateTime } from '../format.js';

const PMPH_LINK = 'https://pharmacy.pmphai.com/';

function Gauge({ score, riskLevel, caution }) {
  const s = Math.round(Number(score) || 0);
  const unrecognized = riskLevel === '未收录';
  const color = unrecognized ? '#6b7280' : s >= 86 ? '#b91c1c' : s >= 61 ? '#ea580c' : s >= 31 ? '#ca8a04' : '#15803d';
  const offset = 283 - (283 * s) / 100;
  return (
    <div className="gauge">
      <svg viewBox="0 0 120 120">
        <circle cx="60" cy="60" r="45" stroke="#e5e7eb" strokeWidth="10" fill="none" />
        <circle
          cx="60" cy="60" r="45"
          stroke={color} strokeWidth="10" fill="none"
          strokeDasharray="283" strokeDashoffset={offset}
          transform="rotate(-90 60 60)"
          strokeLinecap="round"
        />
      </svg>
      <div className="gauge-value" style={{ color }}>
        {unrecognized ? (
          <div>无法评估</div>
        ) : (
          <>
            <div>{s}</div>
            <div>风险分</div>
          </>
        )}
      </div>
      {caution && !unrecognized && s <= 30 && (
        <span className="gauge-caution">有需关注项</span>
      )}
    </div>
  );
}

function SeverityBadge({ severity }) {
  const map = {
    严重: 'sev-critical',
    中等: 'sev-moderate',
    轻微: 'sev-minor',
    需关注: 'sev-watch',
  };
  return <span className={`severity-badge ${map[severity] || 'sev-watch'}`}>{severity || '—'}</span>;
}

function describeVitals(vitals) {
  if (!vitals) return [];
  const descriptions = [];
  const v = vitals;

  if (v.systolic && v.diastolic) {
    const s = Number(v.systolic);
    const d = Number(v.diastolic);
    if (s >= 180 || d >= 110) descriptions.push({ text: '血压很高（3级高血压）', level: 'danger' });
    else if (s >= 160 || d >= 100) descriptions.push({ text: '血压偏高（2级高血压）', level: 'warning' });
    else if (s >= 140 || d >= 90) descriptions.push({ text: '血压略高（1级高血压）', level: 'info' });
    else if (s < 90 || d < 60) descriptions.push({ text: '血压偏低', level: 'warning' });
    else descriptions.push({ text: '血压正常', level: 'normal' });
  }

  if (v.fastingGlucose) {
    const g = Number(v.fastingGlucose);
    if (g >= 11.1) descriptions.push({ text: '血糖很高', level: 'danger' });
    else if (g >= 7.0) descriptions.push({ text: '血糖偏高', level: 'warning' });
    else if (g >= 6.1) descriptions.push({ text: '血糖略高', level: 'info' });
    else if (g < 3.9) descriptions.push({ text: '血糖偏低', level: 'danger' });
    else descriptions.push({ text: '血糖正常', level: 'normal' });
  }

  if (v.heartRate) {
    const hr = Number(v.heartRate);
    if (hr > 100) descriptions.push({ text: '心跳偏快', level: 'warning' });
    else if (hr < 50) descriptions.push({ text: '心跳偏慢', level: 'warning' });
    else descriptions.push({ text: '心跳正常', level: 'normal' });
  }

  if (v.weight) {
    descriptions.push({ text: `体重 ${v.weight} kg`, level: 'neutral' });
  }

  if (v.creatinine) {
    const c = Number(v.creatinine);
    if (c > 133) descriptions.push({ text: '肾功能指标偏高', level: 'warning' });
    else descriptions.push({ text: '肾功能指标正常', level: 'normal' });
  }

  if (v.inr) {
    const i = Number(v.inr);
    if (i >= 4) descriptions.push({ text: `INR ${v.inr} 明显偏高（出血风险）`, level: 'danger' });
    else if (i >= 3) descriptions.push({ text: `INR ${v.inr} 偏高`, level: 'warning' });
    else descriptions.push({ text: `INR ${v.inr}`, level: 'neutral' });
  }

  if (v.potassium) {
    const k = Number(v.potassium);
    if (k >= 6) descriptions.push({ text: `血钾 ${v.potassium} 很高（心律失常风险）`, level: 'danger' });
    else if (k >= 5.5) descriptions.push({ text: `血钾 ${v.potassium} 偏高`, level: 'warning' });
    else if (k < 3.5) descriptions.push({ text: `血钾 ${v.potassium} 偏低`, level: 'warning' });
    else descriptions.push({ text: '血钾正常', level: 'normal' });
  }

  return descriptions;
}

// 医生版行动徽章：决策式表述，替代患者向的"去咨询谁"指引
const DOCTOR_ACTION = {
  '无法评估': '无法评估',
  '可以按说明书自行服用': '常规用量可用',
  '出现危险信号，立即就医': '需紧急评估处置',
  '建议先问医生或药师': '信息不足，建议核实关键指标后决策',
  '建议由药师或医师复核后使用': '建议复核后使用',
  '不建议使用，请咨询医生更换药物': '不建议使用，建议更换药物',
};

// 医生版文案清洗：去掉受众错位的"咨询医生/药师"类前缀，只调整措辞不改医学事实
function sanitizeForDoctor(text) {
  if (!text) return text;
  return String(text)
    .replace(/先咨询医生或药师[，,]?/g, '')
    .replace(/建议咨询医生或药师[后後]?[再]?/g, '结合临床评估')
    .replace(/请咨询医生或药师/g, '请结合临床评估')
    .replace(/咨询医生或药师/g, '结合临床评估')
    .replace(/请务必咨询医生或药师/g, '请结合临床判断')
    .replace(/及时就医/g, '及时就诊评估');
}

// 旧报告无 doctorNote 时的兜底：把患者口吻的提示转成医生视角（您→患者）
function toDoctorPerspective(text) {
  if (!text) return text;
  return sanitizeForDoctor(String(text))
    .replace(/加用任何新药前应告知医生[，,]?\s*以便综合评估[。.]?/g, '加用新药时建议综合评估对现有方案的影响')
    .replace(/告知医生/g, '结合临床评估')
    .replace(/您的/g, '患者的')
    .replace(/您/g, '患者');
}

export default function Report({ report, userRole }) {
  if (!report) return null;

  const {
    overall = {}, summary = {}, interactions = [], duplicates = [], patientWarnings = [],
    dietLifestyle = {}, suggestions = [], disclaimer, overdoseWarnings = [], missingInfo = [],
    dangerSignals = [], _meta = {},
  } = report;

  const unresolved = _meta.unresolvedMedications || [];
  const externalMeds = _meta.externalMedications || [];
  const contraItems = overall.contraindicationItems || [];
  const isDoctor = userRole === 'doctor';
  const isHighRisk = overall.riskLevel === '高' || overall.riskLevel === '极高';
  const vitalsDescriptions = describeVitals(_meta.vitals);
  const symptoms = _meta.symptoms || [];
  const cautionFindings =
    patientWarnings.length + overdoseWarnings.length + duplicates.length + interactions.length;
  const hasCaution = overall.riskLevel === '低' && cautionFindings > 0;

  const actionColor = {
    '无法评估': '#6b7280',
    '可以按说明书自行服用': '#2e7d32',
    '可以吃，但需注意××': '#ef6c00',
    '建议先问医生或药师': '#1570ef',
    '出现危险信号，立即就医': '#d32f2f',
    '建议由药师或医师复核后使用': '#ef6c00',
    '不建议使用，请咨询医生更换药物': '#d32f2f',
  };

  const rawAction = overall.riskLevel === '未收录'
    ? '无法评估'
    : overall.contraindicated
      ? '不建议使用，请咨询医生更换药物'
      : (isHighRisk ? '建议由药师或医师复核后使用' : (overall.action || '建议先问医生或药师'));
  const doctorAction = DOCTOR_ACTION[rawAction] || '建议结合临床判断';
  const displayAction = isDoctor ? doctorAction : rawAction;
  const actionTextColor = actionColor[rawAction] || '#1570ef';
  const headlineText = isDoctor ? sanitizeForDoctor(overall.headline) : overall.headline;

  return (
    <div className="report">
      <div className="report-toolbar no-print">
        <button
          className="print-btn"
          onClick={() => window.print()}
          title={isDoctor ? '打印本报告或另存为 PDF，用于归档或会诊参考' : '打印本报告或另存为 PDF，方便带给医生/药师查看'}
        >
          打印报告 / 存为 PDF
        </button>
      </div>
      {vitalsDescriptions.length > 0 && (
        <section className="block vitals-summary">
          <h3>{isDoctor ? '患者生命体征' : '您的身体状况'}</h3>
          <div className="vitals-tags">
            {vitalsDescriptions.map((desc, i) => (
              <span key={i} className={`vital-tag vital-${desc.level}`}>
                {desc.text}
              </span>
            ))}
          </div>
        </section>
      )}

      {symptoms.length > 0 && (
        <section className="block vitals-summary">
          <h3>{isDoctor ? '患者当前症状' : '当前症状'}</h3>
          <div className="vitals-tags">
            {symptoms.map((s, i) => (
              <span key={i} className="vital-tag vital-neutral">{s}</span>
            ))}
          </div>
        </section>
      )}

      {overdoseWarnings.length > 0 && (
        <section className="block alert-block overdose-alert">
          <h3>⚠ 同成分超量警告</h3>
          {overdoseWarnings.map((w, i) => (
            <div key={i} className="alert-item">
              <p><b>{w.ingredient}</b>：累计 {w.dailyTotalMg}mg，超过日最大量 {w.maxDailyMg}mg</p>
              <p>涉及药物：{w.drugNames?.join('、')}</p>
              <p className="alert-source">来源：{w.source}</p>
            </div>
          ))}
        </section>
      )}

      {unresolved.length > 0 && (
        <section className="block alert-block unresolved-alert">
          <h3>⚠ 以下药物暂无法评估</h3>
          <p>{isDoctor ? '请核对药名：' : '请核对药名或咨询药师：'}{unresolved.join('、')}</p>
          <p className="alert-note">本地知识库未收录该药，AI 外部检索也无法可靠确认，本次未评估其风险（不会误报为"无风险"）。</p>
          <div className="nmpa-links">
            <a href={PMPH_LINK} target="_blank" rel="noreferrer" className="nmpa-link">
              前往人卫用药助手核实药名与说明书 ↗
            </a>
          </div>
        </section>
      )}

      {externalMeds.length > 0 && (
        <section className="block alert-block external-alert">
          <h3>⚡ 本报告包含 AI 外部检索分析</h3>
          <p>以下药物未收录在本地药品知识库中，相关分析由 AI 基于公开权威药品信息（说明书级别资料）检索生成，未经本地规则库核对：</p>
          <ul className="external-drug-list">
            {externalMeds.map((m, i) => (
              <li key={i}>
                <b>{m.name}</b>
                {m.composition ? `（主要成分：${m.composition}）` : ''}
                {m.cautions ? `—— ${m.cautions}` : ''}
              </li>
            ))}
          </ul>
          <p className="alert-note">
            {isDoctor
              ? 'AI 检索内容可能存在偏差或信息滞后，相关推断不计入风险分，请核实药品说明书原文后再用于临床决策。'
              : 'AI 检索内容可能存在偏差或信息滞后，仅供参考。强烈建议先通过人卫用药助手查询该药说明书原文，再咨询医生或药师。'}
          </p>
          <div className="nmpa-links">
            <a href={PMPH_LINK} target="_blank" rel="noreferrer" className="nmpa-link">
              前往人卫用药助手核实药品信息 ↗
            </a>
          </div>
        </section>
      )}

      {missingInfo.length > 0 && (
        <section className="block alert-block info-alert">
          <h3>信息缺失提醒</h3>
          <ul>
            {missingInfo.map((m, i) => <li key={i}>{isDoctor ? sanitizeForDoctor(m) : m}</li>)}
          </ul>
        </section>
      )}

      <div className="report-head">
        <Gauge score={overall.score} riskLevel={overall.riskLevel} caution={hasCaution} />
        <div className="report-headline">
          <div className="headline-title">总体结论</div>
          <div
            className="action-badge"
            style={{ background: `${actionTextColor}20`, color: actionTextColor }}
          >
            {displayAction}
          </div>
          <p>{headlineText || '—'}</p>
          {_meta.generatedAt && (
            <p className="report-meta">报告生成时间：{formatDateTime(_meta.generatedAt)}</p>
          )}
        </div>
      </div>

      {overall.riskLevel !== '未收录' && (
        <p className="score-note">
          <b>风险分是怎么算的：</b>只统计本地知识库中已确认的用药风险（药物相互作用、禁忌、同成分超量），按各项分值累加，0–30 分为低风险、31–60 分为中风险、61–85 分为高风险、86–100 分为极高风险。重复用药提示不参与计分，但同样需要留意。评分不包含未收录的药物和未填写的信息，低分或 0 分只说明"未发现已知规则风险"，不等于绝对安全——请结合报告下方各版块的提示一起看。
          {externalMeds.length > 0 && ' 本报告含 AI 外部检索分析：相关推断不计入分数，但发现潜在严重风险时，风险等级会按保守原则上调，请以人工核实为准。'}
        </p>
      )}

      {contraItems.length > 0 && (
        <section className="block contra-alert">
          <h3>🚫 禁忌警告：不建议使用</h3>
          {contraItems.map((c, i) => (
            <div key={i} className="contra-item">
              <p className="contra-title">
                <b>{c.drug}</b> {isDoctor ? <>对该患者的 <b>{c.condition}</b>（{c.severity}）属于禁忌范围</> : <>对您的 <b>{c.condition}</b>（{c.severity}）属于禁忌范围</>}
              </p>
              <p className="contra-detail">
                {isDoctor
                  ? '该药对该患者属于禁忌范围，不建议使用，建议更换其他药物。'
                  : '该药对您属于禁忌范围，不建议使用，请咨询医生更换其他药物。'}
              </p>
              {c.note && <p className="contra-note">{isDoctor ? sanitizeForDoctor(c.note) : c.note}</p>}
              {c.alternatives?.length > 0 && (
                <p className="contra-alt">
                  <b>{isDoctor ? '更安全的替代方向：' : '可向医生咨询的更安全替代方向：'}</b>
                  {c.alternatives.join('；')}
                  {isDoctor ? '（是否更换请结合临床判断）' : '（是否更换请由医生或药师决定）'}
                </p>
              )}
              {c.source && <p className="contra-source">依据：{c.source}</p>}
              <div className="nmpa-links">
                <a href={PMPH_LINK} target="_blank" rel="noreferrer" className="nmpa-link">
                  人卫用药助手查看 {c.drug} 详情 ↗
                </a>
              </div>
            </div>
          ))}
        </section>
      )}

      <div className="disclaimer-box">
        <p>
          {isDoctor
            ? (disclaimer ? sanitizeForDoctor(disclaimer) : '本报告由本地知识库规则与 AI 辅助生成，仅供专业参考，最终决策以临床判断为准。')
            : (disclaimer || '本报告仅供参考，不替代医师或药师的诊断与处方建议。')}
        </p>
      </div>

      {!isDoctor && dangerSignals.length > 0 && (
        <section className="block danger-signals">
          <h3>危险信号（出现以下情况请立即就医）</h3>
          <div className="signal-list">
            {dangerSignals.map((s, i) => (
              <span key={i} className="signal-tag">{s}</span>
            ))}
          </div>
          <div className="signal-action">
            <b>出现任一信号时这样做：</b>立即停用可疑药物，拨打 120 或由家属陪同前往最近医院急诊；就诊时请带上正在服用的所有药物（或拍照药盒），便于医生快速判断。
          </div>
        </section>
      )}

      {/* 医生视角：可解释评分（风险分 = 各风险项分值累加） */}
      {isDoctor && overall.scoreBreakdown?.length > 0 && (
        <section className="block score-breakdown">
          <h3>评分明细（风险分累加）</h3>
          <table className="breakdown-table">
            <thead>
              <tr><th>风险项</th><th>分值</th><th>依据</th></tr>
            </thead>
            <tbody>
              {overall.scoreBreakdown.map((b, i) => (
                <tr key={i}>
                  <td>{b.item}</td>
                  <td className="score-cell">+{b.score ?? b.deduction ?? 0} 分</td>
                  <td>{b.reason}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="breakdown-foot">总分 = 以上各项分值累加（0–100）。低 0–30 · 中 31–60 · 高 61–85 · 极高 86–100。</p>
        </section>
      )}

      {!isDoctor && summary.forFamily && (
        <section className="block summary-block">
          <h3>给患者和家属的说明</h3>
          <p>{summary.forFamily}</p>
        </section>
      )}

      {isDoctor && summary.forProfessional && (
        <section className="block summary-block professional">
          <h3>专业版分析</h3>
          <p>{sanitizeForDoctor(summary.forProfessional)}</p>
        </section>
      )}

      {interactions.length > 0 && (
        <section className="block">
          <h3>相互作用分析</h3>
          {interactions.map((it, i) => (
            <div key={i} className="interaction-item">
              <div className="interaction-header">
                <div>
                  <span className="drug-name">{it.drugA}</span>
                  <span className="interaction-symbol">×</span>
                  <span className="drug-name">{it.drugB}</span>
                </div>
                <div className="interaction-badges">
                  {it.external && <span className="ext-chip">AI检索推断</span>}
                  <SeverityBadge severity={it.severity} />
                </div>
              </div>
              <div className="item-body">
                <p><b>机制：</b>{it.mechanism || '—'}</p>
                <p><b>后果：</b>{it.clinicalEffect || '—'}</p>
                {it.commonSideEffects && (
                  <p className="common-effects"><b>一般人常见反应：</b>{it.commonSideEffects}</p>
                )}
                <p className="body-impact"><b>{isDoctor ? '对该患者的影响' : '对您身体的影响'}：</b>{it.bodyImpact || '—'}</p>
                {!isDoctor && <p className="diet-advice"><b>饮食注意：</b>{it.dietAdvice || '—'}</p>}
                <p><b>建议：</b>{isDoctor ? sanitizeForDoctor(it.recommendation) : (it.recommendation || '—')}</p>
                {!isDoctor && it.whatToDo && <p className="what-to-do"><b>我该怎么办：</b>{it.whatToDo}</p>}
                {it.whenToSeekHelp && (
                  <p className="when-seek-help"><b>{isDoctor ? '需告知患者立即就诊的情况' : '出现什么情况必须去医院'}：</b>{it.whenToSeekHelp}</p>
                )}

                {/* 人卫用药助手官方说明书链接 */}
                <div className="nmpa-links">
                  <a href={PMPH_LINK} target="_blank" rel="noreferrer" className="nmpa-link">
                    人卫用药助手查看 {it.drugA} 说明书 ↗
                  </a>
                  <a href={PMPH_LINK} target="_blank" rel="noreferrer" className="nmpa-link">
                    人卫用药助手查看 {it.drugB} 说明书 ↗
                  </a>
                  {isDoctor && (
                    <span className="data-version">数据版本：2024-12</span>
                  )}
                </div>

                {/* 医生专属字段 */}
                {isDoctor && (
                  <div className="doctor-only">
                    {it.evidenceLevel && (
                      <p><b>证据级别：</b>
                        <span className={`evidence-badge ev-${it.evidenceLevel}`}>{it.evidenceLevel}</span>
                      </p>
                    )}
                    {it.doseAdjustment && <p><b>剂量调整建议：</b>{it.doseAdjustment}</p>}
                    {it.alternatives?.length > 0 && (
                      <p><b>替代方案：</b>{it.alternatives.join('；')}</p>
                    )}
                  </div>
                )}

                <p className="grounding">
                  {it.grounded
                    ? '基于知识库记录'
                    : it.external
                      ? 'AI 外部检索推断（未经本地知识库核对，请核实）'
                      : `推断（${it.confidence || '低'}置信度）`}
                </p>
              </div>
            </div>
          ))}
        </section>
      )}

      {duplicates.length > 0 && (
        <section className="block">
          <h3>重复用药提示</h3>
          {duplicates.map((d, i) => (
            <div key={i} className="dup-item">
              <div className="dup-header">
                <b>类别：</b>
                {d.category || '—'}
              </div>
              <div className="dup-body">
                <p>
                  <b>涉及药物：</b>
                  {d.drugs?.join('、') || '—'}
                </p>
                <p>
                  <b>风险：</b>
                  {d.reason || '—'}
                </p>
              </div>
            </div>
          ))}
        </section>
      )}

      {patientWarnings.length > 0 && (
        <section className="block">
          <h3>{isDoctor ? '患者情况对用药决策的影响' : '结合您身体情况的提示'}</h3>
          {patientWarnings.map((w, i) => (
            <div key={i} className={`warning-item warning-${w.level === '危险' ? 'critical' : w.level === '警告' ? 'warn' : 'info'}`}>
              <div className="warning-header">
                <b>{w.title}</b>
                {w.level && <span className="warning-level">{w.level}</span>}
              </div>
              <p>{isDoctor ? (w.doctorNote || toDoctorPerspective(w.detail)) : w.detail}</p>
            </div>
          ))}
        </section>
      )}

      {!isDoctor && dietLifestyle?.dietPrinciples?.length > 0 && (
        <section className="block">
          <h3>饮食与生活建议</h3>
          <div className="diet-list">
            {dietLifestyle.dietPrinciples.map((p, i) => (
              <div key={i} className="diet-item">
                <span className="diet-index">{i + 1}</span>
                <p>{p}</p>
              </div>
            ))}
          </div>
          {dietLifestyle.watchList?.length > 0 && (
            <>
              <h4>日常观察清单</h4>
              <ul className="watch-list">
                {dietLifestyle.watchList.map((w, i) => (
                  <li key={i}>{w}</li>
                ))}
              </ul>
            </>
          )}
        </section>
      )}

      {suggestions.length > 0 && (
        <section className="block">
          <h3>{isDoctor ? '需向患者交代的要点' : '建议沟通点'}</h3>
          <ul className="suggestion-list">
            {suggestions.map((s, i) => (
              <li key={i}>{isDoctor ? sanitizeForDoctor(s) : s}</li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
