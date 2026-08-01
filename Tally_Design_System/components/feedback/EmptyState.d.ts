/** Nothing-here state with a tilted lemon glyph slab. */
export interface EmptyStateProps {
  /** Lucide slug. */
  icon?: string;
  title?: React.ReactNode;
  body?: React.ReactNode;
  /** Usually a single primary Button. */
  action?: React.ReactNode;
  style?: React.CSSProperties;
}
export function EmptyState(props: EmptyStateProps): JSX.Element;
