import { cx } from "class-variance-authority"
import type { CSSProperties, ReactNode } from "react"

export type Look = "left" | "center" | "right"
export type Mood = "happy" | "surprised" | "sleepy" | "sad"

const LOOK: Record<Look, number> = { left: -1, center: 0, right: 1 }

const INK = "#3a2e5c"
const LAV = "#c9a8ff"
const LAV_D = "#9d7ae0"
const PINK = "#ffb3d1"
const TEAR = "#9fd8ff"
const WHITE = "#ffffff"
const LINE = { stroke: INK, strokeLinejoin: "round", strokeLinecap: "round" } as const
const HEAD =
  "M0 -72 C44 -72 78 -38 78 18 C78 46 50 56 0 56 C-50 56 -78 46 -78 18 C-78 -38 -44 -72 0 -72 Z"

const BROWS: Partial<Record<Mood, string>> = {
  surprised: "M-39 -28 Q-31 -33 -23 -29",
  sleepy: "M-41 -22 Q-33 -27 -24 -26",
  sad: "M-41 -21 Q-33 -22 -25 -28",
}

const MOUTHS: Record<Exclude<Mood, "surprised">, string> = {
  happy: "M0 19 L0 23 M-12 20.5 Q-6 28.5 0 23 Q6 28.5 12 20.5",
  sleepy: "M0 19 L0 24 M-7 27.5 Q0 20.5 7 27.5",
  sad: "M0 19 L0 24 M-10 29 Q0 19 10 29",
}

export function Znaida({
  look = "center",
  mood = "happy",
  className,
  ...rest
}: {
  look?: Look
  mood?: Mood
  className?: string
  "aria-hidden"?: boolean
}) {
  return (
    <svg
      viewBox="0 0 240 228"
      className={cx("znaida", className)}
      style={{ "--look": LOOK[look] } as CSSProperties}
      role="img"
      aria-label="Знайда"
      {...rest}
    >
      <g transform="translate(120 148.7) scale(1.1)">
        {/* Head */}
        <path d={HEAD} fill={LAV} />

        {/* The face */}
        <g className="znaida-face">
          <ellipse cx="-50" cy="20" rx="11" ry="6.5" fill={PINK} opacity="0.85" />
          <ellipse cx="50" cy="20" rx="11" ry="6.5" fill={PINK} opacity="0.85" />
          <path
            d="M-33 32 C-33 16 -18 7 0 7 C18 7 33 16 33 32 C33 47 26 57 0 57 C-26 57 -33 47 -33 32 Z"
            fill={WHITE}
          />
          <Eyes mood={mood} />
          <Mouth mood={mood} />
          <path
            d="M-10 8 C-10 4 10 4 10 8 C10 14 4 19 0 19 C-4 19 -10 14 -10 8 Z"
            fill={INK}
            strokeWidth={2.6}
            {...LINE}
          />
          <ellipse cx="-3.5" cy="8.5" rx="3.2" ry="1.7" fill={WHITE} opacity="0.9" />
        </g>
        <path d={HEAD} fill="none" strokeWidth={4} {...LINE} />

        {/* Ears */}
        <path
          className="znaida-ear znaida-ear-left"
          d="M-35 -64 C-56 -73 -90 -64 -98 -42 C-103 -27 -93 -6 -82 1 C-74 6 -63 5 -58 -2 C-55 -16 -55 -42 -35 -64 Z"
          fill={LAV_D}
          strokeWidth={4}
          {...LINE}
        />
        <path
          className="znaida-ear znaida-ear-right"
          d="M35 -64 C56 -73 90 -64 98 -42 C103 -27 93 -6 82 1 C74 6 63 5 58 -2 C55 -16 55 -42 35 -64 Z"
          fill={LAV_D}
          strokeWidth={4}
          {...LINE}
        />
      </g>
      {/* Paws on the edge */}
      <ellipse cx="79" cy="206" rx="22" ry="13" fill={LAV} strokeWidth={4.4} {...LINE} />
      <path d="M73 203 L73 209 M85 203 L85 209" strokeWidth={2.8} {...LINE} />
      <ellipse cx="161" cy="206" rx="22" ry="13" fill={LAV} strokeWidth={4.4} {...LINE} />
      <path d="M155 203 L155 209 M167 203 L167 209" strokeWidth={2.8} {...LINE} />
    </svg>
  )
}

function Eyes({ mood }: { mood: Mood }) {
  const wide = mood === "surprised"
  const white = wide ? 17 : 15.5
  const iris = wide ? 10 : 12.5
  const brow = BROWS[mood]

  return (
    <>
      <circle cx="-30" cy="-3" r={white} fill={WHITE} strokeWidth={3} {...LINE} />
      <circle cx="30" cy="-3" r={white} fill={WHITE} strokeWidth={3} {...LINE} />
      <g className="znaida-pupils">
        <Pupil cx={-30} r={iris} />
        <Pupil cx={30} r={iris} />
      </g>
      {mood === "sleepy" && (
        <Pair>
          <path d="M-45.2 0 A15.5 15.5 0 1 1 -14.8 0 Z" fill={LAV} strokeWidth={3} {...LINE} />
        </Pair>
      )}
      {mood === "sad" && (
        <Pair>
          <ellipse cx="-30" cy="7.5" rx="9" ry="3.6" fill={TEAR} />
        </Pair>
      )}
      {brow && (
        <Pair>
          <path d={brow} fill="none" strokeWidth={2.6} {...LINE} />
        </Pair>
      )}
    </>
  )
}

function Pupil({ cx, r }: { cx: number; r: number }) {
  return (
    <>
      <circle cx={cx} cy={-2} r={r} fill={INK} />
      <circle cx={cx - r * 0.36} cy={-2 - r * 0.4} r={r * 0.38} fill={WHITE} />
      <circle cx={cx + r * 0.36} cy={-2 + r * 0.36} r={r * 0.16} fill={WHITE} />
    </>
  )
}

function Mouth({ mood }: { mood: Mood }) {
  if (mood === "surprised") {
    return (
      <>
        <ellipse cx="0" cy="29" rx="6.5" ry="7.5" fill={INK} strokeWidth={2.4} {...LINE} />
        <ellipse cx="0" cy="33" rx="4" ry="2.6" fill={PINK} />
      </>
    )
  }

  return (
    <>
      {mood === "happy" && (
        <path
          className="znaida-tongue"
          d="M-4.5 24.5 C-5 32.5 5 32.5 4.5 24.5 Z"
          fill={PINK}
          strokeWidth={2.4}
          {...LINE}
        />
      )}
      <path d={MOUTHS[mood]} fill="none" strokeWidth={2.6} {...LINE} />
    </>
  )
}

function Pair({ children }: { children: ReactNode }) {
  return (
    <>
      {children}
      <g transform="scale(-1 1)">{children}</g>
    </>
  )
}
