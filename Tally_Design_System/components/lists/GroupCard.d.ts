/**
 * Home-screen tile summarising one group.
 * @startingPoint section="Lists" subtitle="Group tiles with balance and members" viewport="700x320"
 */
export interface GroupCardMember { name: string; hue?: number }
export interface GroupCardProps {
  name: string;
  /** Lucide slug for the group disc, e.g. "plane", "house", "users". */
  icon?: string;
  /** Person-hue index 1-8 for the disc. */
  hue?: number;
  members?: GroupCardMember[];
  /** Your net position in this group. Positive = owed to you. */
  balance?: number;
  onClick?: (e: React.MouseEvent) => void;
  style?: React.CSSProperties;
}
export function GroupCard(props: GroupCardProps): JSX.Element;
