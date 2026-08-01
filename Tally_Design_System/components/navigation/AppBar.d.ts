/** Sticky top bar with a blurred paper backdrop. */
export interface AppBarProps {
  title?: React.ReactNode;
  subtitle?: React.ReactNode;
  /** Render a back arrow when provided. */
  onBack?: () => void;
  /** Right-hand action slot — usually IconButtons. */
  actions?: React.ReactNode;
  /** paper = translucent blurred default · ink = solid dark bar */
  tone?: 'paper' | 'ink';
  style?: React.CSSProperties;
}
export function AppBar(props: AppBarProps): JSX.Element;
