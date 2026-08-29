export const COMMENT_MAX_WORDS = 100
export const COMMENT_MAX_CHARS = 1200

export function countWords(text: string): number {
  return text
    .trim()
    .split(/\s+/)
    .filter(word => word.length > 0).length
}
