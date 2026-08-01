/**
 * Horizontal row of tappable avatars — who is in on this expense.
 * @startingPoint section="Forms" subtitle="Tap avatars to include or exclude" viewport="700x180"
 */
export interface PersonToggleRowPerson { name: string; hue?: number }
export interface PersonToggleRowProps {
  people: PersonToggleRowPerson[];
  /** Names currently included. */
  selected?: string[];
  /** Avatar diameter px. */
  size?: number;
  /** Fires with the tapped person's name. */
  onToggle?: (name: string) => void;
  style?: React.CSSProperties;
}
export function PersonToggleRow(props: PersonToggleRowProps): JSX.Element;
