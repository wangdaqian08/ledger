import React from 'react';

/** Bottom sheet with a grab handle. Every create/edit flow lives in one. */
export function Sheet({ open, onClose, title, children, footer, height = 'auto', style, ...rest }) {
  return (
    <div
      aria-hidden={!open}
      style={{
        position: 'absolute', inset: 0, zIndex: 60,
        pointerEvents: open ? 'auto' : 'none',
      }}
    >
      <div
        onClick={onClose}
        style={{
          position: 'absolute', inset: 0, background: 'var(--scrim)',
          opacity: open ? 1 : 0,
          transition: 'opacity var(--dur-slow) var(--ease-out)',
        }}
      />
      <section
        style={{
          position: 'absolute', left: 0, right: 0, bottom: 0,
          maxHeight: '92%', height,
          display: 'flex', flexDirection: 'column',
          background: 'var(--paper)',
          borderTop: '2px solid var(--ink)',
          borderRadius: 'var(--radius-sheet) var(--radius-sheet) 0 0',
          boxShadow: 'var(--lift-sheet)',
          transform: open ? 'translateY(0)' : 'translateY(102%)',
          transition: `transform var(--dur-slow) ${open ? 'var(--ease-spring)' : 'var(--ease-out)'}`,
          ...style,
        }}
        {...rest}
      >
        <div style={{ display: 'flex', justifyContent: 'center', padding: '10px 0 2px' }}>
          <span style={{ width: 44, height: 5, borderRadius: 'var(--radius-pill)', background: 'var(--hairline-strong)' }} />
        </div>
        {title && (
          <h2 style={{
            margin: 0, padding: '6px var(--gutter-screen) 10px',
            fontFamily: 'var(--font-core)', fontSize: 'var(--text-title)', fontWeight: 'var(--weight-black)',
            letterSpacing: 'var(--ls-title)', color: 'var(--ink)',
          }}>{title}</h2>
        )}
        <div style={{ flex: 1, minHeight: 0, overflowY: 'auto', padding: '0 var(--gutter-screen) var(--space-4)' }}>
          {children}
        </div>
        {footer && (
          <div style={{
            padding: 'var(--space-3) var(--gutter-screen) calc(var(--space-4) + var(--safe-bottom))',
            borderTop: '1.5px solid var(--border-soft)', background: 'var(--paper)',
          }}>{footer}</div>
        )}
      </section>
    </div>
  );
}
