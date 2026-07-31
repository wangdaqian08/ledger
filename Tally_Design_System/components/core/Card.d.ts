/**
 * Ink-bordered slab container.
 * @startingPoint section="Core" subtitle="Card tones, lifts and pressable state" viewport="700x260"
 */
export interface CardProps {
  children?: React.ReactNode;
  /** Background wash. Tints carry meaning: mint = owed to you, coral = you owe. */
  tone?: 'surface' | 'sunk' | 'action' | 'mint' | 'coral' | 'lemon' | 'ink';
  /** Ink edge thickness in px. 0 flat, 2 default, 4 for hero cards, 6 for the top of a stack. */
  lift?: number;
  /** Sinks on press — use for any card that navigates. */
  pressable?: boolean;
  /** Apply --pad-card (16px). Set false for cards holding full-bleed rows. */
  padded?: boolean;
  onClick?: (e: React.MouseEvent) => void;
  style?: React.CSSProperties;
}
export function Card(props: CardProps): JSX.Element;
