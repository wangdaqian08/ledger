/**
 * Tally's signature control: a single proportional bar with draggable handles between people.
 * @startingPoint section="Forms" subtitle="Draggable proportional split bar" viewport="700x260"
 */
export interface SplitBarPerson { name: string; hue?: number; value: number }
export interface SplitBarProps {
  /** One entry per person. `value` is a relative weight — it does not need to sum to 100. */
  people: SplitBarPerson[];
  /** Money total the percentages are applied to, for the label row. */
  total?: number;
  /** Bar height px. 56 default; 40 for a compact inline bar. */
  height?: number;
  /** Show the name + amount legend beneath the bar. */
  showLabels?: boolean;
  /** Fires continuously during drag with the updated people array. */
  onChange?: (people: SplitBarPerson[]) => void;
  style?: React.CSSProperties;
}
export function SplitBar(props: SplitBarProps): JSX.Element;
