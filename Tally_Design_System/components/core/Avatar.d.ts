/**
 * Circular person marker with initials on that person's assigned hue.
 * @startingPoint section="Core" subtitle="Person hues, selected and dimmed states" viewport="700x180"
 */
export interface AvatarProps {
  /** Full name; initials are derived from the first two words. */
  name: string;
  /** 1-8, mapping to --person-1 … --person-8. Assign once per group member and never change it. */
  hue?: number;
  /** Diameter px. 32 dense list · 40 default · 56 header · 72 hero. */
  size?: number;
  /** Draws the double ink ring — used for "included in this split". */
  selected?: boolean;
  /** Desaturates and fades — "excluded from this split". */
  dimmed?: boolean;
  /** One initial instead of two — for overlapped stacks where only a sliver of the circle shows. Set automatically by AvatarStack. */
  compact?: boolean;
  /** Small ink pip at the corner, e.g. a share count or amount. */
  badge?: string | number;
  style?: React.CSSProperties;
}
export function Avatar(props: AvatarProps): JSX.Element;
