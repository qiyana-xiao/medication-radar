import { useEffect, useRef, useState } from 'react';

export default function Pagination({ page, totalPages, onChange }) {
  const [jumping, setJumping] = useState(false);
  const [jumpValue, setJumpValue] = useState('');
  const inputRef = useRef(null);

  useEffect(() => {
    if (jumping) {
      setJumpValue('');
      inputRef.current?.focus();
    }
  }, [jumping]);

  if (totalPages <= 1) return null;

  const showTotal = totalPages !== page;

  const submitJump = () => {
    const n = parseInt(jumpValue, 10);
    if (n >= 1 && n <= totalPages && n !== page) onChange(n);
    setJumping(false);
    setJumpValue('');
  };

  return (
    <div className="pagination">
      <button
        className="pg-arrow"
        disabled={page <= 1}
        onClick={() => onChange(page - 1)}
      >
        上一页
      </button>

      <span className="pg-num active" aria-current="page">
        {page}
      </span>

      {showTotal && !jumping && (
        <button
          className="pg-total"
          title={`共 ${totalPages} 页，点击输入页码跳转`}
          onClick={() => setJumping(true)}
        >
          {totalPages}
        </button>
      )}

      {showTotal && jumping && (
        <span className="pg-jump">
          <input
            ref={inputRef}
            type="number"
            min={1}
            max={totalPages}
            value={jumpValue}
            onChange={(e) => setJumpValue(e.target.value)}
            onBlur={submitJump}
            onKeyDown={(e) => {
              if (e.key === 'Enter') submitJump();
              if (e.key === 'Escape') {
                setJumping(false);
                setJumpValue('');
              }
            }}
            placeholder={`${page}`}
          />
          <span className="pg-jump-total">/ {totalPages}</span>
        </span>
      )}

      <button
        className="pg-arrow"
        disabled={page >= totalPages}
        onClick={() => onChange(page + 1)}
      >
        下一页
      </button>
    </div>
  );
}
