/**
 * Bottom tab bar with an optional raised centre action.
 * @startingPoint section="Navigation" subtitle="Tab bar with centre add action" viewport="700x140"
 */
export interface TabBarTab { id: string; label: string; icon: string }
export interface TabBarProps {
  tabs: TabBarTab[];
  /** Active tab id. */
  value?: string;
  onChange?: (id: string) => void;
  /** Raised circular slab dropped into the middle of the row. */
  centerAction?: { icon?: string; label: string; onClick?: () => void };
  style?: React.CSSProperties;
}
export function TabBar(props: TabBarProps): JSX.Element;
