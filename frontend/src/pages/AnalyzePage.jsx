import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, useAuth } from '../api.js';
import Report from '../components/Report.jsx';
import DrugAutocomplete from '../components/DrugAutocomplete.jsx';

let uid = 1;
const nextId = () => uid++;

const COMMON_CONDITIONS = [
  '高血压',
  '糖尿病',
  '冠心病',
  '房颤',
  '高脂血症',
  '脑梗后遗症',
  '慢性肾病',
  '慢阻肺',
  '骨质疏松',
  '痛风',
];

const BP_PRESETS = [
  { label: '正常 120/80', s: 120, d: 80 },
  { label: '偏高 135/85', s: 135, d: 85 },
  { label: '1级 150/95', s: 150, d: 95 },
  { label: '2级 165/105', s: 165, d: 105 },
  { label: '3级 180/110', s: 180, d: 110 },
];

const FREQ_OPTIONS = [
  ['每日一次', '每日 1 次'],
  ['每日两次', '每日 2 次'],
  ['每日三次', '每日 3 次'],
  ['每周一次', '每周 1 次'],
  ['必要时', '必要时（按需）'],
];

function TagInput({ label, tags, onChange, placeholder }) {
  const [value, setValue] = useState('');

  const commit = () => {
    const v = value.trim();
    if (v && !tags.includes(v)) onChange([...tags, v]);
    setValue('');
  };

  return (
    <div className="field">
      <label>{label}</label>
      <div className="tag-box">
        {tags.map((t) => (
          <span key={t} className="tag">
            {t}
            <button type="button" onClick={() => onChange(tags.filter((x) => x !== t))}>
              ×
            </button>
          </span>
        ))}
        <input
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ',') {
              e.preventDefault();
              commit();
            }
          }}
          onBlur={commit}
          placeholder={tags.length ? '' : placeholder}
        />
      </div>
    </div>
  );
}

function QuickChips({ options, selected, onToggle }) {
  return (
    <div className="quick-chips">
      {options.map((opt) => (
        <button
          key={opt}
          type="button"
          className={selected.includes(opt) ? 'on' : ''}
          onClick={() =>
            onToggle(selected.includes(opt) ? selected.filter((x) => x !== opt) : [...selected, opt])
          }
        >
          {opt}
        </button>
      ))}
    </div>
  );
}

function UnitInput({ value, onChange, placeholder, unit, min, max, step }) {
  return (
    <div className="unit-field">
      <input
        type="number"
        min={min}
        max={max}
        step={step}
        placeholder={placeholder}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
      <span className="unit">{unit}</span>
    </div>
  );
}

function MedicationRow({ med, onChange, onRemove, groupLabel }) {
  return (
    <div className="med-card">
      <div className="med-row">
        <DrugAutocomplete value={med.name} onSelect={(name) => onChange({ ...med, name })} />
        <input
          className="med-dose"
          placeholder="剂量（如 100mg）"
          value={med.dose}
          onChange={(e) => onChange({ ...med, dose: e.target.value })}
        />
        <select
          className="med-freq"
          value={med.frequency}
          onChange={(e) => onChange({ ...med, frequency: e.target.value })}
        >
          <option value="">频次</option>
          {FREQ_OPTIONS.map(([v, l]) => (
            <option key={v} value={v}>
              {l}
            </option>
          ))}
        </select>
        <button type="button" className="remove-btn" onClick={onRemove}>
          删除
        </button>
      </div>
    </div>
  );
}

function MedicationGroup({ title, hint, meds, onChange, onAdd, onRemove }) {
  return (
    <div className="med-group">
      <div className="med-group-header">
        <h3>{title}</h3>
        {hint && <p className="med-group-hint">{hint}</p>}
      </div>
      <div className="med-list">
        {meds.map((med) => (
          <MedicationRow
            key={med.id}
            med={med}
            onChange={(patch) => onChange(med.id, patch)}
            onRemove={() => onRemove(med.id)}
          />
        ))}
      </div>
      <button type="button" className="ghost-btn ghost-sm" onClick={onAdd}>
        + 添加
      </button>
    </div>
  );
}

function SectionTitle({ num, title, hint }) {
  return (
    <h2>
      <span className="sec-num">{num}</span>
      {title}
      {hint && <em className="sec-hint">{hint}</em>}
    </h2>
  );
}

export default function AnalyzePage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [patient, setPatient] = useState({
    label: '',
    age: '',
    gender: '',
    conditions: [],
    allergies: [],
    symptoms: [],
    liver: '未知',
    kidney: '未知',
    treatment: '',
    vitals: { systolic: '', diastolic: '', heartRate: '', fastingGlucose: '', creatinine: '', weight: '', inr: '', potassium: '' },
  });
  const [baseMeds, setBaseMeds] = useState([]);
  const [newMeds, setNewMeds] = useState([{ id: nextId(), name: '', dose: '', frequency: '' }]);
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [consent, setConsent] = useState(false);

  const setPatientField = (k, v) => setPatient((p) => ({ ...p, [k]: v }));
  const setVital = (k, v) =>
    setPatient((p) => ({ ...p, vitals: { ...p.vitals, [k]: v === '' ? '' : String(v) } }));

  const updateBaseMed = (id, patch) =>
    setBaseMeds((list) => list.map((m) => (m.id === id ? { ...m, ...patch } : m)));
  const removeBaseMed = (id) =>
    setBaseMeds((list) => list.filter((m) => m.id !== id));
  const addBaseMed = () =>
    setBaseMeds((list) => [...list, { id: nextId(), name: '', dose: '', frequency: '' }]);

  const updateNewMed = (id, patch) =>
    setNewMeds((list) => list.map((m) => (m.id === id ? { ...m, ...patch } : m)));
  const removeNewMed = (id) =>
    setNewMeds((list) => (list.length > 1 ? list.filter((m) => m.id !== id) : list));
  const addNewMed = () =>
    setNewMeds((list) => [...list, { id: nextId(), name: '', dose: '', frequency: '' }]);

  const submit = async (e) => {
    e.preventDefault();
    setError(null);
    setResult(null);

    const validBase = baseMeds.filter((m) => m.name.trim());
    const validNew = newMeds.filter((m) => m.name.trim());
    if (!validBase.length && !validNew.length) {
      setError('请至少填写一种药物名称');
      return;
    }

    if (!consent) {
      setError('请先阅读并同意数据使用授权');
      return;
    }

    setLoading(true);
    try {
      const data = await api('/api/analyze', {
        method: 'POST',
        body: { patient, baseMedications: validBase, newMedications: validNew },
      });
      setResult(data.report);
    } catch (err) {
      setError(err.message || '分析失败');
    } finally {
      setLoading(false);
    }
  };

  const v = patient.vitals;
  const bpFilled = v.systolic && v.diastolic;
  const bpPresetActive = BP_PRESETS.find((p) => String(p.s) === v.systolic && String(p.d) === v.diastolic);

  return (
    <div className="page-inner">
      <div className="page-title">
        <h2>用药分析</h2>
        <p>填写患者信息与用药清单，AI 将实时生成个性化用药安全报告。</p>
        {!user && (
          <p className="guest-hint">
            当前为游客模式，报告不会保存。<Link to="/login">登录</Link>后自动保存到历史记录。
          </p>
        )}
      </div>

      <form className="form-card" onSubmit={submit}>
        <SectionTitle num="1" title="基本信息" />

        {user?.role === 'doctor' && (
          <div className="field patient-label-field">
            <label>患者标识（用于分析记录分组）</label>
            <input
              type="text"
              maxLength={30}
              placeholder="如：门诊号 / 床号 / 姓氏+序号，如 王阿姨-01"
              value={patient.label}
              onChange={(e) => setPatientField('label', e.target.value)}
            />
            <p className="field-hint">仅医生自己可见，用于按患者归档记录；请勿填写身份证号、全名等敏感身份信息。</p>
          </div>
        )}

        <div className="grid grid-4">
          <div className="field">
            <label>年龄</label>
            <input
              type="number"
              min="0"
              max="120"
              placeholder="如 72"
              value={patient.age}
              onChange={(e) => setPatientField('age', e.target.value)}
            />
          </div>
          <div className="field">
            <label>性别</label>
            <select value={patient.gender} onChange={(e) => setPatientField('gender', e.target.value)}>
              <option value="">请选择</option>
              <option value="男">男</option>
              <option value="女">女</option>
            </select>
          </div>
          <div className="field">
            <label>肝功能</label>
            <select value={patient.liver} onChange={(e) => setPatientField('liver', e.target.value)}>
              {['未知', '正常', '受损'].map((x) => (
                <option key={x} value={x}>
                  {x}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label>肾功能</label>
            <select value={patient.kidney} onChange={(e) => setPatientField('kidney', e.target.value)}>
              {['未知', '正常', '受损'].map((x) => (
                <option key={x} value={x}>
                  {x}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="field">
          <label>基础疾病（可多选 / 可自定义输入并发症）</label>
          <QuickChips
            options={COMMON_CONDITIONS}
            selected={patient.conditions}
            onToggle={(list) => setPatientField('conditions', list)}
          />
          <TagInput
            label=""
            tags={patient.conditions}
            onChange={(list) => setPatientField('conditions', list)}
            placeholder="其他疾病或并发症：输入后回车，如：糖尿病肾病"
          />
        </div>
        <TagInput
          label="过敏史"
          tags={patient.allergies}
          onChange={(list) => setPatientField('allergies', list)}
          placeholder="输入后回车，如：青霉素"
        />
        <TagInput
          label="当前症状"
          tags={patient.symptoms}
          onChange={(list) => setPatientField('symptoms', list)}
          placeholder="输入后回车，如：头痛、咳嗽、发热"
        />

        <div className="field">
          <label>目前治疗方案 / 近期就诊情况（选填）</label>
          <textarea
            rows={3}
            maxLength={500}
            placeholder="填写治疗经过与正在执行的方案，可显著减少「信息缺失」。如：半年前心肌梗死植入支架1枚，术后服用阿司匹林+氯吡格雷双联抗栓；高血压10年，目前氨氯地平5mg每日一次，血压控制在130/80左右；上月在医院查肝肾功能正常。"
            value={patient.treatment}
            onChange={(e) => setPatientField('treatment', e.target.value)}
          />
          <p className="field-hint">包括：手术/支架/起搏器史、近期化验结果、正在执行的处方方案、医生特殊嘱托等。</p>
        </div>

        <SectionTitle num="2" title="生命体征" hint="选填，作为 AI 分析参考" />

        <div className="vitals-block">
          <div className="vitals-bp">
            <div className="bp-inputs">
              <UnitInput
                value={v.systolic}
                onChange={(x) => setVital('systolic', x)}
                placeholder="收缩压（高压）"
                unit="mmHg"
                min="60"
                max="260"
              />
              <span className="bp-sep">/</span>
              <UnitInput
                value={v.diastolic}
                onChange={(x) => setVital('diastolic', x)}
                placeholder="舒张压（低压）"
                unit="mmHg"
                min="30"
                max="180"
              />
            </div>
            <div className="quick-chips">
              {BP_PRESETS.map((p) => (
                <button
                  key={p.label}
                  type="button"
                  className={bpPresetActive?.label === p.label ? 'on' : ''}
                  onClick={() => {
                    if (bpPresetActive?.label === p.label) {
                      setVital('systolic', '');
                      setVital('diastolic', '');
                    } else {
                      setVital('systolic', p.s);
                      setVital('diastolic', p.d);
                    }
                  }}
                >
                  {p.label}
                </button>
              ))}
            </div>
          </div>

          <div className="grid grid-4">
            <div className="field">
              <label>心率</label>
              <UnitInput
                value={v.heartRate}
                onChange={(x) => setVital('heartRate', x)}
                placeholder="60-100"
                unit="次/分"
                min="30"
                max="220"
              />
            </div>
            <div className="field">
              <label>空腹血糖</label>
              <UnitInput
                value={v.fastingGlucose}
                onChange={(x) => setVital('fastingGlucose', x)}
                placeholder="3.9-6.1"
                unit="mmol/L"
                min="1"
                max="35"
                step="0.1"
              />
            </div>
            <div className="field">
              <label>血肌酐</label>
              <UnitInput
                value={v.creatinine}
                onChange={(x) => setVital('creatinine', x)}
                placeholder="40-110"
                unit="μmol/L"
                min="20"
                max="2000"
              />
            </div>
            <div className="field">
              <label>体重</label>
              <UnitInput
                value={v.weight}
                onChange={(x) => setVital('weight', x)}
                placeholder="如 65"
                unit="kg"
                min="2"
                max="300"
                step="0.1"
              />
            </div>
            <div className="field">
              <label>INR（凝血指标，服华法林等抗凝药者填写）</label>
              <UnitInput
                value={v.inr}
                onChange={(x) => setVital('inr', x)}
                placeholder="如 2.4，未查可留空"
                unit=""
                min="0.5"
                max="10"
                step="0.1"
              />
            </div>
            <div className="field">
              <label>血钾</label>
              <UnitInput
                value={v.potassium}
                onChange={(x) => setVital('potassium', x)}
                placeholder="3.5-5.5"
                unit="mmol/L"
                min="1"
                max="9"
                step="0.1"
              />
            </div>
          </div>
          {bpFilled && (
            <p className="bp-reading">
              当前血压 <b>{v.systolic}/{v.diastolic}</b> mmHg
              {v.systolic >= 180 || v.diastolic >= 110
                ? '（3级高血压范围）'
                : v.systolic >= 160 || v.diastolic >= 100
                  ? '（2级高血压范围）'
                  : v.systolic >= 140 || v.diastolic >= 90
                    ? '（1级高血压范围）'
                    : v.systolic >= 130 || v.diastolic >= 85
                      ? '（正常高值）'
                      : '（正常范围）'}
            </p>
          )}
        </div>

        <SectionTitle num="3" title="用药清单" hint="分为长期用药和本次新增两组" />

        <MedicationGroup
          title="长期 / 基础用药"
          hint="患者平时一直在吃的药（如降压药、降糖药等），可留空"
          meds={baseMeds}
          onChange={updateBaseMed}
          onAdd={addBaseMed}
          onRemove={removeBaseMed}
        />

        <MedicationGroup
          title="本次新增 / 拟用药物"
          hint="患者想问能不能吃的药（如感冒药、止痛药等）"
          meds={newMeds}
          onChange={updateNewMed}
          onAdd={addNewMed}
          onRemove={removeNewMed}
        />

        <div className="consent-box">
          <label className="consent-label">
            <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
            <span>
              我已阅读并同意《数据使用授权说明》：您提供的疾病、用药、体征信息将仅用于本次用药安全分析，
              分析完成后数据将保存至您的个人历史记录，您可随时删除。本系统不诊断、不开处方、不替代医师或药师建议。
            </span>
          </label>
        </div>

        <button type="submit" className="submit-btn" disabled={loading}>
          {loading ? 'AI 正在分析…' : '开始分析'}
        </button>

        {loading && (
          <p className="loading-hint">
            AI 正在读取药单、检索药物相互作用并生成个性化报告，通常约需 10–30 秒…
          </p>
        )}
        {error && <p className="error">{error}</p>}
      </form>

      {result && (
        <div className="result-wrap">
          <div className="result-actions">
            {user ? (
              <button className="btn-text" onClick={() => navigate('/history')}>
                已保存到{user.role === 'doctor' ? '分析记录' : '历史报告'} → 查看全部
              </button>
            ) : (
              <button className="btn-text" onClick={() => navigate('/login')}>
                登录后可保存报告 →
              </button>
            )}
          </div>
          <Report report={result} userRole={user?.role} />
        </div>
      )}
    </div>
  );
}