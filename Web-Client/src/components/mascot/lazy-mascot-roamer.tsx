"use client"

import dynamic from "next/dynamic"

export const LazyMascotRoamer = dynamic(
  () => import("@/components/mascot/mascot-roamer").then((mod) => mod.MascotRoamer),
  { ssr: false },
)
