"use client"

import { useTranslations } from "next-intl"
import { useMemo } from "react"
import type { ItemKind } from "@/api/items"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/pouf/card"
import { Link } from "@/i18n/navigation"
import { paths } from "@/i18n/paths"
import ClaimDone from "./claim-done"
import { ClaimItemProvider } from "./claim-item"
import ClaimPrompt from "./claim-prompt"
import { useClaim } from "./use-claim"

type Props = {
  kind: ItemKind
  id: string
  countryCode: string
  contact: { phone: string; email: string | null }
}

export default function ClaimCard({ kind, id, countryCode, contact }: Props) {
  const t = useTranslations("item")
  const tc = useTranslations("claim")
  const tn = useTranslations("nav")
  const { sent, markSent } = useClaim(kind, id)
  const item = useMemo(() => ({ kind, itemId: id, countryCode }), [kind, id, countryCode])
  const otherKind: ItemKind = kind === "lost" ? "found" : "lost"

  return (
    <Card className="mt-7">
      <CardHeader>
        <CardTitle className="text-lg">
          {kind === "lost" ? t("contactTitle") : t("contactFinderTitle")}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <dl className="grid grid-cols-[90px_1fr] gap-x-3.5 gap-y-2.5">
          <dt className="text-muted-foreground">{t("phone")}</dt>
          <dd className="font-semibold">{contact.phone}</dd>
          <dt className="text-muted-foreground">{t("email")}</dt>
          <dd className="font-semibold">{contact.email ?? "-"}</dd>
        </dl>
        <p className="mt-3.5 text-muted-foreground text-sm leading-relaxed">{t("maskedNote")}</p>

        <ClaimItemProvider value={item}>
          <section aria-live="polite" className="mt-6 border-border border-t pt-5">
            {sent ? (
              <ClaimDone repeated={sent === "repeated"} />
            ) : (
              <ClaimPrompt onSent={markSent} />
            )}
          </section>
        </ClaimItemProvider>

        <p className="mt-5 text-muted-foreground text-sm">
          {tc("orPost")}{" "}
          <Link href={paths.report(otherKind)} className="font-bold underline">
            {otherKind === "lost" ? tn("reportLost") : tn("reportFound")}
          </Link>
        </p>
      </CardContent>
    </Card>
  )
}
