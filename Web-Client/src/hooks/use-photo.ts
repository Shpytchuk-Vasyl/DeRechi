"use client"

import { useTranslations } from "next-intl"
import { useEffect, useRef, useState } from "react"
import { ImageUploadError, uploadImage } from "@/lib/uploads/upload-image"

export type Photo = ReturnType<typeof usePhoto>

export function usePhoto() {
  const tu = useTranslations("upload")
  const [file, setFile] = useState<File | null>(null)
  const [url, setUrl] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  const uploaded = useRef<{ file: File; key: string } | null>(null)

  useEffect(() => {
    if (!file) {
      setUrl(null)
      return
    }
    const next = URL.createObjectURL(file)
    setUrl(next)
    return () => URL.revokeObjectURL(next)
  }, [file])

  function change(next: File | null) {
    setFile(next)
    if (next) setError(null)
  }

  async function upload(): Promise<string | undefined | null> {
    if (!file) return undefined
    if (uploaded.current?.file === file) return uploaded.current.key

    setUploading(true)
    try {
      const key = await uploadImage(file)
      uploaded.current = { file, key }
      return key
    } catch (cause) {
      setError(tu(cause instanceof ImageUploadError ? cause.reason : "failed"))
      return null
    } finally {
      setUploading(false)
    }
  }

  return { file, url, error, setError, uploading, change, upload }
}
