/* @ds-bundle: {"format":4,"namespace":"TallyDesignSystem_ae2e12","components":[{"name":"Amount","sourcePath":"components/core/Amount.jsx"},{"name":"Avatar","sourcePath":"components/core/Avatar.jsx"},{"name":"AvatarStack","sourcePath":"components/core/AvatarStack.jsx"},{"name":"Badge","sourcePath":"components/core/Badge.jsx"},{"name":"Button","sourcePath":"components/core/Button.jsx"},{"name":"Card","sourcePath":"components/core/Card.jsx"},{"name":"Chip","sourcePath":"components/core/Chip.jsx"},{"name":"Icon","sourcePath":"components/core/Icon.jsx"},{"name":"IconButton","sourcePath":"components/core/IconButton.jsx"},{"name":"ICON_INNER","sourcePath":"components/core/icon-paths.js"},{"name":"ICON_NAMES","sourcePath":"components/core/icon-paths.js"},{"name":"EmptyState","sourcePath":"components/feedback/EmptyState.jsx"},{"name":"ProgressBar","sourcePath":"components/feedback/ProgressBar.jsx"},{"name":"SettledBanner","sourcePath":"components/feedback/SettledBanner.jsx"},{"name":"Toast","sourcePath":"components/feedback/Toast.jsx"},{"name":"AmountInput","sourcePath":"components/forms/AmountInput.jsx"},{"name":"CATEGORIES","sourcePath":"components/forms/CategoryPicker.jsx"},{"name":"CategoryPicker","sourcePath":"components/forms/CategoryPicker.jsx"},{"name":"Input","sourcePath":"components/forms/Input.jsx"},{"name":"Keypad","sourcePath":"components/forms/Keypad.jsx"},{"name":"PersonToggleRow","sourcePath":"components/forms/PersonToggleRow.jsx"},{"name":"SplitBar","sourcePath":"components/forms/SplitBar.jsx"},{"name":"Stepper","sourcePath":"components/forms/Stepper.jsx"},{"name":"BalanceRow","sourcePath":"components/lists/BalanceRow.jsx"},{"name":"ExpenseRow","sourcePath":"components/lists/ExpenseRow.jsx"},{"name":"GroupCard","sourcePath":"components/lists/GroupCard.jsx"},{"name":"ListRow","sourcePath":"components/lists/ListRow.jsx"},{"name":"AppBar","sourcePath":"components/navigation/AppBar.jsx"},{"name":"Sheet","sourcePath":"components/navigation/Sheet.jsx"},{"name":"TabBar","sourcePath":"components/navigation/TabBar.jsx"}],"sourceHashes":{"components/core/Amount.jsx":"49f88fc5b321","components/core/Avatar.jsx":"eaf5f566e4ea","components/core/AvatarStack.jsx":"580b0fd9fca7","components/core/Badge.jsx":"ee7a393efbb7","components/core/Button.jsx":"b98f2a7dc583","components/core/Card.jsx":"f9ef201a1d92","components/core/Chip.jsx":"49b64eace738","components/core/Icon.jsx":"f3fa8a176032","components/core/IconButton.jsx":"3dd334360766","components/core/icon-paths.js":"da823bacb86a","components/feedback/EmptyState.jsx":"aeb012f38c42","components/feedback/ProgressBar.jsx":"c77a416bebad","components/feedback/SettledBanner.jsx":"1e777bd5cd28","components/feedback/Toast.jsx":"ce2abb1fb20e","components/forms/AmountInput.jsx":"efbc0bc3a4d4","components/forms/CategoryPicker.jsx":"4246a5e085bd","components/forms/Input.jsx":"36513e47d220","components/forms/Keypad.jsx":"da7abd382219","components/forms/PersonToggleRow.jsx":"65dc563dec03","components/forms/SplitBar.jsx":"14eebba76696","components/forms/Stepper.jsx":"2dd99f8ce1c0","components/lists/BalanceRow.jsx":"d5cba2d01407","components/lists/ExpenseRow.jsx":"5edbafc4cfd7","components/lists/GroupCard.jsx":"cb88472bca77","components/lists/ListRow.jsx":"07566c3a808a","components/navigation/AppBar.jsx":"425a0eb47f3c","components/navigation/Sheet.jsx":"5da9202b1096","components/navigation/TabBar.jsx":"e04b22254e02","ui_kits/mobile-app/App.jsx":"8798a3108180","ui_kits/mobile-app/Screens.jsx":"c50ff195c4f7","ui_kits/mobile-app/data.js":"d7e4c90633b7"},"inlinedExternals":[],"unexposedExports":[]} */

(() => {

const __ds_ns = (window.TallyDesignSystem_ae2e12 = window.TallyDesignSystem_ae2e12 || {});

const __ds_scope = {};

(__ds_ns.__errors = __ds_ns.__errors || []);

// components/core/Amount.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
const SIZES = {
  sm: 'var(--text-money-sm)',
  md: 'var(--text-money)',
  lg: 'var(--text-money-lg)',
  hero: 'var(--text-money-hero)'
};
const TONES = {
  neutral: 'var(--ink)',
  owed: 'var(--owed-to-you)',
  owe: 'var(--you-owe)',
  settled: 'var(--settled)',
  onDark: 'var(--text-on-accent)'
};

/** Money. Always Space Grotesk, always tabular, sign carried by color. */
function Amount({
  value,
  currency = '$',
  size = 'md',
  tone = 'neutral',
  showSign = false,
  animate = false,
  style,
  ...rest
}) {
  const n = Math.abs(Number(value) || 0);
  const sign = showSign ? Number(value) < 0 ? '−' : '+' : '';
  const body = n.toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });
  return /*#__PURE__*/React.createElement("span", _extends({
    style: {
      fontFamily: 'var(--font-money)',
      fontVariantNumeric: 'tabular-nums',
      fontFeatureSettings: '"tnum" 1',
      fontSize: SIZES[size] || SIZES.md,
      fontWeight: size === 'hero' || size === 'lg' ? 'var(--weight-bold)' : 'var(--weight-medium)',
      letterSpacing: size === 'hero' ? '-0.03em' : '-0.01em',
      lineHeight: 1,
      color: TONES[tone] || TONES.neutral,
      whiteSpace: 'nowrap',
      transition: animate ? 'color var(--dur-base) var(--ease-out)' : undefined,
      ...style
    }
  }, rest), sign, currency, body);
}
Object.assign(__ds_scope, { Amount });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/core/Amount.jsx", error: String((e && e.message) || e) }); }

// components/core/Avatar.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
const PERSON_HUES = 8;
function initials(name = '', single = false) {
  const parts = String(name).trim().split(/\s+/).filter(Boolean);
  if (!parts.length) return '?';
  if (single) return parts[0][0].toUpperCase();
  return (parts[0][0] + (parts[1] ? parts[1][0] : '')).toUpperCase();
}

/** Person marker. Each member of a group gets a fixed hue from --person-1..8. */
function Avatar({
  name,
  hue = 1,
  size = 40,
  selected,
  dimmed,
  badge,
  compact,
  style,
  ...rest
}) {
  const h = ((Number(hue) - 1) % PERSON_HUES + PERSON_HUES) % PERSON_HUES + 1;
  return /*#__PURE__*/React.createElement("span", {
    style: {
      position: 'relative',
      display: 'inline-flex',
      flex: '0 0 auto'
    }
  }, /*#__PURE__*/React.createElement("span", _extends({
    title: name,
    style: {
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      width: size,
      height: size,
      borderRadius: 'var(--radius-circle)',
      background: `var(--person-${h})`,
      color: h === 3 ? 'var(--ink)' : 'var(--text-on-accent)',
      border: '2px solid var(--ink)',
      fontFamily: 'var(--font-core)',
      fontWeight: 'var(--weight-black)',
      fontSize: Math.max(11, Math.round(size * (compact ? 0.44 : 0.38))),
      lineHeight: 1,
      letterSpacing: '0.01em',
      boxShadow: selected ? '0 0 0 3px var(--paper), 0 0 0 6px var(--ink)' : 'none',
      opacity: dimmed ? 0.32 : 1,
      filter: dimmed ? 'saturate(0.4)' : 'none',
      transition: 'opacity var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast) var(--ease-spring), filter var(--dur-fast) var(--ease-out)',
      ...style
    }
  }, rest), initials(name, compact)), badge != null && /*#__PURE__*/React.createElement("span", {
    style: {
      position: 'absolute',
      right: -4,
      bottom: -4,
      minWidth: 18,
      height: 18,
      padding: '0 4px',
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'var(--ink)',
      color: 'var(--text-on-accent)',
      border: '2px solid var(--paper)',
      borderRadius: 'var(--radius-pill)',
      fontFamily: 'var(--font-money)',
      fontSize: 10,
      fontWeight: 'var(--weight-bold)',
      lineHeight: 1
    }
  }, badge));
}
Object.assign(__ds_scope, { Avatar });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/core/Avatar.jsx", error: String((e && e.message) || e) }); }

// components/core/AvatarStack.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/** Overlapping row of members with a +N overflow pip. */
function AvatarStack({
  people = [],
  size = 32,
  max = 4,
  overlap = 10,
  style,
  ...rest
}) {
  const shown = people.slice(0, max);
  const rest_ = people.length - shown.length;
  // Only `size - overlap` px of each stacked circle is visible; two initials need ~22px.
  const compact = size - overlap < 22;
  return /*#__PURE__*/React.createElement("span", _extends({
    style: {
      display: 'inline-flex',
      alignItems: 'center',
      ...style
    }
  }, rest), shown.map((p, i) => /*#__PURE__*/React.createElement("span", {
    key: p.name + i,
    style: {
      marginLeft: i === 0 ? 0 : -overlap,
      zIndex: shown.length - i
    }
  }, /*#__PURE__*/React.createElement(__ds_scope.Avatar, {
    name: p.name,
    hue: p.hue,
    size: size,
    compact: compact && i > 0
  }))), rest_ > 0 && /*#__PURE__*/React.createElement("span", {
    style: {
      marginLeft: -overlap,
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      width: size,
      height: size,
      borderRadius: 'var(--radius-circle)',
      background: 'var(--bg-sunk)',
      color: 'var(--ink-2)',
      border: '2px solid var(--ink)',
      fontFamily: 'var(--font-core)',
      fontSize: Math.round(size * 0.34),
      fontWeight: 'var(--weight-black)'
    }
  }, "+", rest_));
}
Object.assign(__ds_scope, { AvatarStack });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/core/AvatarStack.jsx", error: String((e && e.message) || e) }); }

// components/core/Badge.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
const T = {
  neutral: ['var(--bg-sunk)', 'var(--ink-2)'],
  action: ['var(--action-tint)', 'var(--action)'],
  mint: ['var(--mint-tint)', 'var(--mint-press)'],
  coral: ['var(--you-owe-tint)', 'var(--coral-press)'],
  lemon: ['var(--lemon-tint)', 'var(--ink)'],
  ink: ['var(--ink)', 'var(--text-on-accent)']
};

/** Tiny uppercase status label. Not tappable — see Chip for that. */
function Badge({
  children,
  tone = 'neutral',
  style,
  ...rest
}) {
  const [bg, fg] = T[tone] || T.neutral;
  return /*#__PURE__*/React.createElement("span", _extends({
    style: {
      display: 'inline-flex',
      alignItems: 'center',
      height: 22,
      padding: '0 9px',
      background: bg,
      color: fg,
      fontFamily: 'var(--font-core)',
      fontSize: 'var(--text-label)',
      fontWeight: 'var(--weight-black)',
      letterSpacing: 'var(--ls-label)',
      textTransform: 'uppercase',
      lineHeight: 1,
      borderRadius: 'var(--radius-pill)',
      whiteSpace: 'nowrap',
      ...style
    }
  }, rest), children);
}
Object.assign(__ds_scope, { Badge });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/core/Badge.jsx", error: String((e && e.message) || e) }); }

// components/core/Card.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/** White slab with a hard ink border. The container for almost everything. */
function Card({
  children,
  tone = 'surface',
  lift = 2,
  pressable,
  padded = true,
  style,
  ...rest
}) {
  const [down, setDown] = React.useState(false);
  const tones = {
    surface: {
      bg: 'var(--surface-card)',
      fg: 'var(--ink)'
    },
    sunk: {
      bg: 'var(--bg-sunk)',
      fg: 'var(--ink)'
    },
    action: {
      bg: 'var(--action-tint)',
      fg: 'var(--ink)'
    },
    mint: {
      bg: 'var(--mint-tint)',
      fg: 'var(--ink)'
    },
    coral: {
      bg: 'var(--you-owe-tint)',
      fg: 'var(--ink)'
    },
    lemon: {
      bg: 'var(--lemon-tint)',
      fg: 'var(--ink)'
    },
    ink: {
      bg: 'var(--ink)',
      fg: 'var(--text-on-accent)'
    }
  };
  const t = tones[tone] || tones.surface;
  const d = pressable && down;
  return /*#__PURE__*/React.createElement("div", _extends({
    onPointerDown: pressable ? () => setDown(true) : undefined,
    onPointerUp: pressable ? () => setDown(false) : undefined,
    onPointerLeave: pressable ? () => setDown(false) : undefined,
    style: {
      background: t.bg,
      color: t.fg,
      border: '2px solid var(--ink)',
      borderRadius: 'var(--radius-card)',
      padding: padded ? 'var(--pad-card)' : 0,
      boxShadow: lift > 0 ? `0 ${d ? 1 : lift}px 0 0 var(--ink)` : 'none',
      transform: d ? `translateY(${lift - 1}px)` : 'none',
      cursor: pressable ? 'pointer' : undefined,
      transition: 'transform var(--dur-instant) var(--ease-out), box-shadow var(--dur-instant) var(--ease-out)',
      ...style
    }
  }, rest), children);
}
Object.assign(__ds_scope, { Card });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/core/Card.jsx", error: String((e && e.message) || e) }); }

// components/core/icon-paths.js
try { (() => {
/* Generated from lucide-icons/lucide (ISC). Inner markup of each 24x24 glyph,
   vendored so icons render offline and survive screenshot / PDF / PPTX export. */
const ICON_INNER = {
  "utensils": "<path d=\"M3 2v7c0 1.1.9 2 2 2h4a2 2 0 0 0 2-2V2\"></path><path d=\"M7 2v20\"></path><path d=\"M21 15V2a5 5 0 0 0-5 5v6c0 1.1.9 2 2 2h3Zm0 0v7\"></path>",
  "beer": "<path d=\"M17 11h1a3 3 0 0 1 0 6h-1\"></path><path d=\"M9 12v6\"></path><path d=\"M13 12v6\"></path><path d=\"M14 7.5c-1 0-1.44.5-3 .5s-2-.5-3-.5-1.72.5-2.5.5a2.5 2.5 0 0 1 0-5c.78 0 1.57.5 2.5.5S9.44 2 11 2s2 1.5 3 1.5 1.72-.5 2.5-.5a2.5 2.5 0 0 1 0 5c-.78 0-1.5-.5-2.5-.5Z\"></path><path d=\"M5 8v12a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V8\"></path>",
  "car-front": "<path d=\"m21 8-2 2-1.5-3.7A2 2 0 0 0 15.646 5H8.4a2 2 0 0 0-1.903 1.257L5 10 3 8\"></path><path d=\"M7 14h.01\"></path><path d=\"M17 14h.01\"></path><rect width=\"18\" height=\"8\" x=\"3\" y=\"10\" rx=\"2\"></rect><path d=\"M5 18v2\"></path><path d=\"M19 18v2\"></path>",
  "bed-double": "<path d=\"M2 20v-8a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v8\"></path><path d=\"M4 10V6a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v4\"></path><path d=\"M12 4v6\"></path><path d=\"M2 18h20\"></path>",
  "shopping-basket": "<path d=\"m15 11-1 9\"></path><path d=\"m19 11-4-7\"></path><path d=\"M2 11h20\"></path><path d=\"m3.5 11 1.6 7.4a2 2 0 0 0 2 1.6h9.8a2 2 0 0 0 2-1.6l1.7-7.4\"></path><path d=\"M4.5 15.5h15\"></path><path d=\"m5 11 4-7\"></path><path d=\"m9 11 1 9\"></path>",
  "ticket": "<path d=\"M2 9a3 3 0 0 1 0 6v2a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-2a3 3 0 0 1 0-6V7a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2Z\"></path><path d=\"M13 5v2\"></path><path d=\"M13 17v2\"></path><path d=\"M13 11v2\"></path>",
  "house": "<path d=\"M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8\"></path><path d=\"M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z\"></path>",
  "circle-dashed": "<path d=\"M10.1 2.182a10 10 0 0 1 3.8 0\"></path><path d=\"M13.9 21.818a10 10 0 0 1-3.8 0\"></path><path d=\"M17.609 3.721a10 10 0 0 1 2.69 2.7\"></path><path d=\"M2.182 13.9a10 10 0 0 1 0-3.8\"></path><path d=\"M20.279 17.609a10 10 0 0 1-2.7 2.69\"></path><path d=\"M21.818 10.1a10 10 0 0 1 0 3.8\"></path><path d=\"M3.721 6.391a10 10 0 0 1 2.7-2.69\"></path><path d=\"M6.391 20.279a10 10 0 0 1-2.69-2.7\"></path>",
  "plus": "<path d=\"M5 12h14\"></path><path d=\"M12 5v14\"></path>",
  "minus": "<path d=\"M5 12h14\"></path>",
  "users": "<path d=\"M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2\"></path><path d=\"M16 3.128a4 4 0 0 1 0 7.744\"></path><path d=\"M22 21v-2a4 4 0 0 0-3-3.87\"></path><circle cx=\"9\" cy=\"7\" r=\"4\"></circle>",
  "clock": "<circle cx=\"12\" cy=\"12\" r=\"10\"></circle><path d=\"M12 6v6l4 2\"></path>",
  "circle-user": "<circle cx=\"12\" cy=\"12\" r=\"10\"></circle><circle cx=\"12\" cy=\"10\" r=\"3\"></circle><path d=\"M7 20.662V19a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v1.662\"></path>",
  "user-round": "<circle cx=\"12\" cy=\"8\" r=\"5\"></circle><path d=\"M20 21a8 8 0 0 0-16 0\"></path>",
  "receipt": "<path d=\"M12 17V7\"></path><path d=\"M16 8h-6a2 2 0 0 0 0 4h4a2 2 0 0 1 0 4H8\"></path><path d=\"M4 3a1 1 0 0 1 1-1 1.3 1.3 0 0 1 .7.2l.933.6a1.3 1.3 0 0 0 1.4 0l.934-.6a1.3 1.3 0 0 1 1.4 0l.933.6a1.3 1.3 0 0 0 1.4 0l.933-.6a1.3 1.3 0 0 1 1.4 0l.934.6a1.3 1.3 0 0 0 1.4 0l.933-.6A1.3 1.3 0 0 1 19 2a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1 1.3 1.3 0 0 1-.7-.2l-.933-.6a1.3 1.3 0 0 0-1.4 0l-.934.6a1.3 1.3 0 0 1-1.4 0l-.933-.6a1.3 1.3 0 0 0-1.4 0l-.933.6a1.3 1.3 0 0 1-1.4 0l-.934-.6a1.3 1.3 0 0 0-1.4 0l-.933.6a1.3 1.3 0 0 1-.7.2 1 1 0 0 1-1-1z\"></path>",
  "party-popper": "<path d=\"M5.8 11.3 2 22l10.7-3.79\"></path><path d=\"M4 3h.01\"></path><path d=\"M22 8h.01\"></path><path d=\"M15 2h.01\"></path><path d=\"M22 20h.01\"></path><path d=\"m22 2-2.24.75a2.9 2.9 0 0 0-1.96 3.12c.1.86-.57 1.63-1.45 1.63h-.38c-.86 0-1.6.6-1.76 1.44L14 10\"></path><path d=\"m22 13-.82-.33c-.86-.34-1.82.2-1.98 1.11c-.11.7-.72 1.22-1.43 1.22H17\"></path><path d=\"m11 2 .33.82c.34.86-.2 1.82-1.11 1.98C9.52 4.9 9 5.52 9 6.23V7\"></path><path d=\"M11 13c1.93 1.93 2.83 4.17 2 5-.83.83-3.07-.07-5-2-1.93-1.93-2.83-4.17-2-5 .83-.83 3.07.07 5 2Z\"></path>",
  "chevron-right": "<path d=\"m9 18 6-6-6-6\"></path>",
  "chevron-left": "<path d=\"m15 18-6-6 6-6\"></path>",
  "user-plus": "<path d=\"M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2\"></path><circle cx=\"9\" cy=\"7\" r=\"4\"></circle><line x1=\"19\" x2=\"19\" y1=\"8\" y2=\"14\"></line><line x1=\"22\" x2=\"16\" y1=\"11\" y2=\"11\"></line>",
  "arrow-left": "<path d=\"m12 19-7-7 7-7\"></path><path d=\"M19 12H5\"></path>",
  "arrow-right": "<path d=\"M5 12h14\"></path><path d=\"m12 5 7 7-7 7\"></path>",
  "check": "<path d=\"M20 6 9 17l-5-5\"></path>",
  "x": "<path d=\"M18 6 6 18\"></path><path d=\"m6 6 12 12\"></path>",
  "search": "<path d=\"m21 21-4.34-4.34\"></path><circle cx=\"11\" cy=\"11\" r=\"8\"></circle>",
  "share-2": "<circle cx=\"18\" cy=\"5\" r=\"3\"></circle><circle cx=\"6\" cy=\"12\" r=\"3\"></circle><circle cx=\"18\" cy=\"19\" r=\"3\"></circle><line x1=\"8.59\" x2=\"15.42\" y1=\"13.51\" y2=\"17.49\"></line><line x1=\"15.41\" x2=\"8.59\" y1=\"6.51\" y2=\"10.49\"></line>",
  "mail": "<path d=\"m22 7-8.991 5.727a2 2 0 0 1-2.009 0L2 7\"></path><rect x=\"2\" y=\"4\" width=\"20\" height=\"16\" rx=\"2\"></rect>",
  "trash-2": "<path d=\"M10 11v6\"></path><path d=\"M14 11v6\"></path><path d=\"M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6\"></path><path d=\"M3 6h18\"></path><path d=\"M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2\"></path>",
  "delete": "<path d=\"M10 5a2 2 0 0 0-1.344.519l-6.328 5.74a1 1 0 0 0 0 1.481l6.328 5.741A2 2 0 0 0 10 19h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2z\"></path><path d=\"m12 9 6 6\"></path><path d=\"m18 9-6 6\"></path>",
  "handshake": "<path d=\"m11 17 2 2a1 1 0 1 0 3-3\"></path><path d=\"m14 14 2.5 2.5a1 1 0 1 0 3-3l-3.88-3.88a3 3 0 0 0-4.24 0l-.88.88a1 1 0 1 1-3-3l2.81-2.81a5.79 5.79 0 0 1 7.06-.87l.47.28a2 2 0 0 0 1.42.25L21 4\"></path><path d=\"m21 3 1 11h-2\"></path><path d=\"M3 3 2 14l6.5 6.5a1 1 0 1 0 3-3\"></path><path d=\"M3 4h8\"></path>",
  "ellipsis": "<circle cx=\"12\" cy=\"12\" r=\"1\"></circle><circle cx=\"19\" cy=\"12\" r=\"1\"></circle><circle cx=\"5\" cy=\"12\" r=\"1\"></circle>",
  "link": "<path d=\"M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71\"></path><path d=\"M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71\"></path>",
  "plane": "<path d=\"M17.8 19.2 16 11l3.5-3.5C21 6 21.5 4 21 3c-1-.5-3 0-4.5 1.5L13 8 4.8 6.2c-.5-.1-.9.1-1.1.5l-.3.5c-.2.5-.1 1 .3 1.3L9 12l-2 3H4l-1 1 3 2 2 3 1-1v-3l3-2 3.5 5.3c.3.4.8.5 1.3.3l.5-.2c.4-.3.6-.7.5-1.2z\"></path>",
  "coffee": "<path d=\"M10 2v2\"></path><path d=\"M14 2v2\"></path><path d=\"M16 8a1 1 0 0 1 1 1v8a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V9a1 1 0 0 1 1-1h14a4 4 0 1 1 0 8h-1\"></path><path d=\"M6 2v2\"></path>",
  "signal": "<path d=\"M2 20h.01\"></path><path d=\"M7 20v-4\"></path><path d=\"M12 20v-8\"></path><path d=\"M17 20V8\"></path><path d=\"M22 4v16\"></path>",
  "wifi": "<path d=\"M12 20h.01\"></path><path d=\"M2 8.82a15 15 0 0 1 20 0\"></path><path d=\"M5 12.859a10 10 0 0 1 14 0\"></path><path d=\"M8.5 16.429a5 5 0 0 1 7 0\"></path>",
  "battery-full": "<path d=\"M10 10v4\"></path><path d=\"M14 10v4\"></path><path d=\"M22 14v-4\"></path><path d=\"M6 10v4\"></path><rect x=\"2\" y=\"6\" width=\"16\" height=\"12\" rx=\"2\"></rect>",
  "lock": "<rect width=\"18\" height=\"11\" x=\"3\" y=\"11\" rx=\"2\" ry=\"2\"></rect><path d=\"M7 11V7a5 5 0 0 1 10 0v4\"></path>",
  "rotate-cw": "<path d=\"M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8\"></path><path d=\"M21 3v5h-5\"></path>"
};
const ICON_NAMES = ["utensils", "beer", "car-front", "bed-double", "shopping-basket", "ticket", "house", "circle-dashed", "plus", "minus", "users", "clock", "circle-user", "user-round", "receipt", "party-popper", "chevron-right", "chevron-left", "user-plus", "arrow-left", "arrow-right", "check", "x", "search", "share-2", "mail", "trash-2", "delete", "handshake", "ellipsis", "link", "plane", "coffee", "signal", "wifi", "battery-full", "lock", "rotate-cw"];
Object.assign(__ds_scope, { ICON_INNER, ICON_NAMES });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/core/icon-paths.js", error: String((e && e.message) || e) }); }

// components/core/Icon.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * Tally uses Lucide (2px round-cap stroke) for every functional glyph.
 * Glyph markup is vendored in icon-paths.js and inlined as a real <svg>, so it
 * inherits \`currentColor\`, needs no network, and survives screenshot / PDF export.
 */
function Icon({
  name,
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  style,
  ...rest
}) {
  const markup = __ds_scope.ICON_INNER[name];
  if (!markup) {
    // Unknown glyph: draw the "other" placeholder rather than a mystery block.
    return /*#__PURE__*/React.createElement("svg", _extends({
      viewBox: "0 0 24 24",
      width: size,
      height: size,
      "aria-label": name,
      role: "img",
      fill: "none",
      stroke: color,
      strokeWidth: strokeWidth,
      strokeLinecap: "round",
      strokeLinejoin: "round",
      style: {
        display: 'block',
        flex: '0 0 auto',
        ...style
      }
    }, rest), /*#__PURE__*/React.createElement("circle", {
      cx: "12",
      cy: "12",
      r: "9",
      strokeDasharray: "3 3"
    }));
  }
  return /*#__PURE__*/React.createElement("svg", _extends({
    viewBox: "0 0 24 24",
    width: size,
    height: size,
    role: "img",
    "aria-label": name,
    fill: "none",
    stroke: color,
    strokeWidth: strokeWidth,
    strokeLinecap: "round",
    strokeLinejoin: "round",
    style: {
      display: 'block',
      flex: '0 0 auto',
      ...style
    },
    dangerouslySetInnerHTML: {
      __html: markup
    }
  }, rest));
}
Object.assign(__ds_scope, { Icon });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/core/Icon.jsx", error: String((e && e.message) || e) }); }

// components/core/Button.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
const TONES = {
  action: {
    bg: 'var(--action)',
    fg: 'var(--text-on-accent)',
    edge: 'var(--action-press)'
  },
  mint: {
    bg: 'var(--mint)',
    fg: 'var(--text-on-accent)',
    edge: 'var(--mint-press)'
  },
  coral: {
    bg: 'var(--coral)',
    fg: 'var(--text-on-accent)',
    edge: 'var(--coral-press)'
  },
  lemon: {
    bg: 'var(--lemon)',
    fg: 'var(--text-on-lemon)',
    edge: 'var(--lemon-press)'
  },
  ink: {
    bg: 'var(--ink)',
    fg: 'var(--text-on-accent)',
    edge: '#000000'
  }
};
const SIZES = {
  sm: {
    h: 40,
    px: 16,
    fs: 14,
    gap: 6,
    icon: 16
  },
  md: {
    h: 48,
    px: 22,
    fs: 16,
    gap: 8,
    icon: 18
  },
  lg: {
    h: 56,
    px: 28,
    fs: 18,
    gap: 10,
    icon: 20
  }
};

/**
 * The pressable slab. Primary action grammar across all of Tally.
 */
function Button({
  children,
  variant = 'primary',
  tone = 'action',
  size = 'md',
  icon,
  iconRight,
  block,
  disabled,
  style,
  ...rest
}) {
  const [down, setDown] = React.useState(false);
  const t = TONES[tone] || TONES.action;
  const s = SIZES[size] || SIZES.md;
  const skins = {
    primary: {
      background: t.bg,
      color: t.fg,
      border: '2px solid var(--ink)',
      edge: 'var(--ink)'
    },
    solid: {
      background: t.bg,
      color: t.fg,
      border: '2px solid transparent',
      edge: t.edge
    },
    outline: {
      background: 'var(--surface-card)',
      color: 'var(--ink)',
      border: '2px solid var(--ink)',
      edge: 'var(--ink)'
    },
    soft: {
      background: 'var(--action-tint)',
      color: 'var(--action)',
      border: '2px solid transparent',
      edge: 'transparent'
    },
    ghost: {
      background: 'transparent',
      color: 'var(--action)',
      border: '2px solid transparent',
      edge: 'transparent'
    }
  };
  const k = skins[variant] || skins.primary;
  const lifted = k.edge !== 'transparent';
  return /*#__PURE__*/React.createElement("button", _extends({
    type: "button",
    disabled: disabled,
    onPointerDown: () => setDown(true),
    onPointerUp: () => setDown(false),
    onPointerLeave: () => setDown(false),
    style: {
      display: block ? 'flex' : 'inline-flex',
      width: block ? '100%' : undefined,
      alignItems: 'center',
      justifyContent: 'center',
      gap: s.gap,
      minHeight: s.h,
      padding: `0 ${s.px}px`,
      fontFamily: 'var(--font-core)',
      fontSize: s.fs,
      fontWeight: 'var(--weight-bold)',
      letterSpacing: '-0.01em',
      lineHeight: 1,
      borderRadius: 'var(--radius-button)',
      background: k.background,
      color: k.color,
      border: k.border,
      boxShadow: lifted ? `0 ${down ? 1 : 4}px 0 0 ${k.edge}` : 'none',
      transform: `translateY(${lifted && down ? 3 : 0}px)`,
      opacity: disabled ? 0.42 : 1,
      cursor: disabled ? 'not-allowed' : 'pointer',
      transition: 'transform var(--dur-instant) var(--ease-out), box-shadow var(--dur-instant) var(--ease-out), background var(--dur-fast) var(--ease-out)',
      ...style
    }
  }, rest), icon && /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: icon,
    size: s.icon
  }), children, iconRight && /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: iconRight,
    size: s.icon
  }));
}
Object.assign(__ds_scope, { Button });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/core/Button.jsx", error: String((e && e.message) || e) }); }

// components/core/Chip.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/** Tappable pill: filters, categories, split-mode switches. */
function Chip({
  children,
  icon,
  selected,
  tone = 'action',
  size = 'md',
  onClick,
  style,
  ...rest
}) {
  const [down, setDown] = React.useState(false);
  const h = size === 'sm' ? 32 : 40;
  const accent = `var(--${tone})`;
  return /*#__PURE__*/React.createElement("button", _extends({
    type: "button",
    "aria-pressed": !!selected,
    onClick: onClick,
    onPointerDown: () => setDown(true),
    onPointerUp: () => setDown(false),
    onPointerLeave: () => setDown(false),
    style: {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 6,
      height: h,
      padding: size === 'sm' ? '0 12px' : '0 16px',
      background: selected ? accent : 'var(--surface-card)',
      color: selected ? 'var(--text-on-accent)' : 'var(--ink-2)',
      border: `2px solid ${selected ? 'var(--ink)' : 'var(--hairline-strong)'}`,
      borderRadius: 'var(--radius-chip)',
      fontFamily: 'var(--font-core)',
      fontSize: size === 'sm' ? 13 : 15,
      fontWeight: 'var(--weight-bold)',
      lineHeight: 1,
      whiteSpace: 'nowrap',
      boxShadow: selected ? `0 ${down ? 1 : 3}px 0 0 var(--ink)` : 'none',
      transform: `translateY(${selected && down ? 2 : 0}px) scale(${!selected && down ? 0.94 : 1})`,
      cursor: 'pointer',
      transition: 'all var(--dur-fast) var(--ease-spring)',
      ...style
    }
  }, rest), icon && /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: icon,
    size: size === 'sm' ? 14 : 16
  }), children);
}
Object.assign(__ds_scope, { Chip });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/core/Chip.jsx", error: String((e && e.message) || e) }); }

// components/core/IconButton.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/** Circular tap target for app bars, dismiss controls and row affordances. */
function IconButton({
  name,
  size = 48,
  tone = 'ink',
  variant = 'plain',
  label,
  style,
  ...rest
}) {
  const [down, setDown] = React.useState(false);
  const fills = {
    plain: {
      bg: 'transparent',
      fg: 'var(--ink)',
      border: 'none',
      edge: false
    },
    surface: {
      bg: 'var(--surface-card)',
      fg: 'var(--ink)',
      border: '2px solid var(--ink)',
      edge: true
    },
    tint: {
      bg: 'var(--action-tint)',
      fg: 'var(--action)',
      border: 'none',
      edge: false
    },
    solid: {
      bg: `var(--${tone === 'ink' ? 'ink' : tone})`,
      fg: 'var(--text-on-accent)',
      border: '2px solid var(--ink)',
      edge: true
    }
  };
  const k = fills[variant] || fills.plain;
  return /*#__PURE__*/React.createElement("button", _extends({
    type: "button",
    "aria-label": label || name,
    onPointerDown: () => setDown(true),
    onPointerUp: () => setDown(false),
    onPointerLeave: () => setDown(false),
    style: {
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      width: size,
      height: size,
      flex: '0 0 auto',
      padding: 0,
      borderRadius: 'var(--radius-circle)',
      background: k.bg,
      color: k.fg,
      border: k.border,
      boxShadow: k.edge ? `0 ${down ? 1 : 3}px 0 0 var(--ink)` : 'none',
      transform: k.edge && down ? 'translateY(2px)' : `scale(${down ? 0.9 : 1})`,
      cursor: 'pointer',
      transition: 'transform var(--dur-instant) var(--ease-out), box-shadow var(--dur-instant) var(--ease-out)',
      ...style
    }
  }, rest), /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: name,
    size: Math.round(size * 0.44)
  }));
}
Object.assign(__ds_scope, { IconButton });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/core/IconButton.jsx", error: String((e && e.message) || e) }); }

// components/feedback/EmptyState.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/** Friendly nothing-here state. */
function EmptyState({
  icon = 'receipt',
  title,
  body,
  action,
  style,
  ...rest
}) {
  return /*#__PURE__*/React.createElement("div", _extends({
    style: {
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      textAlign: 'center',
      gap: 'var(--space-3)',
      padding: 'var(--space-10) var(--space-6)',
      ...style
    }
  }, rest), /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      width: 72,
      height: 72,
      background: 'var(--lemon)',
      color: 'var(--ink)',
      border: '2px solid var(--ink)',
      borderRadius: 'var(--radius-lg)',
      boxShadow: '0 4px 0 0 var(--ink)',
      transform: 'rotate(-4deg)'
    }
  }, /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: icon,
    size: 34
  })), /*#__PURE__*/React.createElement("h3", {
    style: {
      margin: '6px 0 0',
      fontFamily: 'var(--font-core)',
      fontSize: 'var(--text-heading-lg)',
      fontWeight: 'var(--weight-black)',
      letterSpacing: 'var(--ls-heading-lg)',
      color: 'var(--ink)'
    }
  }, title), body && /*#__PURE__*/React.createElement("p", {
    style: {
      margin: 0,
      maxWidth: 280,
      fontSize: 'var(--text-body)',
      color: 'var(--text-muted)'
    }
  }, body), action && /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: 'var(--space-2)'
    }
  }, action));
}
Object.assign(__ds_scope, { EmptyState });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/feedback/EmptyState.jsx", error: String((e && e.message) || e) }); }

// components/feedback/ProgressBar.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/** Slim slab progress meter — settle-up completion, budget used. */
function ProgressBar({
  value = 0,
  max = 100,
  tone = 'action',
  height = 14,
  label,
  style,
  ...rest
}) {
  const p = Math.min(100, Math.max(0, value / (max || 1) * 100));
  return /*#__PURE__*/React.createElement("div", _extends({
    style: {
      ...style
    }
  }, rest), label && /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      justifyContent: 'space-between',
      marginBottom: 6,
      fontSize: 'var(--text-caption)',
      fontWeight: 'var(--weight-bold)',
      color: 'var(--ink-2)'
    }
  }, /*#__PURE__*/React.createElement("span", null, label), /*#__PURE__*/React.createElement("span", null, Math.round(p), "%")), /*#__PURE__*/React.createElement("div", {
    style: {
      height,
      background: 'var(--bg-sunk)',
      border: '2px solid var(--ink)',
      borderRadius: 'var(--radius-pill)',
      overflow: 'hidden'
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      width: p + '%',
      height: '100%',
      background: `var(--${tone})`,
      borderRadius: 'var(--radius-pill)',
      transition: 'width var(--dur-slow) var(--ease-spring)'
    }
  })));
}
Object.assign(__ds_scope, { ProgressBar });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/feedback/ProgressBar.jsx", error: String((e && e.message) || e) }); }

// components/feedback/SettledBanner.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/** The celebration moment: everyone is square. */
function SettledBanner({
  message = "You're all square",
  sub,
  style,
  ...rest
}) {
  return /*#__PURE__*/React.createElement("div", _extends({
    style: {
      position: 'relative',
      overflow: 'hidden',
      display: 'flex',
      alignItems: 'center',
      gap: 'var(--space-3)',
      padding: 'var(--pad-card-lg)',
      background: 'var(--mint)',
      color: 'var(--text-on-accent)',
      border: '2px solid var(--ink)',
      borderRadius: 'var(--radius-card)',
      boxShadow: '0 4px 0 0 var(--ink)',
      animation: 'tally-settle-pop var(--dur-celebrate) var(--ease-spring)',
      ...style
    }
  }, rest), /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      width: 44,
      height: 44,
      flex: '0 0 auto',
      background: 'var(--paper)',
      color: 'var(--mint-press)',
      border: '2px solid var(--ink)',
      borderRadius: 'var(--radius-circle)'
    }
  }, /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: "party-popper",
    size: 24
  })), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    style: {
      fontFamily: 'var(--font-core)',
      fontSize: 'var(--text-heading-lg)',
      fontWeight: 'var(--weight-black)',
      letterSpacing: 'var(--ls-heading-lg)'
    }
  }, message), sub && /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 'var(--text-caption)',
      opacity: 0.9,
      marginTop: 2
    }
  }, sub)), /*#__PURE__*/React.createElement("style", null, '@keyframes tally-settle-pop{0%{transform:scale(0.9) rotate(-1deg);opacity:0}60%{transform:scale(1.03) rotate(0.4deg)}100%{transform:scale(1) rotate(0);opacity:1}}'));
}
Object.assign(__ds_scope, { SettledBanner });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/feedback/SettledBanner.jsx", error: String((e && e.message) || e) }); }

// components/feedback/Toast.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/** Transient ink capsule that drops in from the bottom. */
function Toast({
  open,
  message,
  icon = 'check',
  tone = 'ink',
  action,
  style,
  ...rest
}) {
  const bg = tone === 'mint' ? 'var(--mint)' : tone === 'coral' ? 'var(--coral)' : 'var(--ink)';
  return /*#__PURE__*/React.createElement("div", _extends({
    role: "status",
    style: {
      position: 'absolute',
      left: 'var(--gutter-screen)',
      right: 'var(--gutter-screen)',
      bottom: `calc(var(--tabbar-h) + var(--space-4))`,
      zIndex: 80,
      display: 'flex',
      alignItems: 'center',
      gap: 'var(--space-3)',
      padding: '12px 16px',
      background: bg,
      color: 'var(--text-on-accent)',
      border: '2px solid var(--ink)',
      borderRadius: 'var(--radius-pill)',
      boxShadow: 'var(--lift-toast)',
      fontFamily: 'var(--font-core)',
      fontSize: 'var(--text-body)',
      fontWeight: 'var(--weight-bold)',
      opacity: open ? 1 : 0,
      transform: open ? 'translateY(0) scale(1)' : 'translateY(20px) scale(0.96)',
      pointerEvents: open ? 'auto' : 'none',
      transition: 'opacity var(--dur-base) var(--ease-out), transform var(--dur-base) var(--ease-spring)',
      ...style
    }
  }, rest), /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: icon,
    size: 18
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      flex: 1,
      minWidth: 0
    }
  }, message), action);
}
Object.assign(__ds_scope, { Toast });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/feedback/Toast.jsx", error: String((e && e.message) || e) }); }

// components/forms/AmountInput.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/** Big money display driven by Keypad, not by a system keyboard. */
function AmountInput({
  value = '0',
  currency = '$',
  label,
  tone = 'neutral',
  style,
  ...rest
}) {
  const color = tone === 'owe' ? 'var(--you-owe)' : tone === 'owed' ? 'var(--owed-to-you)' : 'var(--ink)';
  const [bump, setBump] = React.useState(0);
  React.useEffect(() => {
    setBump(b => b + 1);
  }, [value]);
  return /*#__PURE__*/React.createElement("div", _extends({
    style: {
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      gap: 6,
      ...style
    }
  }, rest), label && /*#__PURE__*/React.createElement("span", {
    style: {
      fontFamily: 'var(--font-core)',
      fontSize: 'var(--text-label)',
      fontWeight: 'var(--weight-black)',
      letterSpacing: 'var(--ls-label)',
      textTransform: 'uppercase',
      color: 'var(--text-muted)'
    }
  }, label), /*#__PURE__*/React.createElement("div", {
    key: bump,
    style: {
      display: 'flex',
      alignItems: 'baseline',
      gap: 2,
      fontFamily: 'var(--font-money)',
      fontVariantNumeric: 'tabular-nums',
      fontWeight: 'var(--weight-bold)',
      color,
      lineHeight: 1,
      animation: 'tally-amount-bump var(--dur-fast) var(--ease-spring)'
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 'calc(var(--text-money-hero) * 0.56)',
      color: 'var(--text-muted)'
    }
  }, currency), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 'var(--text-money-hero)',
      letterSpacing: '-0.03em'
    }
  }, value)), /*#__PURE__*/React.createElement("style", null, '@keyframes tally-amount-bump{from{transform:scale(0.94)}to{transform:scale(1)}}'));
}
Object.assign(__ds_scope, { AmountInput });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/AmountInput.jsx", error: String((e && e.message) || e) }); }

// components/forms/CategoryPicker.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
const CATEGORIES = [{
  id: 'food',
  label: 'Food',
  icon: 'utensils'
}, {
  id: 'drinks',
  label: 'Drinks',
  icon: 'beer'
}, {
  id: 'transport',
  label: 'Transport',
  icon: 'car-front'
}, {
  id: 'stay',
  label: 'Stay',
  icon: 'bed-double'
}, {
  id: 'groceries',
  label: 'Groceries',
  icon: 'shopping-basket'
}, {
  id: 'fun',
  label: 'Fun',
  icon: 'ticket'
}, {
  id: 'home',
  label: 'Home',
  icon: 'house'
}, {
  id: 'other',
  label: 'Other',
  icon: 'circle-dashed'
}];

/** Scrolling chip row of expense categories. */
function CategoryPicker({
  value,
  onChange,
  categories = CATEGORIES,
  style,
  ...rest
}) {
  return /*#__PURE__*/React.createElement("div", _extends({
    style: {
      display: 'flex',
      gap: 'var(--gap-inline)',
      overflowX: 'auto',
      paddingBottom: 6,
      WebkitOverflowScrolling: 'touch',
      ...style
    }
  }, rest), categories.map(c => /*#__PURE__*/React.createElement(__ds_scope.Chip, {
    key: c.id,
    icon: c.icon,
    selected: value === c.id,
    onClick: () => onChange && onChange(c.id)
  }, c.label)));
}
Object.assign(__ds_scope, { CATEGORIES, CategoryPicker });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/CategoryPicker.jsx", error: String((e && e.message) || e) }); }

// components/forms/Input.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/** Text field. Tally uses these sparingly — prefer chips, steppers and drags. */
function Input({
  label,
  icon,
  hint,
  error,
  value,
  onChange,
  placeholder,
  type = 'text',
  style,
  ...rest
}) {
  const [focus, setFocus] = React.useState(false);
  const border = error ? 'var(--coral)' : focus ? 'var(--action)' : 'var(--hairline-strong)';
  return /*#__PURE__*/React.createElement("label", {
    style: {
      display: 'flex',
      flexDirection: 'column',
      gap: 6,
      ...style
    }
  }, label && /*#__PURE__*/React.createElement("span", {
    style: {
      fontFamily: 'var(--font-core)',
      fontSize: 'var(--text-label)',
      fontWeight: 'var(--weight-black)',
      letterSpacing: 'var(--ls-label)',
      textTransform: 'uppercase',
      color: 'var(--text-muted)'
    }
  }, label), /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 10,
      minHeight: 52,
      padding: '0 14px',
      background: 'var(--surface-card)',
      border: `2px solid ${border}`,
      borderRadius: 'var(--radius-input)',
      boxShadow: focus ? '0 3px 0 0 var(--ink)' : 'none',
      transition: 'border-color var(--dur-fast) var(--ease-out), box-shadow var(--dur-fast) var(--ease-out)'
    }
  }, icon && /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: icon,
    size: 18,
    color: "var(--ink-3)"
  }), /*#__PURE__*/React.createElement("input", _extends({
    type: type,
    value: value,
    placeholder: placeholder,
    onChange: onChange,
    onFocus: () => setFocus(true),
    onBlur: () => setFocus(false),
    style: {
      flex: 1,
      minWidth: 0,
      border: 'none',
      outline: 'none',
      background: 'transparent',
      fontFamily: 'var(--font-core)',
      fontSize: 'var(--text-body-lg)',
      fontWeight: 'var(--weight-medium)',
      color: 'var(--ink)',
      padding: 0
    }
  }, rest))), (hint || error) && /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 'var(--text-caption)',
      color: error ? 'var(--coral)' : 'var(--text-muted)'
    }
  }, error || hint));
}
Object.assign(__ds_scope, { Input });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/Input.jsx", error: String((e && e.message) || e) }); }

// components/forms/Keypad.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
const KEYS = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '0', 'del'];

/** Custom numeric pad — Tally never opens the OS keyboard for money. */
function Keypad({
  onKey,
  style,
  ...rest
}) {
  const [down, setDown] = React.useState(null);
  return /*#__PURE__*/React.createElement("div", _extends({
    style: {
      display: 'grid',
      gridTemplateColumns: 'repeat(3, 1fr)',
      gap: 'var(--space-3)',
      width: '100%',
      ...style
    }
  }, rest), KEYS.map(k => /*#__PURE__*/React.createElement("button", {
    key: k,
    type: "button",
    "aria-label": k === 'del' ? 'Delete' : k,
    onClick: () => onKey && onKey(k),
    onPointerDown: () => setDown(k),
    onPointerUp: () => setDown(null),
    onPointerLeave: () => setDown(null),
    style: {
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: 56,
      background: k === 'del' ? 'var(--bg-sunk)' : 'var(--surface-card)',
      color: 'var(--ink)',
      border: '2px solid var(--ink)',
      borderRadius: 'var(--radius-md)',
      fontFamily: 'var(--font-money)',
      fontSize: 24,
      fontWeight: 'var(--weight-bold)',
      boxShadow: `0 ${down === k ? 1 : 3}px 0 0 var(--ink)`,
      transform: down === k ? 'translateY(2px)' : 'none',
      cursor: 'pointer',
      transition: 'transform var(--dur-instant) var(--ease-out), box-shadow var(--dur-instant) var(--ease-out)'
    }
  }, k === 'del' ? /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: "delete",
    size: 22
  }) : k)));
}
Object.assign(__ds_scope, { Keypad });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/Keypad.jsx", error: String((e && e.message) || e) }); }

// components/forms/PersonToggleRow.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/** Tap avatars to include/exclude people from a split. */
function PersonToggleRow({
  people = [],
  selected = [],
  onToggle,
  size = 52,
  style,
  ...rest
}) {
  return /*#__PURE__*/React.createElement("div", _extends({
    style: {
      display: 'flex',
      gap: 'var(--space-3)',
      overflowX: 'auto',
      paddingBottom: 4,
      ...style
    }
  }, rest), people.map(p => {
    const on = selected.includes(p.name);
    return /*#__PURE__*/React.createElement("button", {
      key: p.name,
      type: "button",
      "aria-pressed": on,
      onClick: () => onToggle && onToggle(p.name),
      style: {
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: 6,
        border: 'none',
        background: 'transparent',
        padding: '2px 0',
        cursor: 'pointer',
        flex: '0 0 auto',
        transform: on ? 'translateY(-2px)' : 'none',
        transition: 'transform var(--dur-fast) var(--ease-spring)'
      }
    }, /*#__PURE__*/React.createElement(__ds_scope.Avatar, {
      name: p.name,
      hue: p.hue,
      size: size,
      selected: on,
      dimmed: !on
    }), /*#__PURE__*/React.createElement("span", {
      style: {
        fontFamily: 'var(--font-core)',
        fontSize: 'var(--text-caption)',
        fontWeight: on ? 'var(--weight-black)' : 'var(--weight-medium)',
        color: on ? 'var(--ink)' : 'var(--text-subtle)',
        maxWidth: size + 16,
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap'
      }
    }, p.name.split(' ')[0]));
  }));
}
Object.assign(__ds_scope, { PersonToggleRow });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/PersonToggleRow.jsx", error: String((e && e.message) || e) }); }

// components/forms/SplitBar.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/**
 * Draggable proportional split. One horizontal bar, one handle between each
 * neighbouring pair. This is Tally's signature interaction.
 */
function SplitBar({
  people = [],
  total = 100,
  onChange,
  height = 56,
  showLabels = true,
  style,
  ...rest
}) {
  const ref = React.useRef(null);
  const [drag, setDrag] = React.useState(null);
  const sum = people.reduce((a, p) => a + (p.value || 0), 0) || 1;
  const pct = people.map(p => (p.value || 0) / sum * 100);
  const move = clientX => {
    if (drag == null || !ref.current || !onChange) return;
    const r = ref.current.getBoundingClientRect();
    const at = Math.min(100, Math.max(0, (clientX - r.left) / r.width * 100));
    const before = pct.slice(0, drag).reduce((a, b) => a + b, 0);
    const pair = pct[drag] + pct[drag + 1];
    const left = Math.min(Math.max(at - before, 2), pair - 2);
    const next = people.map((p, i) => i === drag ? {
      ...p,
      value: left / 100 * sum
    } : i === drag + 1 ? {
      ...p,
      value: (pair - left) / 100 * sum
    } : p);
    onChange(next);
  };
  React.useEffect(() => {
    if (drag == null) return;
    const mv = e => move(e.touches ? e.touches[0].clientX : e.clientX);
    const up = () => setDrag(null);
    window.addEventListener('pointermove', mv);
    window.addEventListener('pointerup', up);
    return () => {
      window.removeEventListener('pointermove', mv);
      window.removeEventListener('pointerup', up);
    };
  });
  let acc = 0;
  const edges = pct.slice(0, -1).map(p => acc += p);
  return /*#__PURE__*/React.createElement("div", _extends({
    style: {
      ...style
    }
  }, rest), /*#__PURE__*/React.createElement("div", {
    ref: ref,
    style: {
      position: 'relative',
      display: 'flex',
      height,
      width: '100%',
      border: '2px solid var(--ink)',
      borderRadius: 'var(--radius-md)',
      overflow: 'hidden',
      boxShadow: '0 3px 0 0 var(--ink)',
      background: 'var(--bg-sunk)',
      touchAction: 'none',
      userSelect: 'none'
    }
  }, people.map((p, i) => /*#__PURE__*/React.createElement("div", {
    key: p.name + i,
    style: {
      width: pct[i] + '%',
      background: `var(--person-${((p.hue || 1) - 1) % 8 + 1})`,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      transition: drag == null ? 'width var(--dur-base) var(--ease-spring)' : 'none',
      overflow: 'hidden'
    }
  }, pct[i] > 12 && /*#__PURE__*/React.createElement("span", {
    style: {
      fontFamily: 'var(--font-money)',
      fontSize: 13,
      fontWeight: 'var(--weight-bold)',
      color: p.hue === 3 ? 'var(--ink)' : 'var(--text-on-accent)',
      whiteSpace: 'nowrap'
    }
  }, Math.round(pct[i]), "%"))), edges.map((x, i) => /*#__PURE__*/React.createElement("span", {
    key: i,
    onPointerDown: e => {
      e.preventDefault();
      setDrag(i);
    },
    style: {
      position: 'absolute',
      top: -2,
      bottom: -2,
      left: `calc(${x}% - 13px)`,
      width: 26,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      cursor: 'ew-resize',
      touchAction: 'none'
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      width: 14,
      height: height - 12,
      borderRadius: 'var(--radius-pill)',
      background: 'var(--surface-card)',
      border: '2px solid var(--ink)',
      transform: drag === i ? 'scaleY(1.12) scaleX(1.15)' : 'none',
      boxShadow: drag === i ? 'var(--lift-drag)' : 'none',
      transition: 'transform var(--dur-fast) var(--ease-spring)'
    }
  })))), showLabels && /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 'var(--space-4)',
      marginTop: 'var(--space-3)',
      flexWrap: 'wrap'
    }
  }, people.map((p, i) => /*#__PURE__*/React.createElement("span", {
    key: p.name + i,
    style: {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 6
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      width: 10,
      height: 10,
      borderRadius: 'var(--radius-circle)',
      background: `var(--person-${((p.hue || 1) - 1) % 8 + 1})`,
      border: '1.5px solid var(--ink)'
    }
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 'var(--text-caption)',
      fontWeight: 'var(--weight-semibold)',
      color: 'var(--ink-2)'
    }
  }, p.name), /*#__PURE__*/React.createElement("span", {
    style: {
      fontFamily: 'var(--font-money)',
      fontVariantNumeric: 'tabular-nums',
      fontSize: 'var(--text-money-sm)',
      fontWeight: 'var(--weight-bold)',
      color: 'var(--ink)'
    }
  }, "$", ((p.value || 0) / sum * total).toFixed(2))))));
}
Object.assign(__ds_scope, { SplitBar });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/SplitBar.jsx", error: String((e && e.message) || e) }); }

// components/forms/Stepper.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/** +/− control for share counts. Tap, never type. */
function Stepper({
  value = 1,
  min = 0,
  max = 99,
  onChange,
  suffix,
  style,
  ...rest
}) {
  const set = v => onChange && onChange(Math.min(max, Math.max(min, v)));
  return /*#__PURE__*/React.createElement("div", _extends({
    style: {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 'var(--space-3)',
      ...style
    }
  }, rest), /*#__PURE__*/React.createElement(__ds_scope.IconButton, {
    name: "minus",
    size: 40,
    variant: "surface",
    label: "Decrease",
    onClick: () => set(value - 1)
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      minWidth: 44,
      textAlign: 'center',
      fontFamily: 'var(--font-money)',
      fontVariantNumeric: 'tabular-nums',
      fontSize: 'var(--text-money-lg)',
      fontWeight: 'var(--weight-bold)',
      color: 'var(--ink)',
      lineHeight: 1
    }
  }, value, suffix), /*#__PURE__*/React.createElement(__ds_scope.IconButton, {
    name: "plus",
    size: 40,
    variant: "surface",
    label: "Increase",
    onClick: () => set(value + 1)
  }));
}
Object.assign(__ds_scope, { Stepper });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/forms/Stepper.jsx", error: String((e && e.message) || e) }); }

// components/lists/BalanceRow.jsx
try { (() => {
/** "Who owes who" row with an inline settle action. */
function BalanceRow({
  name,
  hue = 1,
  amount = 0,
  onSettle,
  size = 'lg',
  divider = true,
  style
}) {
  const owed = amount >= 0;
  return /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 'var(--space-3)',
      padding: '12px var(--pad-card)',
      minHeight: 64,
      borderBottom: divider ? '1.5px solid var(--border-soft)' : 'none',
      ...style
    }
  }, /*#__PURE__*/React.createElement(__ds_scope.Avatar, {
    name: name,
    hue: hue,
    size: 40
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 'var(--text-heading-sm)',
      fontWeight: 'var(--weight-bold)',
      color: 'var(--ink)',
      lineHeight: 1.2,
      overflowWrap: 'anywhere'
    }
  }, name), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 'var(--text-caption)',
      color: 'var(--text-muted)',
      marginTop: 2
    }
  }, amount === 0 ? 'all square' : owed ? 'owes you' : 'you owe')), /*#__PURE__*/React.createElement(__ds_scope.Amount, {
    value: amount,
    tone: amount === 0 ? 'settled' : owed ? 'owed' : 'owe',
    size: size
  }), onSettle && amount !== 0 && /*#__PURE__*/React.createElement(__ds_scope.Button, {
    size: "sm",
    variant: owed ? 'soft' : 'solid',
    tone: owed ? 'action' : 'mint',
    onClick: onSettle
  }, owed ? 'Remind' : 'Pay'));
}
Object.assign(__ds_scope, { BalanceRow });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/lists/BalanceRow.jsx", error: String((e && e.message) || e) }); }

// components/lists/GroupCard.jsx
try { (() => {
/** Home-screen tile for one group. */
function GroupCard({
  name,
  icon = 'users',
  hue = 6,
  members = [],
  balance = 0,
  onClick,
  style
}) {
  const settled = Math.abs(balance) < 0.005;
  return /*#__PURE__*/React.createElement(__ds_scope.Card, {
    pressable: true,
    lift: 4,
    onClick: onClick,
    style: {
      display: 'flex',
      flexDirection: 'column',
      gap: 'var(--space-3)',
      ...style
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 'var(--space-3)'
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      width: 44,
      height: 44,
      flex: '0 0 auto',
      background: `var(--person-${hue})`,
      border: '2px solid var(--ink)',
      borderRadius: 'var(--radius-md)',
      color: hue === 3 ? 'var(--ink)' : 'var(--text-on-accent)'
    }
  }, /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: icon,
    size: 22
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 'var(--text-heading-lg)',
      fontWeight: 'var(--weight-black)',
      letterSpacing: 'var(--ls-heading-lg)',
      color: 'var(--ink)',
      overflow: 'hidden',
      textOverflow: 'ellipsis',
      whiteSpace: 'nowrap'
    }
  }, name), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 'var(--text-caption)',
      color: 'var(--text-muted)',
      marginTop: 2
    }
  }, members.length, " ", members.length === 1 ? 'person' : 'people')), /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: "chevron-right",
    size: 20,
    color: "var(--ink-4)"
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      gap: 'var(--space-3)'
    }
  }, /*#__PURE__*/React.createElement(__ds_scope.AvatarStack, {
    people: members,
    size: 30,
    max: 5
  }), settled ? /*#__PURE__*/React.createElement(__ds_scope.Badge, {
    tone: "mint"
  }, "All square") : /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'flex',
      alignItems: 'baseline',
      gap: 6
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 11,
      fontWeight: 'var(--weight-black)',
      letterSpacing: '0.05em',
      textTransform: 'uppercase',
      color: 'var(--text-subtle)'
    }
  }, balance > 0 ? 'you get' : 'you owe'), /*#__PURE__*/React.createElement(__ds_scope.Amount, {
    value: balance,
    tone: balance > 0 ? 'owed' : 'owe',
    size: "lg"
  }))));
}
Object.assign(__ds_scope, { GroupCard });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/lists/GroupCard.jsx", error: String((e && e.message) || e) }); }

// components/lists/ListRow.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/** Generic row: leading slot, title + subtitle, trailing slot, optional chevron. */
function ListRow({
  leading,
  title,
  subtitle,
  trailing,
  chevron,
  onClick,
  divider = true,
  style,
  ...rest
}) {
  const [down, setDown] = React.useState(false);
  return /*#__PURE__*/React.createElement("div", _extends({
    onClick: onClick,
    onPointerDown: onClick ? () => setDown(true) : undefined,
    onPointerUp: onClick ? () => setDown(false) : undefined,
    onPointerLeave: onClick ? () => setDown(false) : undefined,
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 'var(--space-3)',
      minHeight: 'var(--tap-min)',
      padding: '12px var(--pad-card)',
      borderBottom: divider ? '1.5px solid var(--border-soft)' : 'none',
      background: down ? 'var(--surface-card-hover)' : 'transparent',
      cursor: onClick ? 'pointer' : undefined,
      transition: 'background var(--dur-fast) var(--ease-out)',
      ...style
    }
  }, rest), leading, /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      fontFamily: 'var(--font-core)',
      fontSize: 'var(--text-heading-sm)',
      fontWeight: 'var(--weight-bold)',
      color: 'var(--ink)',
      letterSpacing: 'var(--ls-heading-sm)',
      overflow: 'hidden',
      textOverflow: 'ellipsis',
      whiteSpace: 'nowrap'
    }
  }, title), subtitle && /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 'var(--text-caption)',
      color: 'var(--text-muted)',
      marginTop: 2,
      overflow: 'hidden',
      textOverflow: 'ellipsis',
      whiteSpace: 'nowrap'
    }
  }, subtitle)), trailing, chevron && /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: "chevron-right",
    size: 18,
    color: "var(--ink-4)"
  }));
}
Object.assign(__ds_scope, { ListRow });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/lists/ListRow.jsx", error: String((e && e.message) || e) }); }

// components/lists/ExpenseRow.jsx
try { (() => {
const CAT_HUE = {
  food: 2,
  drinks: 3,
  transport: 5,
  stay: 7,
  groceries: 4,
  fun: 8,
  home: 6,
  other: 1
};
const CAT_ICON = {
  food: 'utensils',
  drinks: 'beer',
  transport: 'car-front',
  stay: 'bed-double',
  groceries: 'shopping-basket',
  fun: 'ticket',
  home: 'house',
  other: 'circle-dashed'
};

/** One expense in a group feed: category disc, title, who paid, your share. */
function ExpenseRow({
  title,
  category = 'other',
  paidBy,
  date,
  yourShare = 0,
  settled,
  onClick,
  divider = true,
  style
}) {
  const hue = CAT_HUE[category] || 1;
  return /*#__PURE__*/React.createElement(__ds_scope.ListRow, {
    onClick: onClick,
    divider: divider,
    style: style,
    leading: /*#__PURE__*/React.createElement("span", {
      style: {
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: 44,
        height: 44,
        flex: '0 0 auto',
        background: `var(--person-${hue})`,
        border: '2px solid var(--ink)',
        borderRadius: 'var(--radius-md)',
        color: hue === 3 ? 'var(--ink)' : 'var(--text-on-accent)'
      }
    }, /*#__PURE__*/React.createElement(__ds_scope.Icon, {
      name: CAT_ICON[category] || 'circle-dashed',
      size: 22
    })),
    title: title,
    subtitle: [paidBy && `${paidBy} paid`, date].filter(Boolean).join(' · '),
    trailing: /*#__PURE__*/React.createElement("span", {
      style: {
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'flex-end',
        gap: 2
      }
    }, /*#__PURE__*/React.createElement(__ds_scope.Amount, {
      value: yourShare,
      tone: settled ? 'settled' : yourShare < 0 ? 'owe' : 'owed',
      showSign: !settled
    }), /*#__PURE__*/React.createElement("span", {
      style: {
        fontSize: 11,
        fontWeight: 'var(--weight-bold)',
        letterSpacing: '0.05em',
        textTransform: 'uppercase',
        color: 'var(--text-subtle)'
      }
    }, settled ? 'settled' : yourShare < 0 ? 'you owe' : 'you get'))
  });
}
Object.assign(__ds_scope, { ExpenseRow });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/lists/ExpenseRow.jsx", error: String((e && e.message) || e) }); }

// components/navigation/AppBar.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/** Top bar. Title left-aligned and heavy; actions right. */
function AppBar({
  title,
  subtitle,
  onBack,
  actions,
  tone = 'paper',
  style,
  ...rest
}) {
  const dark = tone === 'ink';
  return /*#__PURE__*/React.createElement("header", _extends({
    style: {
      position: 'sticky',
      top: 0,
      zIndex: 20,
      display: 'flex',
      alignItems: 'center',
      gap: 'var(--space-2)',
      minHeight: 'var(--appbar-h)',
      padding: '8px var(--gutter-screen)',
      background: dark ? 'var(--ink)' : 'color-mix(in srgb, var(--paper) 88%, transparent)',
      backdropFilter: dark ? undefined : 'saturate(180%) blur(14px)',
      WebkitBackdropFilter: dark ? undefined : 'saturate(180%) blur(14px)',
      borderBottom: `2px solid ${dark ? 'var(--ink)' : 'var(--border-soft)'}`,
      color: dark ? 'var(--text-on-accent)' : 'var(--ink)',
      ...style
    }
  }, rest), onBack && /*#__PURE__*/React.createElement(__ds_scope.IconButton, {
    name: "arrow-left",
    size: 44,
    label: "Back",
    onClick: onBack,
    style: {
      marginLeft: -10,
      color: 'inherit'
    }
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      fontFamily: 'var(--font-core)',
      fontSize: 'var(--text-title)',
      fontWeight: 'var(--weight-black)',
      letterSpacing: 'var(--ls-title)',
      lineHeight: 1.1,
      overflow: 'hidden',
      textOverflow: 'ellipsis',
      whiteSpace: 'nowrap'
    }
  }, title), subtitle && /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 'var(--text-caption)',
      color: dark ? 'var(--ink-4)' : 'var(--text-muted)',
      marginTop: 1
    }
  }, subtitle)), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 'var(--space-1)'
    }
  }, actions));
}
Object.assign(__ds_scope, { AppBar });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/navigation/AppBar.jsx", error: String((e && e.message) || e) }); }

// components/navigation/Sheet.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/** Bottom sheet with a grab handle. Every create/edit flow lives in one. */
function Sheet({
  open,
  onClose,
  title,
  children,
  footer,
  height = 'auto',
  style,
  ...rest
}) {
  return /*#__PURE__*/React.createElement("div", {
    "aria-hidden": !open,
    style: {
      position: 'absolute',
      inset: 0,
      zIndex: 60,
      pointerEvents: open ? 'auto' : 'none'
    }
  }, /*#__PURE__*/React.createElement("div", {
    onClick: onClose,
    style: {
      position: 'absolute',
      inset: 0,
      background: 'var(--scrim)',
      opacity: open ? 1 : 0,
      transition: 'opacity var(--dur-slow) var(--ease-out)'
    }
  }), /*#__PURE__*/React.createElement("section", _extends({
    style: {
      position: 'absolute',
      left: 0,
      right: 0,
      bottom: 0,
      maxHeight: '92%',
      height,
      display: 'flex',
      flexDirection: 'column',
      background: 'var(--paper)',
      borderTop: '2px solid var(--ink)',
      borderRadius: 'var(--radius-sheet) var(--radius-sheet) 0 0',
      boxShadow: 'var(--lift-sheet)',
      transform: open ? 'translateY(0)' : 'translateY(102%)',
      transition: `transform var(--dur-slow) ${open ? 'var(--ease-spring)' : 'var(--ease-out)'}`,
      ...style
    }
  }, rest), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      justifyContent: 'center',
      padding: '10px 0 2px'
    }
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      width: 44,
      height: 5,
      borderRadius: 'var(--radius-pill)',
      background: 'var(--hairline-strong)'
    }
  })), title && /*#__PURE__*/React.createElement("h2", {
    style: {
      margin: 0,
      padding: '6px var(--gutter-screen) 10px',
      fontFamily: 'var(--font-core)',
      fontSize: 'var(--text-title)',
      fontWeight: 'var(--weight-black)',
      letterSpacing: 'var(--ls-title)',
      color: 'var(--ink)'
    }
  }, title), /*#__PURE__*/React.createElement("div", {
    style: {
      flex: 1,
      minHeight: 0,
      overflowY: 'auto',
      padding: '0 var(--gutter-screen) var(--space-4)'
    }
  }, children), footer && /*#__PURE__*/React.createElement("div", {
    style: {
      padding: 'var(--space-3) var(--gutter-screen) calc(var(--space-4) + var(--safe-bottom))',
      borderTop: '1.5px solid var(--border-soft)',
      background: 'var(--paper)'
    }
  }, footer)));
}
Object.assign(__ds_scope, { Sheet });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/navigation/Sheet.jsx", error: String((e && e.message) || e) }); }

// components/navigation/TabBar.jsx
try { (() => {
function _extends() { return _extends = Object.assign ? Object.assign.bind() : function (n) { for (var e = 1; e < arguments.length; e++) { var t = arguments[e]; for (var r in t) ({}).hasOwnProperty.call(t, r) && (n[r] = t[r]); } return n; }, _extends.apply(null, arguments); }
/** Bottom tab bar with a centre action slab. */
function TabBar({
  tabs = [],
  value,
  onChange,
  centerAction,
  style,
  ...rest
}) {
  const half = Math.ceil(tabs.length / 2);
  const groups = centerAction ? [tabs.slice(0, half), tabs.slice(half)] : [tabs];
  const [down, setDown] = React.useState(false);
  const Tab = t => {
    const on = t.id === value;
    return /*#__PURE__*/React.createElement("button", {
      key: t.id,
      type: "button",
      onClick: () => onChange && onChange(t.id),
      style: {
        flex: 1,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 3,
        minHeight: 'var(--tabbar-h)',
        border: 'none',
        background: 'transparent',
        cursor: 'pointer',
        color: on ? 'var(--action)' : 'var(--ink-4)',
        transition: 'color var(--dur-fast) var(--ease-out)'
      }
    }, /*#__PURE__*/React.createElement("span", {
      style: {
        transform: on ? 'translateY(-1px) scale(1.08)' : 'none',
        transition: 'transform var(--dur-fast) var(--ease-spring)'
      }
    }, /*#__PURE__*/React.createElement(__ds_scope.Icon, {
      name: t.icon,
      size: 24
    })), /*#__PURE__*/React.createElement("span", {
      style: {
        fontFamily: 'var(--font-core)',
        fontSize: 11,
        fontWeight: on ? 'var(--weight-black)' : 'var(--weight-semibold)',
        letterSpacing: '0.01em'
      }
    }, t.label));
  };
  return /*#__PURE__*/React.createElement("nav", _extends({
    style: {
      position: 'sticky',
      bottom: 0,
      zIndex: 20,
      display: 'flex',
      alignItems: 'center',
      background: 'var(--surface-card)',
      borderTop: '2px solid var(--ink)',
      paddingBottom: 'var(--safe-bottom)',
      ...style
    }
  }, rest), groups[0].map(Tab), centerAction && /*#__PURE__*/React.createElement("button", {
    type: "button",
    onClick: centerAction.onClick,
    "aria-label": centerAction.label,
    onPointerDown: () => setDown(true),
    onPointerUp: () => setDown(false),
    onPointerLeave: () => setDown(false),
    style: {
      flex: '0 0 auto',
      width: 60,
      height: 60,
      marginTop: -22,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'var(--action)',
      color: 'var(--text-on-accent)',
      border: '2.5px solid var(--ink)',
      borderRadius: 'var(--radius-circle)',
      boxShadow: `0 ${down ? 1 : 4}px 0 0 var(--ink)`,
      transform: down ? 'translateY(3px)' : 'none',
      cursor: 'pointer',
      transition: 'transform var(--dur-instant) var(--ease-out), box-shadow var(--dur-instant) var(--ease-out)'
    }
  }, /*#__PURE__*/React.createElement(__ds_scope.Icon, {
    name: centerAction.icon || 'plus',
    size: 30
  })), groups[1] && groups[1].map(Tab));
}
Object.assign(__ds_scope, { TabBar });
})(); } catch (e) { __ds_ns.__errors.push({ path: "components/navigation/TabBar.jsx", error: String((e && e.message) || e) }); }

// ui_kits/mobile-app/App.jsx
try { (() => {
const T2 = window.TallyDesignSystem_ae2e12;
const {
  AppBar: Bar,
  TabBar: Tabs,
  IconButton: IB,
  Toast: Snack
} = T2;
const TABS = [{
  id: 'groups',
  label: 'Groups',
  icon: 'users'
}, {
  id: 'activity',
  label: 'Activity',
  icon: 'clock'
}, {
  id: 'friends',
  label: 'Friends',
  icon: 'user-round'
}, {
  id: 'you',
  label: 'You',
  icon: 'circle-user'
}];
function TallyApp() {
  const {
    GROUPS,
    MEMBERS
  } = window.TallyData;
  const [groups, setGroups] = React.useState(GROUPS);
  const [tab, setTab] = React.useState('groups');
  const [view, setView] = React.useState({
    name: 'home'
  }); // home | overall | group
  const [sheet, setSheet] = React.useState(null);
  const [expense, setExpense] = React.useState(null);
  const [toast, setToast] = React.useState(null);
  const group = view.name === 'group' ? groups.find(g => g.id === view.id) : null;
  const say = (message, tone = 'mint', icon = 'check') => {
    setToast({
      message,
      tone,
      icon
    });
    clearTimeout(window.__tallyToast);
    window.__tallyToast = setTimeout(() => setToast(null), 2400);
  };
  const openGroup = id => {
    setTab('groups');
    setView({
      name: 'group',
      id
    });
  };
  const back = () => {
    if (view.name === 'group') setView({
      name: 'home'
    });else if (view.name === 'overall') setView({
      name: 'home'
    });
  };
  const save = ({
    title,
    total,
    cat,
    payer,
    splits
  }) => {
    const target = group || groups[0];
    const mine = (splits.find(s => s.name === 'You') || {
      value: 0
    }).value;
    const yourShare = payer === 'You' ? total - mine : -mine;
    setGroups(gs => gs.map(g => g.id !== target.id ? g : {
      ...g,
      balance: Number((g.balance + yourShare).toFixed(2)),
      expenses: [{
        id: Date.now(),
        title,
        category: cat,
        paidBy: payer,
        date: 'Just now',
        day: 'Today',
        total,
        yourShare: Number(yourShare.toFixed(2)),
        splits
      }, ...g.expenses]
    }));
    setSheet(null);
    say('Split saved');
  };
  const settleAll = () => {
    const target = group || groups[0];
    setGroups(gs => gs.map(g => g.id !== target.id ? g : {
      ...g,
      balance: 0,
      balances: g.balances.map(b => ({
        ...b,
        amount: 0
      })),
      expenses: g.expenses.map(e => ({
        ...e,
        settled: true,
        yourShare: 0
      }))
    }));
    setSheet(null);
    say("You're all square", 'mint', 'party-popper');
  };
  const deleteExpense = () => {
    const id = expense.id;
    setGroups(gs => gs.map(g => ({
      ...g,
      expenses: g.expenses.filter(e => e.id !== id)
    })));
    setExpense(null);
    say('Expense deleted', 'ink', 'trash-2');
  };
  const title = group ? group.name : view.name === 'overall' ? 'Overall' : tab === 'groups' ? 'Tally' : tab === 'activity' ? 'Activity' : tab === 'friends' ? 'Friends' : 'You';
  const subtitle = group ? `${group.members.length} people · since ${group.started}` : view.name === 'overall' ? 'Every group, every person' : tab === 'groups' ? 'Split it, sorted' : null;
  return /*#__PURE__*/React.createElement("div", {
    style: {
      position: 'relative',
      display: 'flex',
      flexDirection: 'column',
      height: '100%',
      background: 'var(--paper)',
      overflow: 'hidden'
    }
  }, /*#__PURE__*/React.createElement(Bar, {
    title: title,
    subtitle: subtitle,
    onBack: view.name !== 'home' ? back : undefined,
    actions: group ? /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement(IB, {
      name: "user-plus",
      size: 42,
      label: "Invite",
      onClick: () => say('Invite link copied', 'ink', 'link')
    }), /*#__PURE__*/React.createElement(IB, {
      name: "ellipsis",
      size: 42,
      label: "More"
    })) : view.name === 'overall' ? /*#__PURE__*/React.createElement(IB, {
      name: "share-2",
      size: 42,
      label: "Share summary",
      onClick: () => say('Summary copied', 'ink', 'link')
    }) : /*#__PURE__*/React.createElement(IB, {
      name: "search",
      size: 42,
      label: "Search"
    })
  }), group ? /*#__PURE__*/React.createElement(GroupDetail, {
    group: group,
    onSettle: () => setSheet('settle'),
    onOpenExpense: setExpense
  }) : view.name === 'overall' ? /*#__PURE__*/React.createElement(OverallScreen, {
    groups: groups,
    members: MEMBERS,
    onOpenGroup: openGroup,
    onSettle: () => {
      setView({
        name: 'group',
        id: groups[0].id
      });
      setSheet('settle');
    }
  }) : tab === 'groups' ? /*#__PURE__*/React.createElement(GroupsHome, {
    groups: groups,
    onOpen: openGroup,
    onOverall: () => setView({
      name: 'overall'
    })
  }) : tab === 'activity' ? /*#__PURE__*/React.createElement(Activity, {
    groups: groups,
    onOpenExpense: setExpense
  }) : /*#__PURE__*/React.createElement(You, {
    members: MEMBERS
  }), /*#__PURE__*/React.createElement(Tabs, {
    tabs: TABS,
    value: view.name === 'home' ? tab : 'groups',
    onChange: id => {
      setView({
        name: 'home'
      });
      setTab(id);
    },
    centerAction: {
      icon: 'plus',
      label: 'Add expense',
      onClick: () => {
        if (!group) setView({
          name: 'group',
          id: groups[0].id
        });
        setSheet('add');
      }
    }
  }), /*#__PURE__*/React.createElement(AddExpenseSheet, {
    open: sheet === 'add',
    onClose: () => setSheet(null),
    group: group || groups[0],
    onSave: save
  }), /*#__PURE__*/React.createElement(SettleUpSheet, {
    open: sheet === 'settle',
    onClose: () => setSheet(null),
    group: group || groups[0],
    onDone: settleAll
  }), /*#__PURE__*/React.createElement(ExpenseDetailSheet, {
    expense: expense,
    onClose: () => setExpense(null),
    onDelete: deleteExpense
  }), /*#__PURE__*/React.createElement(Snack, {
    open: !!toast,
    message: toast && toast.message,
    tone: toast && toast.tone,
    icon: toast && toast.icon
  }));
}
Object.assign(window, {
  TallyApp
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/mobile-app/App.jsx", error: String((e && e.message) || e) }); }

// ui_kits/mobile-app/Screens.jsx
try { (() => {
const T = window.TallyDesignSystem_ae2e12;
const {
  Button,
  IconButton,
  Card,
  Chip,
  Badge,
  Avatar,
  AvatarStack,
  Amount,
  Icon
} = T;
const {
  AppBar,
  TabBar,
  Sheet
} = T;
const {
  GroupCard,
  ExpenseRow,
  BalanceRow,
  ListRow
} = T;
const {
  SplitBar,
  PersonToggleRow,
  Stepper,
  Input,
  AmountInput,
  Keypad,
  CategoryPicker
} = T;
const {
  Toast,
  ProgressBar,
  EmptyState,
  SettledBanner
} = T;
const Screen = ({
  children
}) => /*#__PURE__*/React.createElement("div", {
  style: {
    flex: 1,
    minHeight: 0,
    overflowY: 'auto',
    WebkitOverflowScrolling: 'touch'
  }
}, children);
const Pad = ({
  children
}) => /*#__PURE__*/React.createElement("div", {
  style: {
    padding: '0 var(--gutter-screen) var(--space-10)',
    display: 'flex',
    flexDirection: 'column',
    gap: 'var(--stack-section)'
  }
}, children);
const SectionLabel = ({
  children,
  right
}) => /*#__PURE__*/React.createElement("div", {
  style: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 8,
    margin: '0 0 8px',
    minHeight: 26
  }
}, /*#__PURE__*/React.createElement("span", {
  style: {
    font: '800 12px/1 var(--font-core)',
    letterSpacing: '0.06em',
    textTransform: 'uppercase',
    color: 'var(--text-subtle)'
  }
}, children), right);

/* Small sunk stat tile used in the stats strips. */
const Stat = ({
  label,
  children,
  tone
}) => /*#__PURE__*/React.createElement("div", {
  style: {
    flex: 1,
    minWidth: 0,
    padding: '10px 12px',
    background: 'var(--bg-sunk)',
    border: '2px solid var(--ink)',
    borderRadius: 'var(--radius-md)'
  }
}, /*#__PURE__*/React.createElement("div", {
  style: {
    font: '800 11px/1 var(--font-core)',
    letterSpacing: '0.05em',
    textTransform: 'uppercase',
    color: 'var(--text-subtle)'
  }
}, label), /*#__PURE__*/React.createElement("div", {
  style: {
    marginTop: 6
  }
}, children));
const CAT_LABEL = {
  food: 'Food',
  drinks: 'Drinks',
  transport: 'Transport',
  stay: 'Stay',
  groceries: 'Groceries',
  fun: 'Fun',
  home: 'Home',
  other: 'Other'
};
const CAT_HUE = {
  food: 2,
  drinks: 3,
  transport: 5,
  stay: 7,
  groceries: 4,
  fun: 8,
  home: 6,
  other: 1
};
const CAT_ICON = {
  food: 'utensils',
  drinks: 'beer',
  transport: 'car-front',
  stay: 'bed-double',
  groceries: 'shopping-basket',
  fun: 'ticket',
  home: 'house',
  other: 'circle-dashed'
};
const CatDisc = ({
  category,
  size = 44,
  radius = 'var(--radius-md)'
}) => {
  const hue = CAT_HUE[category] || 1;
  return /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      width: size,
      height: size,
      flex: '0 0 auto',
      background: `var(--person-${hue})`,
      border: '2px solid var(--ink)',
      borderRadius: radius,
      color: hue === 3 ? 'var(--ink)' : 'var(--text-on-accent)'
    }
  }, /*#__PURE__*/React.createElement(Icon, {
    name: CAT_ICON[category] || 'circle-dashed',
    size: Math.round(size * 0.5)
  }));
};

/* ---------------- Groups home ---------------- */
function GroupsHome({
  groups,
  onOpen,
  onOverall
}) {
  const net = groups.reduce((a, g) => a + g.balance, 0);
  return /*#__PURE__*/React.createElement(Screen, null, /*#__PURE__*/React.createElement(Pad, null, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement(Card, {
    tone: net >= 0 ? 'mint' : 'coral',
    lift: 6,
    pressable: true,
    onClick: onOverall
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'flex-start',
      justifyContent: 'space-between',
      gap: 8
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      font: '800 12px/1 var(--font-core)',
      letterSpacing: '0.06em',
      textTransform: 'uppercase',
      color: 'var(--ink-3)'
    }
  }, net >= 0 ? 'Overall you get back' : 'Overall you owe'), /*#__PURE__*/React.createElement("span", {
    style: {
      display: 'inline-flex',
      alignItems: 'center',
      gap: 2,
      font: '800 12px/1 var(--font-core)',
      color: 'var(--action)'
    }
  }, "Details ", /*#__PURE__*/React.createElement(Icon, {
    name: "chevron-right",
    size: 14
  }))), /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: 6
    }
  }, /*#__PURE__*/React.createElement(Amount, {
    value: net,
    size: "hero",
    tone: net >= 0 ? 'owed' : 'owe'
  })), /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: 'var(--space-3)'
    }
  }, /*#__PURE__*/React.createElement(ProgressBar, {
    value: groups.filter(g => g.balance === 0).length,
    max: groups.length,
    tone: net >= 0 ? 'mint' : 'coral',
    label: "Groups settled",
    height: 12
  })))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement(SectionLabel, {
    right: /*#__PURE__*/React.createElement(Chip, {
      size: "sm",
      icon: "plus"
    }, "New group")
  }, "Your groups"), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      flexDirection: 'column',
      gap: 'var(--gap-list)'
    }
  }, groups.map(g => /*#__PURE__*/React.createElement(GroupCard, {
    key: g.id,
    name: g.name,
    icon: g.icon,
    hue: g.hue,
    members: g.members,
    balance: g.balance,
    onClick: () => onOpen(g.id)
  }))))));
}

/* ---------------- Overall balance detail ---------------- */
function OverallScreen({
  groups,
  members,
  onOpenGroup,
  onSettle
}) {
  const net = groups.reduce((a, g) => a + g.balance, 0);
  const totalSpent = groups.reduce((a, g) => a + g.expenses.reduce((b, e) => b + e.total, 0), 0);
  const yourOutlay = groups.reduce((a, g) => a + g.expenses.filter(e => e.paidBy === 'You').reduce((b, e) => b + e.total, 0), 0);

  // Net per person across every group.
  const perPerson = {};
  groups.forEach(g => g.balances.forEach(b => {
    perPerson[b.name] = perPerson[b.name] || {
      name: b.name,
      hue: b.hue,
      amount: 0,
      groups: []
    };
    perPerson[b.name].amount += b.amount;
    if (b.amount !== 0) perPerson[b.name].groups.push(g.name);
  }));
  const people = Object.values(perPerson).sort((a, b) => a.amount - b.amount);
  const owedTo = people.filter(p => p.amount < 0);
  const owedBy = people.filter(p => p.amount > 0);
  return /*#__PURE__*/React.createElement(Screen, null, /*#__PURE__*/React.createElement(Pad, null, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement(Card, {
    tone: net >= 0 ? 'mint' : 'coral',
    lift: 6
  }, /*#__PURE__*/React.createElement(SectionLabel, null, net >= 0 ? 'Overall you get back' : 'Overall you owe'), /*#__PURE__*/React.createElement(Amount, {
    value: net,
    size: "hero",
    tone: net >= 0 ? 'owed' : 'owe'
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 'var(--space-2)',
      marginTop: 'var(--space-4)'
    }
  }, /*#__PURE__*/React.createElement(Stat, {
    label: "You owe"
  }, /*#__PURE__*/React.createElement(Amount, {
    value: owedTo.reduce((a, p) => a + p.amount, 0),
    tone: "owe",
    size: "md"
  })), /*#__PURE__*/React.createElement(Stat, {
    label: "Owed to you"
  }, /*#__PURE__*/React.createElement(Amount, {
    value: owedBy.reduce((a, p) => a + p.amount, 0),
    tone: "owed",
    size: "md"
  }))))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement(SectionLabel, {
    right: /*#__PURE__*/React.createElement(Badge, {
      tone: "coral"
    }, `${owedTo.length + owedBy.length} open`)
  }, "Across everyone"), owedTo.length + owedBy.length === 0 ? /*#__PURE__*/React.createElement(SettledBanner, {
    message: "You're all square",
    sub: "Every group, everyone. Nice."
  }) : /*#__PURE__*/React.createElement(Card, {
    padded: false,
    lift: 2
  }, [...owedTo, ...owedBy].map((p, i) => /*#__PURE__*/React.createElement("div", {
    key: p.name
  }, /*#__PURE__*/React.createElement(BalanceRow, {
    name: p.name,
    hue: p.hue,
    amount: p.amount,
    onSettle: onSettle,
    size: "md",
    divider: false,
    style: {
      paddingBottom: 4
    }
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      padding: '0 var(--pad-card) 12px 68px',
      fontSize: 'var(--text-caption)',
      color: 'var(--text-subtle)',
      borderBottom: i < owedTo.length + owedBy.length - 1 ? '1.5px solid var(--border-soft)' : 'none'
    }
  }, p.groups.join(' · ')))))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement(SectionLabel, null, "By group"), /*#__PURE__*/React.createElement(Card, {
    padded: false,
    lift: 2
  }, groups.map((g, i) => /*#__PURE__*/React.createElement(ListRow, {
    key: g.id,
    onClick: () => onOpenGroup(g.id),
    chevron: true,
    divider: i < groups.length - 1,
    leading: /*#__PURE__*/React.createElement(CatDisc, {
      category: "other",
      size: 36
    }),
    title: g.name,
    subtitle: `${g.expenses.length} expenses · since ${g.started}`,
    trailing: g.balance === 0 ? /*#__PURE__*/React.createElement(Badge, {
      tone: "mint"
    }, "Square") : /*#__PURE__*/React.createElement(Amount, {
      value: g.balance,
      tone: g.balance > 0 ? 'owed' : 'owe',
      showSign: true
    })
  })))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement(SectionLabel, null, "Your share of the spending"), /*#__PURE__*/React.createElement(Card, {
    lift: 2
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 'var(--space-2)'
    }
  }, /*#__PURE__*/React.createElement(Stat, {
    label: "Group total"
  }, /*#__PURE__*/React.createElement(Amount, {
    value: totalSpent,
    size: "md"
  })), /*#__PURE__*/React.createElement(Stat, {
    label: "You fronted"
  }, /*#__PURE__*/React.createElement(Amount, {
    value: yourOutlay,
    size: "md"
  }))), /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: 'var(--space-4)'
    }
  }, /*#__PURE__*/React.createElement(ProgressBar, {
    value: yourOutlay,
    max: totalSpent,
    tone: "action",
    label: "Fronted by you"
  })), /*#__PURE__*/React.createElement("p", {
    style: {
      margin: '12px 0 0',
      fontSize: 'var(--text-caption)',
      color: 'var(--text-muted)'
    }
  }, "Across ", groups.length, " groups and ", members.length, " people. Tally never moves money \u2014 it just keeps the maths straight.")))));
}

/* ---------------- Group detail ---------------- */
function GroupDetail({
  group,
  onSettle,
  onOpenExpense
}) {
  const [filter, setFilter] = React.useState('all');
  const settled = group.balance === 0;
  const total = group.expenses.reduce((a, e) => a + e.total, 0);
  const yours = group.expenses.reduce((a, e) => a + (e.splits ? (e.splits.find(s => s.name === 'You') || {}).value || 0 : 0), 0);
  const youFronted = group.expenses.filter(e => e.paidBy === 'You').reduce((a, e) => a + e.total, 0);
  const list = group.expenses.filter(e => filter === 'all' ? true : filter === 'open' ? !e.settled : e.paidBy === 'You');
  const days = [];
  list.forEach(e => {
    const last = days[days.length - 1];
    if (last && last.day === e.day) last.items.push(e);else days.push({
      day: e.day,
      items: [e]
    });
  });
  return /*#__PURE__*/React.createElement(Screen, null, /*#__PURE__*/React.createElement(Pad, null, /*#__PURE__*/React.createElement("div", null, settled ? /*#__PURE__*/React.createElement(SettledBanner, {
    message: "You're all square",
    sub: "Nobody owes anybody. Nice."
  }) : /*#__PURE__*/React.createElement(Card, {
    tone: group.balance > 0 ? 'mint' : 'coral',
    lift: 6
  }, /*#__PURE__*/React.createElement(SectionLabel, null, group.balance > 0 ? 'You get back' : 'You owe'), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'flex-end',
      justifyContent: 'space-between',
      gap: 12
    }
  }, /*#__PURE__*/React.createElement(Amount, {
    value: group.balance,
    size: "hero",
    tone: group.balance > 0 ? 'owed' : 'owe'
  }), /*#__PURE__*/React.createElement(Button, {
    size: "sm",
    variant: "solid",
    tone: group.balance > 0 ? 'action' : 'mint',
    icon: "handshake",
    onClick: onSettle
  }, "Settle up"))), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 'var(--space-2)',
      marginTop: 'var(--space-3)'
    }
  }, /*#__PURE__*/React.createElement(Stat, {
    label: "Group spend"
  }, /*#__PURE__*/React.createElement(Amount, {
    value: total,
    size: "md"
  })), /*#__PURE__*/React.createElement(Stat, {
    label: "Your share"
  }, /*#__PURE__*/React.createElement(Amount, {
    value: yours,
    size: "md"
  })), /*#__PURE__*/React.createElement(Stat, {
    label: "You fronted"
  }, /*#__PURE__*/React.createElement(Amount, {
    value: youFronted,
    size: "md"
  })))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement(SectionLabel, {
    right: /*#__PURE__*/React.createElement(Chip, {
      size: "sm",
      icon: "user-plus"
    }, "Invite")
  }, "Who owes who"), /*#__PURE__*/React.createElement(Card, {
    padded: false,
    lift: 2
  }, group.balances.map((b, i) => /*#__PURE__*/React.createElement(BalanceRow, {
    key: b.name,
    name: b.name,
    hue: b.hue,
    amount: b.amount,
    divider: i < group.balances.length - 1
  }))), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 8,
      marginTop: 10
    }
  }, /*#__PURE__*/React.createElement(AvatarStack, {
    people: group.members,
    size: 28,
    max: 5
  }), /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 'var(--text-caption)',
      color: 'var(--text-muted)'
    }
  }, group.members.length, " people \xB7 ", group.currency, " \xB7 since ", group.started))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement(SectionLabel, {
    right: /*#__PURE__*/React.createElement(Badge, null, `${list.length} of ${group.expenses.length}`)
  }, "Expenses"), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 'var(--gap-inline)',
      overflowX: 'auto',
      paddingBottom: 8
    }
  }, /*#__PURE__*/React.createElement(Chip, {
    size: "sm",
    selected: filter === 'all',
    onClick: () => setFilter('all')
  }, "All"), /*#__PURE__*/React.createElement(Chip, {
    size: "sm",
    selected: filter === 'open',
    onClick: () => setFilter('open')
  }, "Unsettled"), /*#__PURE__*/React.createElement(Chip, {
    size: "sm",
    selected: filter === 'mine',
    onClick: () => setFilter('mine')
  }, "You paid")), list.length === 0 ? /*#__PURE__*/React.createElement(EmptyState, {
    icon: "receipt",
    title: "Nothing to show",
    body: "No expenses match that filter yet."
  }) : /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      flexDirection: 'column',
      gap: 'var(--space-4)'
    }
  }, days.map(d => /*#__PURE__*/React.createElement("div", {
    key: d.day
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      font: '800 11px/1 var(--font-core)',
      letterSpacing: '0.06em',
      textTransform: 'uppercase',
      color: 'var(--text-subtle)',
      margin: '0 0 6px 2px'
    }
  }, d.day), /*#__PURE__*/React.createElement(Card, {
    padded: false,
    lift: 2
  }, d.items.map((e, i) => /*#__PURE__*/React.createElement(ExpenseRow, {
    key: e.id,
    title: e.title,
    category: e.category,
    paidBy: e.paidBy.split(' ')[0],
    date: e.date,
    yourShare: e.yourShare,
    settled: e.settled,
    divider: i < d.items.length - 1,
    onClick: () => onOpenExpense(e)
  })))))))));
}

/* ---------------- Expense detail sheet ---------------- */
function ExpenseDetailSheet({
  expense,
  onClose,
  onDelete
}) {
  const e = expense || {};
  const splits = e.splits || [];
  const sum = splits.reduce((a, s) => a + s.value, 0) || 1;
  return /*#__PURE__*/React.createElement(Sheet, {
    open: !!expense,
    onClose: onClose,
    height: "82%",
    title: null,
    footer: /*#__PURE__*/React.createElement("div", {
      style: {
        display: 'flex',
        gap: 'var(--space-3)'
      }
    }, /*#__PURE__*/React.createElement(Button, {
      variant: "outline",
      size: "lg",
      icon: "trash-2",
      onClick: onDelete
    }), /*#__PURE__*/React.createElement(Button, {
      block: true,
      size: "lg",
      icon: "check",
      onClick: onClose
    }, "Done"))
  }, expense && /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      flexDirection: 'column',
      gap: 'var(--space-5)'
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 'var(--space-3)'
    }
  }, /*#__PURE__*/React.createElement(CatDisc, {
    category: e.category,
    size: 56,
    radius: "var(--radius-lg)"
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      minWidth: 0
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      font: '800 24px/1.1 var(--font-core)',
      letterSpacing: '-0.02em',
      color: 'var(--ink)'
    }
  }, e.title), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 'var(--text-caption)',
      color: 'var(--text-muted)',
      marginTop: 3
    }
  }, CAT_LABEL[e.category] || 'Other', " \xB7 ", e.date))), /*#__PURE__*/React.createElement(Card, {
    tone: "sunk",
    lift: 0,
    style: {
      display: 'flex',
      alignItems: 'flex-end',
      justifyContent: 'space-between',
      gap: 12
    }
  }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    style: {
      font: '800 11px/1 var(--font-core)',
      letterSpacing: '0.05em',
      textTransform: 'uppercase',
      color: 'var(--text-subtle)'
    }
  }, "Total"), /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: 6
    }
  }, /*#__PURE__*/React.createElement(Amount, {
    value: e.total,
    size: "lg"
  }))), /*#__PURE__*/React.createElement("div", {
    style: {
      textAlign: 'right'
    }
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      font: '800 11px/1 var(--font-core)',
      letterSpacing: '0.05em',
      textTransform: 'uppercase',
      color: 'var(--text-subtle)'
    }
  }, e.settled ? 'Settled' : e.yourShare < 0 ? 'You owe' : 'You get'), /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: 6
    }
  }, /*#__PURE__*/React.createElement(Amount, {
    value: e.yourShare,
    size: "lg",
    tone: e.settled ? 'settled' : e.yourShare < 0 ? 'owe' : 'owed',
    showSign: !e.settled
  })))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement(SectionLabel, null, "Paid by"), /*#__PURE__*/React.createElement(Card, {
    padded: false,
    lift: 2
  }, /*#__PURE__*/React.createElement(ListRow, {
    divider: false,
    leading: /*#__PURE__*/React.createElement(Avatar, {
      name: e.paidBy || 'You',
      hue: (splits.find(s => s.name === e.paidBy) || {}).hue || 6,
      size: 40
    }),
    title: e.paidBy,
    subtitle: "fronted the whole bill",
    trailing: /*#__PURE__*/React.createElement(Amount, {
      value: e.total
    })
  }))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement(SectionLabel, {
    right: /*#__PURE__*/React.createElement(Chip, {
      size: "sm"
    }, "Even split")
  }, "How it was split"), /*#__PURE__*/React.createElement(SplitBar, {
    people: splits,
    total: e.total,
    showLabels: false,
    height: 44
  }), /*#__PURE__*/React.createElement("div", {
    style: {
      marginTop: 'var(--space-3)'
    }
  }, /*#__PURE__*/React.createElement(Card, {
    padded: false,
    lift: 2
  }, splits.map((s, i) => /*#__PURE__*/React.createElement(ListRow, {
    key: s.name,
    divider: i < splits.length - 1,
    leading: /*#__PURE__*/React.createElement(Avatar, {
      name: s.name,
      hue: s.hue,
      size: 36
    }),
    title: s.name,
    subtitle: `${Math.round(s.value / sum * 100)}% of the bill`,
    trailing: /*#__PURE__*/React.createElement(Amount, {
      value: s.value,
      tone: s.name === e.paidBy ? 'owed' : 'neutral'
    })
  }))))), e.note && /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement(SectionLabel, null, "Note"), /*#__PURE__*/React.createElement(Card, {
    tone: "lemon",
    lift: 2
  }, /*#__PURE__*/React.createElement("span", {
    style: {
      fontSize: 'var(--text-body-lg)',
      color: 'var(--ink)'
    }
  }, e.note)))));
}

/* ---------------- Activity ---------------- */
function Activity({
  groups,
  onOpenExpense
}) {
  const all = groups.flatMap(g => g.expenses.map(e => ({
    ...e,
    group: g.name
  })));
  return /*#__PURE__*/React.createElement(Screen, null, /*#__PURE__*/React.createElement(Pad, null, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement(SectionLabel, null, "Latest across all groups"), /*#__PURE__*/React.createElement(Card, {
    padded: false,
    lift: 2
  }, all.map((e, i) => /*#__PURE__*/React.createElement(ExpenseRow, {
    key: e.id,
    title: e.title,
    category: e.category,
    paidBy: e.paidBy.split(' ')[0],
    date: `${e.group} · ${e.date}`,
    yourShare: e.yourShare,
    settled: e.settled,
    divider: i < all.length - 1,
    onClick: () => onOpenExpense(e)
  }))))));
}

/* ---------------- You / friends ---------------- */
function You({
  members
}) {
  return /*#__PURE__*/React.createElement(Screen, null, /*#__PURE__*/React.createElement(Pad, null, /*#__PURE__*/React.createElement(Card, {
    lift: 4,
    style: {
      display: 'flex',
      alignItems: 'center',
      gap: 'var(--space-4)'
    }
  }, /*#__PURE__*/React.createElement(Avatar, {
    name: "You",
    hue: 6,
    size: 64
  }), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    style: {
      font: '800 19px/1.2 var(--font-core)',
      letterSpacing: '-0.012em'
    }
  }, "You"), /*#__PURE__*/React.createElement("div", {
    style: {
      fontSize: 'var(--text-caption)',
      color: 'var(--text-muted)',
      marginTop: 2
    }
  }, "you@tally.app"))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement(SectionLabel, null, "Friends"), /*#__PURE__*/React.createElement(Card, {
    padded: false,
    lift: 2
  }, members.slice(1).map((m, i) => /*#__PURE__*/React.createElement(ListRow, {
    key: m.name,
    leading: /*#__PURE__*/React.createElement(Avatar, {
      name: m.name,
      hue: m.hue,
      size: 36
    }),
    title: m.name,
    subtitle: "2 shared groups",
    chevron: true,
    divider: i < members.length - 2
  })))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement(SectionLabel, null, "Settings"), /*#__PURE__*/React.createElement(Card, {
    padded: false,
    lift: 2
  }, /*#__PURE__*/React.createElement(ListRow, {
    title: "Currency",
    subtitle: "US Dollar",
    chevron: true
  }), /*#__PURE__*/React.createElement(ListRow, {
    title: "Notifications",
    subtitle: "Nudges on, digests off",
    chevron: true
  }), /*#__PURE__*/React.createElement(ListRow, {
    title: "Sign out",
    divider: false
  })))));
}

/* ---------------- Add expense sheet ---------------- */
function AddExpenseSheet({
  open,
  onClose,
  group,
  onSave
}) {
  const [step, setStep] = React.useState(0);
  const [digits, setDigits] = React.useState('0');
  const [cat, setCat] = React.useState('food');
  const [title, setTitle] = React.useState('');
  const [payer, setPayer] = React.useState('You');
  const [included, setIncluded] = React.useState(group.members.map(m => m.name));
  const [mode, setMode] = React.useState('even');
  const [weights, setWeights] = React.useState(group.members.map(m => ({
    ...m,
    value: 25
  })));
  React.useEffect(() => {
    if (open) {
      setStep(0);
      setDigits('0');
      setTitle('');
      setMode('even');
      setPayer('You');
      setIncluded(group.members.map(m => m.name));
      setWeights(group.members.map(m => ({
        ...m,
        value: 25
      })));
    }
  }, [open, group]);
  const total = Number(digits) || 0;
  const key = k => setDigits(v => {
    if (k === 'del') return v.length > 1 ? v.slice(0, -1) : '0';
    if (k === '.') return v.includes('.') ? v : v + '.';
    if (v.includes('.') && v.split('.')[1].length >= 2) return v;
    return v === '0' ? k : v + k;
  });
  const active = weights.filter(w => included.includes(w.name));
  const even = active.map(w => ({
    ...w,
    value: 100 / (active.length || 1)
  }));
  const shown = mode === 'even' ? even : active;
  const sum = shown.reduce((a, b) => a + b.value, 0) || 1;
  return /*#__PURE__*/React.createElement(Sheet, {
    open: open,
    onClose: onClose,
    height: "88%",
    title: step === 0 ? 'How much?' : 'Who split it?',
    footer: step === 0 ? /*#__PURE__*/React.createElement(Button, {
      block: true,
      size: "lg",
      disabled: total <= 0,
      onClick: () => setStep(1),
      iconRight: "arrow-right"
    }, "Next") : /*#__PURE__*/React.createElement("div", {
      style: {
        display: 'flex',
        gap: 'var(--space-3)'
      }
    }, /*#__PURE__*/React.createElement(Button, {
      variant: "outline",
      size: "lg",
      onClick: () => setStep(0),
      icon: "arrow-left"
    }), /*#__PURE__*/React.createElement(Button, {
      block: true,
      size: "lg",
      variant: "solid",
      tone: "mint",
      icon: "check",
      onClick: () => onSave({
        title: title || 'Expense',
        total,
        cat,
        payer,
        splits: shown.map(s => ({
          name: s.name,
          hue: s.hue,
          value: total * (s.value / sum)
        }))
      })
    }, "Save split"))
  }, step === 0 ? /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      flexDirection: 'column',
      gap: 'var(--space-4)'
    }
  }, /*#__PURE__*/React.createElement(AmountInput, {
    value: digits
  }), /*#__PURE__*/React.createElement(CategoryPicker, {
    value: cat,
    onChange: setCat
  }), /*#__PURE__*/React.createElement(Input, {
    label: "What was it?",
    icon: "receipt",
    value: title,
    placeholder: "Dinner at Sichuan Rose",
    onChange: ev => setTitle(ev.target.value)
  }), /*#__PURE__*/React.createElement(Keypad, {
    onKey: key
  })) : /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      flexDirection: 'column',
      gap: 'var(--space-5)'
    }
  }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement(SectionLabel, null, "Who paid"), /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      gap: 'var(--gap-inline)',
      overflowX: 'auto',
      paddingBottom: 4
    }
  }, group.members.map(m => /*#__PURE__*/React.createElement(Chip, {
    key: m.name,
    selected: payer === m.name,
    onClick: () => setPayer(m.name)
  }, m.name.split(' ')[0])))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement(SectionLabel, null, "Split between"), /*#__PURE__*/React.createElement(PersonToggleRow, {
    people: group.members,
    selected: included,
    onToggle: n => setIncluded(s => s.includes(n) ? s.filter(x => x !== n) : [...s, n])
  })), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement(SectionLabel, {
    right: /*#__PURE__*/React.createElement("div", {
      style: {
        display: 'flex',
        gap: 6
      }
    }, /*#__PURE__*/React.createElement(Chip, {
      size: "sm",
      selected: mode === 'even',
      onClick: () => {
        setMode('even');
        setWeights(w => w.map(x => ({
          ...x,
          value: 25
        })));
      }
    }, "Evenly"), /*#__PURE__*/React.createElement(Chip, {
      size: "sm",
      selected: mode === 'custom',
      onClick: () => setMode('custom')
    }, "Custom"))
  }, "How"), /*#__PURE__*/React.createElement(SplitBar, {
    people: shown,
    total: total,
    onChange: next => {
      setMode('custom');
      setWeights(prev => prev.map(p => {
        const n = next.find(x => x.name === p.name);
        return n ? {
          ...p,
          value: n.value
        } : p;
      }));
    }
  })), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement(SectionLabel, null, "Shares"), /*#__PURE__*/React.createElement(Card, {
    padded: false,
    lift: 2
  }, shown.map((p, i) => /*#__PURE__*/React.createElement(ListRow, {
    key: p.name,
    leading: /*#__PURE__*/React.createElement(Avatar, {
      name: p.name,
      hue: p.hue,
      size: 36
    }),
    title: p.name.split(' ')[0],
    subtitle: p.name === payer ? 'paid the bill' : undefined,
    divider: i < shown.length - 1,
    trailing: /*#__PURE__*/React.createElement(Amount, {
      value: total * (p.value / sum)
    })
  }))))));
}

/* ---------------- Settle up sheet ---------------- */
function SettleUpSheet({
  open,
  onClose,
  group,
  onDone
}) {
  const owing = group.balances.filter(b => b.amount !== 0);
  // History of names in the order they were marked, so a single tap can undo the last one.
  const [paid, setPaid] = React.useState([]);
  React.useEffect(() => {
    if (open) setPaid([]);
  }, [open]);
  const mark = name => setPaid(p => p.includes(name) ? p : [...p, name]);
  const revert = name => setPaid(p => p.filter(x => x !== name));
  const revertLast = () => setPaid(p => p.slice(0, -1));
  const revertAll = () => setPaid([]);
  const last = paid[paid.length - 1];
  const done = paid.length >= owing.length && owing.length > 0;
  return /*#__PURE__*/React.createElement(Sheet, {
    open: open,
    onClose: onClose,
    height: "76%",
    title: "Settle up",
    footer: /*#__PURE__*/React.createElement("div", {
      style: {
        display: 'flex',
        flexDirection: 'column',
        gap: 'var(--space-2)'
      }
    }, last && /*#__PURE__*/React.createElement(Button, {
      block: true,
      variant: "outline",
      size: "md",
      icon: "rotate-cw",
      onClick: revertLast
    }, `Undo ${last.split(' ')[0]}`), /*#__PURE__*/React.createElement(Button, {
      block: true,
      size: "lg",
      variant: "solid",
      tone: done ? 'mint' : 'action',
      icon: done ? 'party-popper' : 'handshake',
      onClick: onDone
    }, done ? "We're all square" : 'Done for now'))
  }, /*#__PURE__*/React.createElement("div", {
    style: {
      display: 'flex',
      flexDirection: 'column',
      gap: 'var(--space-4)'
    }
  }, /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement(SectionLabel, {
    right: paid.length > 1 ? /*#__PURE__*/React.createElement(Chip, {
      size: "sm",
      icon: "rotate-cw",
      onClick: revertAll
    }, "Reset all") : null
  }, "Marked as paid"), /*#__PURE__*/React.createElement(ProgressBar, {
    value: paid.length,
    max: owing.length || 1,
    tone: "mint",
    height: 14
  })), /*#__PURE__*/React.createElement(Card, {
    padded: false,
    lift: 2
  }, owing.map((b, i) => {
    const isPaid = paid.includes(b.name);
    const divider = i < owing.length - 1;
    // A marked row keeps its place in the list and offers an immediate revert,
    // so tapping the wrong person is a one-tap mistake, never a lost balance.
    return isPaid ? /*#__PURE__*/React.createElement(ListRow, {
      key: b.name,
      divider: divider,
      leading: /*#__PURE__*/React.createElement(Avatar, {
        name: b.name,
        hue: b.hue,
        size: 40,
        dimmed: true
      }),
      title: /*#__PURE__*/React.createElement("span", {
        style: {
          color: 'var(--text-muted)',
          textDecoration: 'line-through',
          textDecorationThickness: '2px'
        }
      }, b.name),
      subtitle: "marked as paid",
      trailing: /*#__PURE__*/React.createElement("span", {
        style: {
          display: 'flex',
          alignItems: 'center',
          gap: 'var(--space-2)'
        }
      }, /*#__PURE__*/React.createElement(Amount, {
        value: b.amount,
        tone: "settled",
        size: "md"
      }), /*#__PURE__*/React.createElement(Button, {
        size: "sm",
        variant: "outline",
        icon: "rotate-cw",
        onClick: () => revert(b.name)
      }, "Undo"))
    }) : /*#__PURE__*/React.createElement(BalanceRow, {
      key: b.name,
      name: b.name,
      hue: b.hue,
      amount: b.amount,
      size: "md",
      onSettle: () => mark(b.name),
      divider: divider
    });
  })), /*#__PURE__*/React.createElement("p", {
    style: {
      margin: 0,
      fontSize: 'var(--text-body)',
      color: 'var(--text-muted)'
    }
  }, "Tally doesn't move money. Mark what you've squared up in real life \u2014 tap ", /*#__PURE__*/React.createElement("b", null, "Undo"), " if you mark the wrong person.")));
}
Object.assign(window, {
  Screen,
  Pad,
  SectionLabel,
  Stat,
  CatDisc,
  GroupsHome,
  OverallScreen,
  GroupDetail,
  ExpenseDetailSheet,
  Activity,
  You,
  AddExpenseSheet,
  SettleUpSheet
});
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/mobile-app/Screens.jsx", error: String((e && e.message) || e) }); }

// ui_kits/mobile-app/data.js
try { (() => {
window.TallyData = (() => {
  const MEMBERS = [{
    name: 'You',
    hue: 6
  }, {
    name: 'Mei Wong',
    hue: 4
  }, {
    name: 'Sam Oyelaran',
    hue: 2
  }, {
    name: 'Ade Kim',
    hue: 5
  }];
  // splits: what each person's share of the expense total is
  const GROUPS = [{
    id: 'osaka',
    name: 'Osaka trip',
    icon: 'plane',
    hue: 5,
    members: MEMBERS,
    balance: -42.8,
    started: '12 Jun',
    currency: 'USD',
    expenses: [{
      id: 1,
      title: 'Sichuan Rose',
      category: 'food',
      paidBy: 'Mei Wong',
      date: 'Yesterday',
      day: 'Yesterday',
      total: 96.4,
      yourShare: -24.1,
      splits: [{
        name: 'You',
        hue: 6,
        value: 24.1
      }, {
        name: 'Mei Wong',
        hue: 4,
        value: 24.1
      }, {
        name: 'Sam Oyelaran',
        hue: 2,
        value: 24.1
      }, {
        name: 'Ade Kim',
        hue: 5,
        value: 24.1
      }],
      note: 'Mapo tofu, twice-cooked pork, two beers'
    }, {
      id: 2,
      title: 'Shinkansen tickets',
      category: 'transport',
      paidBy: 'You',
      date: 'Tue',
      day: 'Tuesday',
      total: 256.8,
      yourShare: 192.6,
      splits: [{
        name: 'You',
        hue: 6,
        value: 64.2
      }, {
        name: 'Mei Wong',
        hue: 4,
        value: 64.2
      }, {
        name: 'Sam Oyelaran',
        hue: 2,
        value: 64.2
      }, {
        name: 'Ade Kim',
        hue: 5,
        value: 64.2
      }],
      note: 'Osaka → Kyoto, reserved seats'
    }, {
      id: 3,
      title: 'Capsule hotel',
      category: 'stay',
      paidBy: 'Sam Oyelaran',
      date: 'Tue',
      day: 'Tuesday',
      total: 184,
      yourShare: -46,
      splits: [{
        name: 'You',
        hue: 6,
        value: 46
      }, {
        name: 'Mei Wong',
        hue: 4,
        value: 46
      }, {
        name: 'Sam Oyelaran',
        hue: 2,
        value: 46
      }, {
        name: 'Ade Kim',
        hue: 5,
        value: 46
      }],
      note: 'Two nights, four pods'
    }, {
      id: 4,
      title: 'Konbini run',
      category: 'groceries',
      paidBy: 'Ade Kim',
      date: 'Mon',
      day: 'Monday',
      total: 22.4,
      yourShare: -5.6,
      splits: [{
        name: 'You',
        hue: 6,
        value: 5.6
      }, {
        name: 'Mei Wong',
        hue: 4,
        value: 5.6
      }, {
        name: 'Sam Oyelaran',
        hue: 2,
        value: 5.6
      }, {
        name: 'Ade Kim',
        hue: 5,
        value: 5.6
      }]
    }, {
      id: 5,
      title: 'Karaoke',
      category: 'fun',
      paidBy: 'Mei Wong',
      date: 'Mon',
      day: 'Monday',
      total: 60,
      yourShare: -15,
      splits: [{
        name: 'You',
        hue: 6,
        value: 15
      }, {
        name: 'Mei Wong',
        hue: 4,
        value: 15
      }, {
        name: 'Sam Oyelaran',
        hue: 2,
        value: 15
      }, {
        name: 'Ade Kim',
        hue: 5,
        value: 15
      }],
      note: 'Two hours, room 4'
    }],
    balances: [{
      name: 'Mei Wong',
      hue: 4,
      amount: -39.1
    }, {
      name: 'Sam Oyelaran',
      hue: 2,
      amount: -46
    }, {
      name: 'Ade Kim',
      hue: 5,
      amount: 42.3
    }]
  }, {
    id: 'flat',
    name: 'Flat 12B',
    icon: 'house',
    hue: 6,
    members: MEMBERS.slice(0, 3),
    balance: 0,
    started: '1 Jan',
    currency: 'USD',
    expenses: [{
      id: 6,
      title: 'Broadband',
      category: 'home',
      paidBy: 'You',
      date: '1 Jul',
      day: 'July',
      total: 45,
      yourShare: 0,
      settled: true,
      splits: [{
        name: 'You',
        hue: 6,
        value: 15
      }, {
        name: 'Mei Wong',
        hue: 4,
        value: 15
      }, {
        name: 'Sam Oyelaran',
        hue: 2,
        value: 15
      }]
    }, {
      id: 7,
      title: 'Weekly shop',
      category: 'groceries',
      paidBy: 'Mei Wong',
      date: '28 Jun',
      day: 'June',
      total: 82.5,
      yourShare: 0,
      settled: true,
      splits: [{
        name: 'You',
        hue: 6,
        value: 27.5
      }, {
        name: 'Mei Wong',
        hue: 4,
        value: 27.5
      }, {
        name: 'Sam Oyelaran',
        hue: 2,
        value: 27.5
      }]
    }],
    balances: [{
      name: 'Mei Wong',
      hue: 4,
      amount: 0
    }, {
      name: 'Sam Oyelaran',
      hue: 2,
      amount: 0
    }]
  }, {
    id: 'brunch',
    name: 'Sunday brunch club',
    icon: 'coffee',
    hue: 2,
    members: MEMBERS,
    balance: 18.75,
    started: '3 Mar',
    currency: 'USD',
    expenses: [{
      id: 8,
      title: 'Eggs at Nine Lives',
      category: 'food',
      paidBy: 'You',
      date: 'Sun',
      day: 'Sunday',
      total: 75,
      yourShare: 18.75,
      splits: [{
        name: 'You',
        hue: 6,
        value: 18.75
      }, {
        name: 'Mei Wong',
        hue: 4,
        value: 18.75
      }, {
        name: 'Sam Oyelaran',
        hue: 2,
        value: 18.75
      }, {
        name: 'Ade Kim',
        hue: 5,
        value: 18.75
      }],
      note: 'Big table, one bill'
    }],
    balances: [{
      name: 'Mei Wong',
      hue: 4,
      amount: 6.25
    }, {
      name: 'Sam Oyelaran',
      hue: 2,
      amount: 6.25
    }, {
      name: 'Ade Kim',
      hue: 5,
      amount: 6.25
    }]
  }];
  return {
    MEMBERS,
    GROUPS
  };
})();
})(); } catch (e) { __ds_ns.__errors.push({ path: "ui_kits/mobile-app/data.js", error: String((e && e.message) || e) }); }

__ds_ns.Amount = __ds_scope.Amount;

__ds_ns.Avatar = __ds_scope.Avatar;

__ds_ns.AvatarStack = __ds_scope.AvatarStack;

__ds_ns.Badge = __ds_scope.Badge;

__ds_ns.Button = __ds_scope.Button;

__ds_ns.Card = __ds_scope.Card;

__ds_ns.Chip = __ds_scope.Chip;

__ds_ns.Icon = __ds_scope.Icon;

__ds_ns.IconButton = __ds_scope.IconButton;

__ds_ns.ICON_INNER = __ds_scope.ICON_INNER;

__ds_ns.ICON_NAMES = __ds_scope.ICON_NAMES;

__ds_ns.EmptyState = __ds_scope.EmptyState;

__ds_ns.ProgressBar = __ds_scope.ProgressBar;

__ds_ns.SettledBanner = __ds_scope.SettledBanner;

__ds_ns.Toast = __ds_scope.Toast;

__ds_ns.AmountInput = __ds_scope.AmountInput;

__ds_ns.CATEGORIES = __ds_scope.CATEGORIES;

__ds_ns.CategoryPicker = __ds_scope.CategoryPicker;

__ds_ns.Input = __ds_scope.Input;

__ds_ns.Keypad = __ds_scope.Keypad;

__ds_ns.PersonToggleRow = __ds_scope.PersonToggleRow;

__ds_ns.SplitBar = __ds_scope.SplitBar;

__ds_ns.Stepper = __ds_scope.Stepper;

__ds_ns.BalanceRow = __ds_scope.BalanceRow;

__ds_ns.ExpenseRow = __ds_scope.ExpenseRow;

__ds_ns.GroupCard = __ds_scope.GroupCard;

__ds_ns.ListRow = __ds_scope.ListRow;

__ds_ns.AppBar = __ds_scope.AppBar;

__ds_ns.Sheet = __ds_scope.Sheet;

__ds_ns.TabBar = __ds_scope.TabBar;

})();
