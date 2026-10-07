"use client"

import { cn } from "cn"
import { ImagePlus, Loader2, X } from "lucide-react"
import { useTranslations } from "next-intl"
import { useCallback, useEffect, useState } from "react"
import { useDropzone } from "react-dropzone"
import { Button, IconButton } from "@/components/pouf/Button"
import { FieldError } from "@/components/pouf/Input"

const INPUT_ID = "image-upload"
const ERROR_ID = `${INPUT_ID}-err`

const ACCEPT = {
  "image/jpeg": [".jpg", ".jpeg"],
  "image/png": [".png"],
  "image/webp": [".webp"],
  "image/gif": [".gif"],
}

type Props = {
  file: File | null
  onFileChange: (file: File | null) => void
  maxBytes: number
  uploading?: boolean
  error?: string
}

export function ImageUpload({ file, onFileChange, maxBytes, uploading, error }: Props) {
  const t = useTranslations("upload")
  const [preview, setPreview] = useState<string | null>(null)
  const [rejected, setRejected] = useState<string | null>(null)

  useEffect(() => {
    if (!file) {
      setPreview(null)
      return
    }

    const url = URL.createObjectURL(file)
    setPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [file])

  const onDrop = useCallback(
    (accepted: File[]) => {
      const next = accepted[0]
      if (next) {
        setRejected(null)
        onFileChange(next)
      }
    },
    [onFileChange],
  )

  const { getRootProps, getInputProps, isDragActive, open } = useDropzone({
    onDrop,
    accept: ACCEPT,
    maxFiles: 1,
    maxSize: maxBytes,
    noClick: true,
    noKeyboard: true,
    onDropRejected: (rejections) => {
      const code = rejections[0]?.errors[0]?.code
      setRejected(t(code === "file-too-large" ? "tooLarge" : "unsupportedType"))
    },
  })

  const problem = error ?? rejected

  return (
    <div className="flex flex-col gap-2">
      <div
        {...getRootProps()}
        className={cn(
          "flex flex-wrap items-center gap-4 rounded-control p-5 transition-colors",
          isDragActive ? "bg-secondary" : "bg-bg",
          // Same-property utilities don't cascade, so the invalid ring replaces the cushion.
          problem
            ? "[box-shadow:var(--pouf-field),inset_0_0_0_3px_var(--orange)]"
            : "cushion-field",
        )}
      >
        <input
          {...getInputProps({
            id: INPUT_ID,
            "aria-invalid": problem ? true : undefined,
            "aria-describedby": problem ? ERROR_ID : undefined,
          })}
        />

        <div className="relative grid h-21 w-28 place-items-center overflow-hidden rounded-control bg-surface">
          {preview ? (
            // biome-ignore lint/performance/noImgElement: object URL, never optimised
            <img src={preview} alt={t("preview")} className="h-full w-full object-cover" />
          ) : (
            <ImagePlus className="size-6 text-muted-foreground" aria-hidden />
          )}
          {uploading ? (
            <span className="absolute inset-0 grid place-items-center bg-surface/70">
              <Loader2 className="size-5 animate-spin text-primary" aria-hidden />
            </span>
          ) : null}
        </div>

        <div className="min-w-50 flex-1">
          <p className="font-semibold">{t("label")}</p>
          <p className="mt-1 text-muted-foreground text-sm">
            {t("hint", { size: Math.round(maxBytes / (1024 * 1024)) })}
          </p>
          <p aria-live="polite" className="mt-1 text-sm">
            {uploading ? <span className="text-muted-foreground">{t("uploading")}</span> : null}
            {!uploading && file && !problem ? (
              <span className="text-muted-foreground">{file.name}</span>
            ) : null}
          </p>
        </div>

        <div className="flex gap-2">
          <Button variant="quiet" onClick={open}>
            {t("choose")}
          </Button>
          {file ? (
            <IconButton
              label={t("remove")}
              icon={<X className="size-4" />}
              onClick={() => {
                setRejected(null)
                onFileChange(null)
              }}
            />
          ) : null}
        </div>
      </div>
      {problem ? <FieldError id={ERROR_ID}>{problem}</FieldError> : null}
    </div>
  )
}
