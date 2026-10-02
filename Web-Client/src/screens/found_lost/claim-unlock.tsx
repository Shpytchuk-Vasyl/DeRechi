"use client"

import { CircleCheck, ExternalLink, Phone } from "lucide-react"
import { useTranslations } from "next-intl"
import { useCallback, useEffect, useState } from "react"
import { claimStatus, unlockClaim } from "@/app/actions/claim"
import { Button } from "@/components/pouf/Button"
import {
  Dialog,
  DialogBody,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/pouf/dialog"
import { ErrorNote } from "@/components/pouf/feedback"
import { LinkButton } from "@/components/pouf/link-button"
import { Blob } from "@/components/pouf/media"
import { Text } from "@/components/pouf/text"

export type ClaimPayment = {
  token: string
  checkoutUrl: string | null
  paid: boolean
  contactsSent: boolean
}

export type PaymentStatus = Partial<Omit<ClaimPayment, "token">>

type Props = {
  payment: ClaimPayment
  onStatus: (status: PaymentStatus) => void
}

type Failure = "unavailable" | "failed" | null

const POLL_EVERY_MS = 20_000
const POLL_FOR_MS = 15 * 60_000

export default function ClaimUnlock({ payment, onStatus }: Props) {
  const t = useTranslations("claim.unlock")
  const [open, setOpen] = useState(false)
  const [timedOut, setTimedOut] = useState(false)
  const [preparing, setPreparing] = useState(false)
  const [failure, setFailure] = useState<Failure>(null)
  const { token, checkoutUrl, paid, contactsSent } = payment

  const prepare = useCallback(async () => {
    setPreparing(true)
    setFailure(null)
    const result = await unlockClaim(token).catch(() => ({
      ok: false as const,
      reason: "failed" as const,
    }))
    setPreparing(false)
    if (result.ok) {
      onStatus({
        checkoutUrl: result.checkoutUrl,
        paid: result.paid,
        contactsSent: result.contactsSent,
      })
      return
    }
    setFailure(result.reason === "unavailable" ? "unavailable" : "failed")
  }, [token, onStatus])

  useEffect(() => {
    if (open && !checkoutUrl && !paid && !preparing && failure === null) void prepare()
    // biome-ignore lint/correctness/useExhaustiveDependencies: see above
  }, [open])

  useEffect(() => {
    if (!open || contactsSent) return
    const startedAt = Date.now()
    const timer = window.setInterval(async () => {
      if (Date.now() - startedAt >= POLL_FOR_MS) {
        window.clearInterval(timer)
        setTimedOut(true)
        return
      }
      const status = await claimStatus(token).catch(() => null)
      if (status?.ok) {
        onStatus({
          checkoutUrl: status.checkoutUrl,
          paid: status.paid,
          contactsSent: status.contactsSent,
        })
      }
    }, POLL_EVERY_MS)
    return () => window.clearInterval(timer)
  }, [open, token, contactsSent, onStatus])

  function onOpenChange(next: boolean) {
    setOpen(next)
    if (next) {
      setTimedOut(false)
      setFailure(null)
    }
  }

  const status = paid ? (
    <div className="flex items-start gap-3.5">
      <Blob icon={<CircleCheck />} tone="mint" size="sm" />
      <div>
        <Text className="block leading-relaxed">{t("paid")}</Text>
        {contactsSent ? (
          <Text muted className="mt-1 block leading-relaxed">
            {t("sent")}
          </Text>
        ) : null}
      </div>
    </div>
  ) : null

  return (
    <div className="mt-5">
      {status ?? (
        <Button onClick={() => onOpenChange(true)}>
          <Phone className="size-4" aria-hidden />
          {t("button")}
        </Button>
      )}

      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent size="md">
          <DialogHeader>
            <DialogTitle className="whitespace-normal">{t("title")}</DialogTitle>
            <DialogDescription>{t("text")}</DialogDescription>
          </DialogHeader>
          <DialogBody className="flex flex-col gap-5" aria-live="polite">
            {status ?? (
              <>
                <Text muted className="block leading-relaxed">
                  {t("support")}
                </Text>

                {checkoutUrl ? (
                  <LinkButton href={checkoutUrl} target="_blank" block>
                    <ExternalLink className="size-4" aria-hidden />
                    {t("pay")}
                  </LinkButton>
                ) : failure ? (
                  <>
                    <ErrorNote>{t(failure)}</ErrorNote>
                    <Button variant="quiet" onClick={prepare} loading={preparing}>
                      {t("retry")}
                    </Button>
                  </>
                ) : (
                  <Text muted className="block leading-relaxed">
                    {t("preparing")}
                  </Text>
                )}

                {checkoutUrl ? (
                  <Text size="sm" muted className="block leading-relaxed">
                    {t("timing")} {timedOut ? t("stopped") : t("waiting")}
                  </Text>
                ) : null}
              </>
            )}
          </DialogBody>
        </DialogContent>
      </Dialog>
    </div>
  )
}
