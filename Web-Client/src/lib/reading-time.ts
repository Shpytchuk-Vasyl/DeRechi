// Reading time for the long-form pages (guides, terms, privacy): whole minutes, never zero.

export const WORDS_PER_MINUTE = 180

export function countWords(texts: readonly string[]): number {
  return texts.join(" ").split(/\s+/).filter(Boolean).length
}

export function readingMinutes(texts: readonly string[]): number {
  return Math.max(1, Math.round(countWords(texts) / WORDS_PER_MINUTE))
}
