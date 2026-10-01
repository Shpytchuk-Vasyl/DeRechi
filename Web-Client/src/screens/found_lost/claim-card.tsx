"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { CircleCheck } from "lucide-react"
import { useTranslations } from "next-intl"
import { type ReactNode, useEffect, useState } from "react"
import { FormProvider, useForm } from "react-hook-form"
import type { ItemKind } from "@/api/items"
import { claimNotice } from "@/app/actions/claim"
import { ContactFields } from "@/components/form/contact-fields"
import { LegalLink } from "@/components/form/legal-link"
import { Button } from "@/components/pouf/Button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/pouf/card"
import { ErrorNote } from "@/components/pouf/feedback"
import { Blob } from "@/components/pouf/media"
import { Heading, Text } from "@/components/pouf/text"
import { Link } from "@/i18n/navigation"
import { paths } from "@/i18n/paths"
import { hasClaimCookie } from "@/lib/claim-cookie"
import { type ClaimValues, claimSchema } from "@/schema/claim-schema"

type Props = {
  kind: ItemKind
  id: string
  countryCode: string
  contact: { phone: string; email: string }
}

type Sent = "new" | "repeated" | null

export default function ClaimCard({ kind, id, countryCode, contact }: Props) {
  const t = useTranslations("item")
  const tc = useTranslations("claim")
  const tn = useTranslations("nav")
  const [open, setOpen] = useState(false)
  const [sent, setSent] = useState<Sent>(null)
  const [failure, setFailure] = useState<string | null>(null)
  const otherKind: ItemKind = kind === "lost" ? "found" : "lost"

  const form = useForm<ClaimValues>({
    resolver: zodResolver(claimSchema),
    defaultValues: { phone: "", email: "", socialMedias: [] },
  })
  const {
    handleSubmit,
    setFocus,
    formState: { isSubmitting },
  } = form

  useEffect(() => {
    if (hasClaimCookie(document.cookie, kind, id)) {
      setSent((current) => current ?? "repeated")
    }
  }, [kind, id])

  useEffect(() => {
    if (open) setFocus("phone")
  }, [open, setFocus])

  const send = handleSubmit(async (values) => {
    setFailure(null)
    const result = await claimNotice(kind, id, values)
    if (result.ok) {
      setSent(result.repeated ? "repeated" : "new")
      return
    }
    setFailure(tc(`error.${result.reason}`))
  })

  const legal = (pathname: typeof paths.terms | typeof paths.privacy) => (chunks: ReactNode) => (
    <LegalLink href={{ pathname, query: { country: countryCode } }}>{chunks}</LegalLink>
  )

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
          <dd className="font-semibold">{contact.email}</dd>
        </dl>
        <p className="mt-3.5 text-muted-foreground text-sm leading-relaxed">{t("maskedNote")}</p>

        <section aria-live="polite" className="mt-6 border-border border-t pt-5">
          {sent ? (
            <div className="flex items-start gap-3.5">
              <Blob icon={<CircleCheck />} tone="mint" size="sm" />
              <div>
                <Heading level={3} className="whitespace-normal text-base">
                  {tc("doneTitle")}
                </Heading>
                <Text muted className="mt-1 block leading-relaxed">
                  {sent === "repeated"
                    ? tc("repeated")
                    : kind === "lost"
                      ? tc("doneOwner")
                      : tc("doneFinder")}
                </Text>
              </div>
            </div>
          ) : (
            <>
              <Heading level={3} className="whitespace-normal text-base">
                {kind === "lost" ? t("didYouFind") : t("isItYours")}
              </Heading>
              <Text muted className="mt-1.5 block leading-relaxed">
                {kind === "lost" ? t("didYouFindText") : t("isItYoursText")}
              </Text>

              {open ? (
                <FormProvider {...form}>
                  <form onSubmit={send} noValidate className="mt-5 flex flex-col gap-5">
                    <ContactFields />

                    <p className="text-muted-foreground text-sm">
                      {tc.rich("consent", {
                        terms: legal(paths.terms),
                        privacy: legal(paths.privacy),
                      })}
                    </p>

                    {failure ? <ErrorNote>{failure}</ErrorNote> : null}

                    <div className="flex flex-wrap items-center gap-3">
                      <Button type="submit" loading={isSubmitting}>
                        {isSubmitting ? tc("submitting") : tc("submit")}
                      </Button>
                      <Button
                        variant="quiet"
                        onClick={() => setOpen(false)}
                        disabled={isSubmitting}
                      >
                        {tc("cancel")}
                      </Button>
                    </div>
                  </form>
                </FormProvider>
              ) : (
                <Button className="mt-4" onClick={() => setOpen(true)}>
                  {kind === "lost" ? tc("sendFound") : tc("sendMine")}
                </Button>
              )}
            </>
          )}
        </section>

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
