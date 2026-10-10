import { CircleCheck } from "lucide-react"
import { useTranslations } from "next-intl"
import { Blob } from "@/components/pouf/media"
import { Heading, Text } from "@/components/pouf/text"
import { useClaimItem } from "./claim-item"

type Props = {
  repeated: boolean
}

export default function ClaimDone({ repeated }: Props) {
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
      </div>
    </div>
  )
}
