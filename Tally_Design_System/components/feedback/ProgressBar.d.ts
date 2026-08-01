/** Slim ink-bordered progress meter. */
export interface ProgressBarProps {
  value?: number;
  max?: number;
  tone?: 'action' | 'mint' | 'coral' | 'lemon';
  /** Track height px. */
  height?: number;
  /** Caption above the track; renders the percentage on the right. */
  label?: string;
  style?: React.CSSProperties;
}
export function ProgressBar(props: ProgressBarProps): JSX.Element;
