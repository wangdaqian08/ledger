/** Single-line text field with an uppercase micro-label. */
export interface InputProps {
  label?: string;
  /** Lucide slug shown inside the field. */
  icon?: string;
  hint?: string;
  /** Error text; also turns the border coral. */
  error?: string;
  value?: string;
  placeholder?: string;
  type?: 'text' | 'email' | 'tel' | 'number' | 'search';
  onChange?: (e: React.ChangeEvent<HTMLInputElement>) => void;
  style?: React.CSSProperties;
}
export function Input(props: InputProps): JSX.Element;
