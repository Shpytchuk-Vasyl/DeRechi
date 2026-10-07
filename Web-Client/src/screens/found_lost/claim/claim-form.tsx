import { useTranslations } from "next-intl"
import { type ReactNode, type SubmitEventHandler, useEffect } from "react"
import { useFormContext } from "react-hook-form"
import { ConsentCheckbox } from "@/components/form/consent-checkbox"
import { ContactFields } from "@/components/form/contact-fields"
import { LegalLink } from "@/components/form/legal-link"
import { Button } from "@/components/pouf/Button"
import { ErrorNote } from "@/components/pouf/feedback"
import { SafetyLink, SafetyNote } from "@/components/safety/safety-note"
import { paths } from "@/i18n/paths"
import type { ClaimFormValues } from "@/schema/claim-schema"
import { useClaimItem } from "./claim-item"

type Props = {
  failure: string | null
  onSubmit: SubmitEventHandler<HTMLFormElement>
  onCancel: () => void
}

export default function ClaimForm({ failure, onSubmit, onCancel }: Props) {
  const tc = useTranslations("claim")
  const { countryCode } = useClaimItem()
  const {
    setFocus,
    formState: { isSubmitting },
  } = useFormContext<ClaimFormValues>()

  useEffect(() => {
    setFocus("phone")
  }, [setFocus])

  const legal = (pathname: typeof paths.terms | typeof paths.privacy) => (chunks: ReactNode) => (
    <LegalLink href={{ pathname, query: { country: countryCode } }}>{chunks}</LegalLink>
  )

  return (
    <form onSubmit={onSubmit} noValidate className="mt-5 flex flex-col gap-5">
      <ContactFields />

      <ConsentCheckbox>
        {tc.rich("consent", {
          terms: legal(paths.terms),
          privacy: legal(paths.privacy),
        })}
      </ConsentCheckbox>

      <SafetyNote>
        {tc.rich("safetyNote", { link: (chunks) => <SafetyLink newTab>{chunks}</SafetyLink> })}
      </SafetyNote>

      {failure ? <ErrorNote>{failure}</ErrorNote> : null}

      <div className="flex flex-wrap items-center gap-3">
        <Button type="submit" loading={isSubmitting}>
          {isSubmitting ? tc("submitting") : tc("submit")}
        </Button>
        <Button variant="quiet" onClick={onCancel} disabled={isSubmitting}>
          {tc("cancel")}
        </Button>
      </div>
    </form>
  )
}
