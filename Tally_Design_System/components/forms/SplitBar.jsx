import React from 'react';

/**
 * Draggable proportional split. One horizontal bar, one handle between each
 * neighbouring pair. This is Tally's signature interaction.
 */
export function SplitBar({ people = [], total = 100, onChange, height = 56, showLabels = true, style, ...rest }) {
  const ref = React.useRef(null);
  const [drag, setDrag] = React.useState(null);
  const sum = people.reduce((a, p) => a + (p.value || 0), 0) || 1;
  const pct = people.map((p) => ((p.value || 0) / sum) * 100);

  const move = (clientX) => {
    if (drag == null || !ref.current || !onChange) return;
    const r = ref.current.getBoundingClientRect();
    const at = Math.min(100, Math.max(0, ((clientX - r.left) / r.width) * 100));
    const before = pct.slice(0, drag).reduce((a, b) => a + b, 0);
    const pair = pct[drag] + pct[drag + 1];
    const left = Math.min(Math.max(at - before, 2), pair - 2);
    const next = people.map((p, i) =>
      i === drag ? { ...p, value: (left / 100) * sum }
      : i === drag + 1 ? { ...p, value: ((pair - left) / 100) * sum }
      : p
    );
    onChange(next);
  };

  React.useEffect(() => {
    if (drag == null) return;
    const mv = (e) => move(e.touches ? e.touches[0].clientX : e.clientX);
    const up = () => setDrag(null);
    window.addEventListener('pointermove', mv);
    window.addEventListener('pointerup', up);
    return () => { window.removeEventListener('pointermove', mv); window.removeEventListener('pointerup', up); };
  });

  let acc = 0;
  const edges = pct.slice(0, -1).map((p) => (acc += p));

  return (
    <div style={{ ...style }} {...rest}>
      <div
        ref={ref}
        style={{
          position: 'relative', display: 'flex', height, width: '100%',
          border: '2px solid var(--ink)', borderRadius: 'var(--radius-md)',
          overflow: 'hidden', boxShadow: '0 3px 0 0 var(--ink)', background: 'var(--bg-sunk)',
          touchAction: 'none', userSelect: 'none',
        }}
      >
        {people.map((p, i) => (
          <div key={p.name + i} style={{
            width: pct[i] + '%',
            background: `var(--person-${((p.hue || 1) - 1) % 8 + 1})`,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            transition: drag == null ? 'width var(--dur-base) var(--ease-spring)' : 'none',
            overflow: 'hidden',
          }}>
            {pct[i] > 12 && (
              <span style={{
                fontFamily: 'var(--font-money)', fontSize: 13, fontWeight: 'var(--weight-bold)',
                color: (p.hue === 3) ? 'var(--ink)' : 'var(--text-on-accent)', whiteSpace: 'nowrap',
              }}>{Math.round(pct[i])}%</span>
            )}
          </div>
        ))}
        {edges.map((x, i) => (
          <span
            key={i}
            onPointerDown={(e) => { e.preventDefault(); setDrag(i); }}
            style={{
              position: 'absolute', top: -2, bottom: -2, left: `calc(${x}% - 13px)`,
              width: 26, display: 'flex', alignItems: 'center', justifyContent: 'center',
              cursor: 'ew-resize', touchAction: 'none',
            }}
          >
            <span style={{
              width: 14, height: height - 12, borderRadius: 'var(--radius-pill)',
              background: 'var(--surface-card)', border: '2px solid var(--ink)',
              transform: drag === i ? 'scaleY(1.12) scaleX(1.15)' : 'none',
              boxShadow: drag === i ? 'var(--lift-drag)' : 'none',
              transition: 'transform var(--dur-fast) var(--ease-spring)',
            }} />
          </span>
        ))}
      </div>
      {showLabels && (
        <div style={{ display: 'flex', gap: 'var(--space-4)', marginTop: 'var(--space-3)', flexWrap: 'wrap' }}>
          {people.map((p, i) => (
            <span key={p.name + i} style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
              <span style={{
                width: 10, height: 10, borderRadius: 'var(--radius-circle)',
                background: `var(--person-${((p.hue || 1) - 1) % 8 + 1})`, border: '1.5px solid var(--ink)',
              }} />
              <span style={{ fontSize: 'var(--text-caption)', fontWeight: 'var(--weight-semibold)', color: 'var(--ink-2)' }}>
                {p.name}
              </span>
              <span style={{
                fontFamily: 'var(--font-money)', fontVariantNumeric: 'tabular-nums',
                fontSize: 'var(--text-money-sm)', fontWeight: 'var(--weight-bold)', color: 'var(--ink)',
              }}>
                ${(((p.value || 0) / sum) * total).toFixed(2)}
              </span>
            </span>
          ))}
        </div>
      )}
    </div>
  );
}
