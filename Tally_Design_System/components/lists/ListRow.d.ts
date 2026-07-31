/** Generic tappable row inside an unpadded Card. */
export interface ListRowProps {
  /** Left slot — usually an Avatar or a category disc. */
  leading?: React.ReactNode;
  title?: React.ReactNode;
  subtitle?: React.ReactNode;
  /** Right slot — usually an Amount or a Badge. */
  trailing?: React.ReactNode;
  /** Append a chevron to signal navigation. */
  chevron?: boolean;
  /** Hairline beneath the row; set false on the last row. */
  divider?: boolean;
  onClick?: (e: React.MouseEvent) => void;
  style?: React.CSSProperties;
}
export function ListRow(props: ListRowProps): JSX.Element;
