/** Spring-in bottom sheet with grab handle, scrolling body and pinned footer. */
export interface SheetProps {
  open?: boolean;
  onClose?: () => void;
  title?: React.ReactNode;
  children?: React.ReactNode;
  /** Pinned footer — put the primary Button here with `block`. */
  footer?: React.ReactNode;
  /** CSS height; 'auto' hugs content, or e.g. '76%'. */
  height?: string;
  style?: React.CSSProperties;
}
export function Sheet(props: SheetProps): JSX.Element;
