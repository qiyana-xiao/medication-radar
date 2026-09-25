import { useEffect, useRef, useState } from 'react';
import { api } from '../api.js';

// 药物联想输入：防抖搜索 + 键盘导航 + 命中高亮
export default function DrugAutocomplete({ value, onSelect, placeholder = '输入药名，如：阿司匹林' }) {
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState([]);
  const [active, setActive] = useState(-1);
  const [loading, setLoading] = useState(false);
  const boxRef = useRef(null);
  const timerRef = useRef(null);
  const valueRef = useRef(value);

  useEffect(() => {
    return () => clearTimeout(timerRef.current);
  }, []);

  useEffect(() => {
    const onDocClick = (e) => {
      if (boxRef.current && !boxRef.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener('mousedown', onDocClick);
    return () => document.removeEventListener('mousedown', onDocClick);
  }, []);

  const onChange = (v) => {
    valueRef.current = v;
    onSelect(v);
    clearTimeout(timerRef.current);
    if (!v.trim()) {
      setItems([]);
      setOpen(false);
      return;
    }
    const kw = v.trim();
    timerRef.current = setTimeout(async () => {
      setLoading(true);
      try {
        const res = await api(`/api/medications/search?q=${encodeURIComponent(kw)}&limit=12`);
        // 值在请求期间已改变（如已选药），丢弃过期结果
        if (valueRef.current.trim() !== kw) return;
        setItems(res);
        setActive(res.length ? 0 : -1);
        if (res.length === 1 && res[0].name === valueRef.current.trim()) {
          setOpen(false);
        } else {
          setOpen(true);
        }
      } catch {
        setItems([]);
      } finally {
        setLoading(false);
      }
    }, 250);
  };

  const pick = (item) => {
    clearTimeout(timerRef.current);
    valueRef.current = item.name;
    onSelect(item.name);
    setOpen(false);
  };

  const onKeyDown = (e) => {
    if (!open || !items.length) {
      if (e.key === 'Escape') setOpen(false);
      return;
    }
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActive((a) => (a + 1) % items.length);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActive((a) => (a - 1 + items.length) % items.length);
    } else if (e.key === 'Enter') {
      e.preventDefault();
      if (active >= 0 && items[active]) pick(items[active]);
    } else if (e.key === 'Escape') {
      setOpen(false);
    }
  };

  const highlight = (name) => {
    const kw = value.trim();
    const idx = name.indexOf(kw);
    if (!kw || idx < 0) return name;
    return (
      <>
        {name.slice(0, idx)}
        <b className="hl">{kw}</b>
        {name.slice(idx + kw.length)}
      </>
    );
  };

  return (
    <div className="autocomplete" ref={boxRef}>
      <input
        className="med-name"
        placeholder={placeholder}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={onKeyDown}
        autoComplete="off"
      />
      {open && (
        <div className="ac-panel">
          {loading && <div className="ac-hint">搜索中…</div>}
          {!loading && items.length === 0 && <div className="ac-hint">无匹配药物（仍可直接使用该名称分析）</div>}
          {!loading &&
            items.map((it, i) => (
              <div
                key={it.id}
                className={`ac-item ${i === active ? 'active' : ''}`}
                onMouseEnter={() => setActive(i)}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => pick(it)}
              >
                <div className="ac-name">{highlight(it.name)}</div>
                <div className="ac-meta">
                  {it.category}
                  {it.dosageForm ? ` · ${it.dosageForm}` : ''}
                </div>
              </div>
            ))}
        </div>
      )}
    </div>
  );
}