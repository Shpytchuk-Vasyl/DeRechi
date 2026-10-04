import { zodResolver } from "@hookform/resolvers/zod"
import { useTranslations } from "next-intl"
import { useState } from "react"
import { FormProvider, useForm } from "react-hook-form"
import { claimNotice } from "@/app/actions/claim"
import { Button } from "@/components/pouf/Button"
import { Heading, Text } from "@/components/pouf/text"
import { type ClaimValues, claimSchema } from "@/schema/claim-schema"
import ClaimForm from "./claim-form"
import { useClaimItem } from "./claim-item"
import type { ClaimSuccess } from "./use-claim"

type Props = {
  onSent: (result: ClaimSuccess) => void
}

export default function ClaimPrompt({ onSent }: Props) {
  const { kind, itemId } = useClaimItem()
  const t = useTranslations("item")
  const tc = useTranslations("claim")
  const [open, setOpen] = useState(false)
  const [failure, setFailure] = useState<string | null>(null)

  const form = useForm<ClaimValues>({
    resolver: zodResolver(claimSchema),
    defaultValues: { phone: "", email: "", socialMedias: [] },
  })

  const send = form.handleSubmit(async (values) => {
    setFailure(null)
    const result = await claimNotice(kind, itemId, values)
    if (result.ok) {
      onSent(result)
      return
    }
    setFailure(tc(`error.${result.reason}`))
  })

  return (
    <>
      <Heading level={3} className="whitespace-normal text-base">
        {kind === "lost" ? t("didYouFind") : t("isItYours")}
      </Heading>
      <Text muted className="mt-1.5 block leading-relaxed">
        {kind === "lost" ? t("didYouFindText") : t("isItYoursText")}
      </Text>

      {open ? (
        <FormProvider {...form}>
          <ClaimForm failure={failure} onSubmit={send} onCancel={() => setOpen(false)} />
        </FormProvider>
      ) : (
        <Button className="mt-4" onClick={() => setOpen(true)}>
          {kind === "lost" ? tc("sendFound") : tc("sendMine")}
        </Button>
      )}
    </>
  )
}
