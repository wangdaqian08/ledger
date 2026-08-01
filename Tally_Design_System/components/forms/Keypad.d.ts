/** 3x4 numeric pad: 1-9, decimal point, 0, delete. */
export interface KeypadProps {
  /** Fires with '0'-'9', '.' or 'del'. */
  onKey?: (key: string) => void;
  style?: React.CSSProperties;
}
export function Keypad(props: KeypadProps): JSX.Element;
