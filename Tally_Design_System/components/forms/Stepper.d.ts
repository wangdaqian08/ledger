/** Tap-only numeric stepper for share counts and people counts. */
export interface StepperProps {
  value?: number;
  min?: number;
  max?: number;
  /** Short unit appended to the figure, e.g. "×". */
  suffix?: string;
  onChange?: (value: number) => void;
  style?: React.CSSProperties;
}
export function Stepper(props: StepperProps): JSX.Element;
