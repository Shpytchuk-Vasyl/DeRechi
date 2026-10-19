import { describe, expect, it, vi } from "vitest"
import { type SharePayload, shareOrCopy, shareOrDownloadImage } from "./share"

const PAYLOAD: SharePayload = {
  title: "Знайдено ключі",
  text: "Знайдено ключі (вулиця Чумацька, Тернопіль). Допоможіть знайти власника.",
  url: "https://www.derechi.site/uk/found/65",
}

function failing(name: string) {
  const error = new Error(name)
  error.name = name
  return vi.fn().mockRejectedValue(error)
}

describe("shareOrCopy", () => {
  it("opens the system share sheet when there is one, and copies nothing", async () => {
    const share = vi.fn().mockResolvedValue(undefined)
    const writeText = vi.fn()

    expect(await shareOrCopy(PAYLOAD, { share, clipboard: { writeText } })).toBe("shared")
    expect(share).toHaveBeenCalledWith(PAYLOAD)
    expect(writeText).not.toHaveBeenCalled()
  })

  it("does nothing more when the person closes the share sheet", async () => {
    const writeText = vi.fn()

    expect(
      await shareOrCopy(PAYLOAD, { share: failing("AbortError"), clipboard: { writeText } }),
    ).toBe("cancelled")
    expect(writeText).not.toHaveBeenCalled()
  })

  it("copies the text and the link when the share sheet refuses", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)

    expect(
      await shareOrCopy(PAYLOAD, { share: failing("NotAllowedError"), clipboard: { writeText } }),
    ).toBe("copied")
    expect(writeText).toHaveBeenCalledWith(`${PAYLOAD.text}\n${PAYLOAD.url}`)
  })

  it("copies the text and the link where there is no share sheet", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)

    expect(await shareOrCopy(PAYLOAD, { clipboard: { writeText } })).toBe("copied")
    expect(writeText).toHaveBeenCalledWith(`${PAYLOAD.text}\n${PAYLOAD.url}`)
  })

  it("reports a failure when neither works", async () => {
    expect(await shareOrCopy(PAYLOAD, {})).toBe("failed")
    expect(
      await shareOrCopy(PAYLOAD, { clipboard: { writeText: failing("NotAllowedError") } }),
    ).toBe("failed")
  })
})

describe("shareOrDownloadImage", () => {
  const file = new File([new Uint8Array([1, 2, 3])], "derechi-found-65.png", { type: "image/png" })

  it("shares the image with the text and the link where files can be shared", async () => {
    const share = vi.fn().mockResolvedValue(undefined)
    const download = vi.fn()

    expect(
      await shareOrDownloadImage(file, PAYLOAD, { share, canShare: () => true }, download),
    ).toBe("shared")
    expect(share).toHaveBeenCalledWith({
      files: [file],
      title: PAYLOAD.title,
      text: `${PAYLOAD.text}\n${PAYLOAD.url}`,
    })
    expect(download).not.toHaveBeenCalled()
  })

  it("downloads the image where files cannot be shared", async () => {
    const share = vi.fn()
    const download = vi.fn()

    expect(
      await shareOrDownloadImage(file, PAYLOAD, { share, canShare: () => false }, download),
    ).toBe("downloaded")
    expect(await shareOrDownloadImage(file, PAYLOAD, { share }, download)).toBe("downloaded")
    expect(share).not.toHaveBeenCalled()
    expect(download).toHaveBeenCalledTimes(2)
  })

  it("downloads the image when the share sheet refuses, and not when it is closed", async () => {
    const download = vi.fn()
    const canShare = () => true

    expect(
      await shareOrDownloadImage(
        file,
        PAYLOAD,
        { share: failing("NotAllowedError"), canShare },
        download,
      ),
    ).toBe("downloaded")
    expect(
      await shareOrDownloadImage(
        file,
        PAYLOAD,
        { share: failing("AbortError"), canShare },
        download,
      ),
    ).toBe("cancelled")
    expect(download).toHaveBeenCalledTimes(1)
  })

  it("reports a failure when the download throws too", async () => {
    const download = vi.fn(() => {
      throw new Error("blocked")
    })
    expect(await shareOrDownloadImage(file, PAYLOAD, {}, download)).toBe("failed")
  })
})
