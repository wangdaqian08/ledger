/** Horizontally scrolling category chip row. */
export interface Category { id: string; label: string; icon: string }
export interface CategoryPickerProps {
  /** Selected category id. */
  value?: string;
  categories?: Category[];
  onChange?: (id: string) => void;
  style?: React.CSSProperties;
}
export function CategoryPicker(props: CategoryPickerProps): JSX.Element;
export const CATEGORIES: Category[];
