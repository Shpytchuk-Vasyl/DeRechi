import { CACHE_TTL } from "@/lib/cache"

export type NunitoWeight = 600 | 800

export async function nunito(
  text: string,
  weight: NunitoWeight = 800,
): Promise<ArrayBuffer | null> {
  try {
    const css = await fetch(
      `https://fonts.googleapis.com/css2?family=Nunito:wght@${weight}&text=${encodeURIComponent(text)}`,
      { headers: { "User-Agent": "Mozilla/5.0" }, next: { revalidate: CACHE_TTL.day } },
    ).then((response) => response.text())

    const url = css.match(/src: url\((https:[^)]+)\)/)?.[1]
    if (!url) return null

    return await fetch(url, { next: { revalidate: CACHE_TTL.day } }).then((response) =>
      response.arrayBuffer(),
    )
  } catch {
    return null
  }
}
