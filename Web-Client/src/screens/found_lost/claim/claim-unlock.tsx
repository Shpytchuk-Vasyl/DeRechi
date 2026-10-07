import { CircleCheck, ExternalLink, Phone } from "lucide-react"
import { useFormatter, useTranslations } from "next-intl"
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
import { SafetyNote } from "@/components/safety/safety-note"
import { useSmsOutageNotice } from "@/hooks/use-sms-outage-notice"
import { fromIsoInstant } from "@/lib/intl/dates"
import { useClaimItem } from "./claim-item"

export type PhoneUnlock = {
  claimId: string
  checkoutUrl: string | null
  paid: boolean
  contactsSent: boolean
}

export type PhoneUnlockUpdate = Partial<Omit<PhoneUnlock, "claimId">>

type Props = {
  unlock: PhoneUnlock
  onChange: (update: PhoneUnlockUpdate) => void
}

type Failure =
  | { reason: "unavailable" | "failed" }
  | { reason: "limited"; retryAfter: Date | undefined }
  | null

const POLL_EVERY_MS = 20_000
const POLL_FOR_MS = 15 * 60_000

export default function ClaimUnlock({ unlock, onChange }: Props) {
  const { kind, itemId } = useClaimItem()
  const t = useTranslations("claim.unlock")
  const format = useFormatter()
  const [open, setOpen] = useState(false)
  const [timedOut, setTimedOut] = useState(false)
  const [preparing, setPreparing] = useState(false)
  const [failure, setFailure] = useState<Failure>(null)
  const warnSmsOutage = useSmsOutageNotice()
  const { claimId, checkoutUrl, paid, contactsSent } = unlock

  const prepare = useCallback(async () => {
    setPreparing(true)
    setFailure(null)
    const result = await unlockClaim(kind, itemId, claimId).catch(() => ({
      ok: false as const,
      reason: "failed" as const,
    }))
    setPreparing(false)
    if (result.ok) {
      onChange({
        checkoutUrl: result.checkoutUrl,
        paid: result.paid,
        contactsSent: result.contactsSent,
      })
      return
    }
    setFailure(
      result.reason === "limited"
        ? { reason: "limited", retryAfter: fromIsoInstant(result.retryAfter) }
        : { reason: result.reason === "unavailable" ? "unavailable" : "failed" },
    )
  }, [kind, itemId, claimId, onChange])

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
      const status = await claimStatus(kind, itemId, claimId).catch(() => null)
      if (status?.ok) {
        onChange({
          checkoutUrl: status.checkoutUrl,
          paid: status.paid,
          contactsSent: status.contactsSent,
        })
      }
    }, POLL_EVERY_MS)
    return () => window.clearInterval(timer)
  }, [open, kind, itemId, claimId, contactsSent, onChange])

  function onOpenChange(next: boolean) {
    setOpen(next)
    if (next) {
      setTimedOut(false)
      setFailure(null)
      warnSmsOutage()
    }
  }

  const paidStatus = (
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
  )

  return (
    <div className={paid ? "mt-5 -ml-14.5" : "mt-5"}>
      {paid ? (
        paidStatus
      ) : (
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
            {paid ? (
              paidStatus
            ) : (
              <>
                <Text muted className="block leading-relaxed">
                  {t("support")}
                </Text>
                <SafetyNote>{t("safety")}</SafetyNote>

                {checkoutUrl ? (
                  <LinkButton href={checkoutUrl} target="_blank" block>
                    <ExternalLink className="size-4" aria-hidden />
                    {t("pay")}
                  </LinkButton>
                ) : failure?.reason === "limited" ? (
                  <ErrorNote>
                    {failure.retryAfter
                      ? t("limitedUntil", {
                          date: format.dateTime(failure.retryAfter, {
                            dateStyle: "long",
                            timeStyle: "short",
                          }),
                        })
                      : t("limited")}
                  </ErrorNote>
                ) : failure ? (
                  <>
                    <ErrorNote>{t(failure.reason)}</ErrorNote>
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
