import type { Metadata } from "next"
import { notFound } from "next/navigation"
import { ZnaidaLab } from "@/components/playground/znaida-lab"

export const metadata: Metadata = { title: "Playground", robots: { index: false } }

export default function PlaygroundRoute() {
  if (process.env.NODE_ENV === "production") {
    notFound()
  }

  return (
    <div className="mx-auto flex max-w-295 flex-col gap-8 px-4 py-8 sm:px-6">
      <h1 className="font-black text-3xl">Playground</h1>
      <ZnaidaLab />
    </div>
  )
}
