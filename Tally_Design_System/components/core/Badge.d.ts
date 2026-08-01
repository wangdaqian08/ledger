/** Small uppercase status label — "SETTLED", "PENDING", "3 LEFT". */
export interface BadgeProps {
  children?: React.ReactNode;
  tone?: 'neutral' | 'action' | 'mint' | 'coral' | 'lemon' | 'ink';
  style?: React.CSSProperties;
}
export function Badge(props: BadgeProps): JSX.Element;
