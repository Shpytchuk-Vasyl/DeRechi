export type SharePayload = { title: string; text: string; url: string }

export type ShareOutcome = "shared" | "copied" | "cancelled" | "failed"

export type ImageShareOutcome = "shared" | "downloaded" | "cancelled" | "failed"

type ShareNavigator = {
  share?: (data: ShareData) => Promise<void>
  canShare?: (data: ShareData) => boolean
  clipboard?: { writeText: (text: string) => Promise<void> }
}

export async function shareOrCopy(
  payload: SharePayload,
  nav: ShareNavigator,
): Promise<ShareOutcome> {
  if (nav.share) {
    try {
      await nav.share(payload)
      return "shared"
    } catch (error) {
      if (isAbort(error)) return "cancelled"
    }
  }

  try {
    if (!nav.clipboard) return "failed"
    await nav.clipboard.writeText(postText(payload))
    return "copied"
  } catch {
    return "failed"
  }
}

export async function shareOrDownloadImage(
  file: File,
  payload: SharePayload,
  nav: ShareNavigator,
  download: (file: File) => void,
): Promise<ImageShareOutcome> {
  const data: ShareData = { files: [file], title: payload.title, text: postText(payload) }
  if (nav.share && nav.canShare?.({ files: [file] })) {
    try {
      await nav.share(data)
      return "shared"
    } catch (error) {
      if (isAbort(error)) return "cancelled"
    }
  }

  try {
    download(file)
    return "downloaded"
  } catch {
    return "failed"
  }
}

function postText(payload: SharePayload): string {
  return `${payload.text}\n${payload.url}`
}

function isAbort(error: unknown): boolean {
  return (error as { name?: unknown } | null)?.name === "AbortError"
}
