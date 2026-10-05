import { CircleCheck } from "lucide-react"
import { useTranslations } from "next-intl"
import { Blob } from "@/components/pouf/media"
import { Heading, Text } from "@/components/pouf/text"
import { SafetyLink, SafetyNote } from "@/components/safety/safety-note"
import { useClaimItem } from "./claim-item"
import ClaimUnlock, { type PhoneUnlock, type PhoneUnlockUpdate } from "./claim-unlock"

type Props = {
  repeated: boolean
  unlock: PhoneUnlock | null
  onUnlockChange: (update: PhoneUnlockUpdate) => void
}

export default function ClaimDone({ repeated, unlock, onUnlockChange }: Props) {
  const { kind } = useClaimItem()
  const tc = useTranslations("claim")

  return (
    <div className="flex items-start gap-3.5">
      <Blob icon={<CircleCheck />} tone="mint" size="sm" />
      <div>
        <Heading level={3} className="whitespace-normal text-base">
          {tc("doneTitle")}
        </Heading>
        <Text muted className="mt-1 block leading-relaxed">
          {repeated ? tc("repeated") : kind === "lost" ? tc("doneOwner") : tc("doneFinder")}
        </Text>
        <SafetyNote className="mt-3">
          {tc.rich("doneSafety", { link: (chunks) => <SafetyLink newTab>{chunks}</SafetyLink> })}
        </SafetyNote>
        {unlock ? <ClaimUnlock unlock={unlock} onChange={onUnlockChange} /> : null}
      </div>
    </div>
  )
}
