import { Lightbulb, TriangleAlert } from "lucide-react"
import { Blob } from "@/components/pouf/media"
import { Text } from "@/components/pouf/text"
import type { Tone } from "@/components/pouf/tone"
import type { GuideBlock, GuideStep } from "@/content/guides"

export function Blocks({ blocks }: { blocks: GuideBlock[] }) {
  return (
    <>
      {blocks.map((block) => {
        const key = typeof block === "string" ? block : Array.isArray(block) ? block[0] : block.text
        if (Array.isArray(block)) {
          return (
            <ul key={key} className="flex list-disc flex-col gap-2 pl-5">
              {block.map((item) => (
                <li key={item}>
                  <Text className="leading-relaxed">{item}</Text>
                </li>
              ))}
            </ul>
          )
        }
        if (typeof block === "string") {
          return (
            <Text key={key} className="block leading-relaxed">
              {block}
            </Text>
          )
        }
        const tip = block.tone === "tip"
        return (
          <div key={key} className="flex gap-3.5 items-center" role={tip ? undefined : "note"}>
            <Blob
              icon={tip ? <Lightbulb /> : <TriangleAlert />}
              tone={tip ? "mint" : "yellow"}
              size="sm"
            />
            <Text className="min-w-0 leading-relaxed">{block.text}</Text>
          </div>
        )
      })}
    </>
  )
}

export function Steps({ steps, tone }: { steps: GuideStep[]; tone: Tone }) {
  return (
    <ol className="flex flex-col">
      {steps.map((step, index) => (
        <li key={step.title} className="relative flex gap-4 pb-6 last:pb-0">
          {index < steps.length - 1 ? (
            <span aria-hidden className="absolute top-12 bottom-0 left-5.25 w-0.5 bg-border" />
          ) : null}
          <Blob icon={<span className="font-black">{index + 1}</span>} tone={tone} size="sm" />
          <div className="min-w-0 pt-2">
            <Text className="block font-bold">{step.title}</Text>
            <Text muted className="mt-1 block leading-relaxed">
              {step.text}
            </Text>
          </div>
        </li>
      ))}
    </ol>
  )
}
