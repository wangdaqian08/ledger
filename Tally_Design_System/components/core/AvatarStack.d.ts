/** Overlapping avatars with a +N overflow pip. */
export interface AvatarStackPerson { name: string; hue?: number }
export interface AvatarStackProps {
  people: AvatarStackPerson[];
  /** Avatar diameter px. */
  size?: number;
  /** How many avatars before the +N pip. */
  max?: number;
  /** Overlap in px between neighbours. */
  overlap?: number;
  style?: React.CSSProperties;
}
export function AvatarStack(props: AvatarStackProps): JSX.Element;
