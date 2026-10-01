import { createUploadTicket } from "@/app/actions/uploads"

export type UploadFailure = "unsupportedType" | "tooLarge" | "failed"

export class ImageUploadError extends Error {
  constructor(readonly reason: UploadFailure) {
    super(`Image upload failed: ${reason}`)
    this.name = "ImageUploadError"
  }
}

export async function uploadImage(file: File): Promise<string> {
  const ticket = await createUploadTicket({ contentType: file.type, size: file.size })

  if (!ticket.ok) {
    throw new ImageUploadError(ticket.reason)
  }

  const response = await fetch(ticket.url, {
    method: "PUT",
    body: file,
    headers: { "Content-Type": file.type },
  })

  if (!response.ok) {
    throw new ImageUploadError("failed")
  }

  return ticket.key
}
