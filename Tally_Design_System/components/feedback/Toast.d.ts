/** Transient confirmation capsule above the tab bar. */
export interface ToastProps {
  open?: boolean;
  message?: React.ReactNode;
  /** Lucide slug. */
  icon?: string;
  tone?: 'ink' | 'mint' | 'coral';
  /** Optional trailing action, e.g. an Undo ghost button. */
  action?: React.ReactNode;
  style?: React.CSSProperties;
}
export function Toast(props: ToastProps): JSX.Element;
