export function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
}

export function exactText(text: string): RegExp {
  return new RegExp(`^\\s*${escapeRegExp(text)}\\s*$`)
}
