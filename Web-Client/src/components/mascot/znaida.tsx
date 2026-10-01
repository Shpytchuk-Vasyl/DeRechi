import { cx } from "class-variance-authority"
import type { CSSProperties } from "react"

export type Look = "left" | "center" | "right"

const LOOK: Record<Look, number> = { left: -1, center: 0, right: 1 }

const INK = "#3a2e5c"
const LAV = "#c9a8ff"
const LAV_D = "#9d7ae0"
const PINK = "#ffb3d1"
const WHITE = "#ffffff"
const LINE = { stroke: INK, strokeLinejoin: "round", strokeLinecap: "round" } as const

export function Znaida({
  look = "center",
  className,
  ...rest
}: {
  look?: Look
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
        <path
          d="M-68 -6 C-68 -46 -38 -66 0 -66 C38 -66 68 -46 68 -6 C68 40 40 66 0 66 C-40 66 -68 40 -68 -6 Z"
          fill={LAV}
          strokeWidth={4.8}
          {...LINE}
        />

        {/* The face */}
        <g className="znaida-face">
          <ellipse cx="-47" cy="20" rx="10" ry="6.5" fill={PINK} opacity="0.85" />
          <ellipse cx="47" cy="20" rx="10" ry="6.5" fill={PINK} opacity="0.85" />
          <ellipse cx="-25" cy="-8" rx="10" ry="12" fill={WHITE} strokeWidth={3.8} {...LINE} />
          <ellipse cx="25" cy="-8" rx="10" ry="12" fill={WHITE} strokeWidth={3.8} {...LINE} />
          <g className="znaida-pupils">
            <circle cx="-25" cy="-6.5" r="6" fill={INK} />
            <circle cx="-27" cy="-9.5" r="2" fill={WHITE} />
            <circle cx="25" cy="-6.5" r="6" fill={INK} />
            <circle cx="23" cy="-9.5" r="2" fill={WHITE} />
          </g>
          <ellipse cx="0" cy="30" rx="32" ry="24" fill={WHITE} strokeWidth={3.8} {...LINE} />
          <path
            className="znaida-tongue"
            d="M-7 38 C-8 54 8 54 7 38 Z"
            fill={PINK}
            strokeWidth={3.3}
            {...LINE}
          />
          <path
            d="M0 28 L0 35 M-12 33 Q-6 41 0 35 Q6 41 12 33"
            fill="none"
            strokeWidth={3.3}
            {...LINE}
          />
          <g transform="translate(0 20)">
            <path
              d="M-13 -6 C-13 -11 13 -11 13 -6 C13 3 5 9 0 9 C-5 9 -13 3 -13 -6 Z"
              fill={INK}
              strokeWidth={3.8}
              strokeLinejoin="round"
              stroke={INK}
            />
            <ellipse cx="-4" cy="-5" rx="4" ry="2.2" fill={WHITE} opacity="0.9" />
          </g>
        </g>

        {/* Ears */}
        <path
          className="znaida-ear znaida-ear-left"
          d="M-50 -54 C-82 -52 -96 0 -88 38 C-82 60 -56 58 -52 38 C-48 10 -40 -28 -50 -54 Z"
          fill={LAV_D}
          strokeWidth={4.8}
          {...LINE}
        />
        <path
          className="znaida-ear znaida-ear-right"
          d="M50 -54 C82 -52 96 0 88 38 C82 60 56 58 52 38 C48 10 40 -28 50 -54 Z"
          fill={LAV_D}
          strokeWidth={4.8}
          {...LINE}
        />
      </g>
      {/* Paws on the edge */}
      <ellipse cx="80" cy="206" rx="20" ry="12" fill={LAV} strokeWidth={5} {...LINE} />
      <path d="M74 202 L74 208 M86 202 L86 208" strokeWidth={3} {...LINE} />
      <ellipse cx="160" cy="206" rx="20" ry="12" fill={LAV} strokeWidth={5} {...LINE} />
      <path d="M154 202 L154 208 M166 202 L166 208" strokeWidth={3} {...LINE} />
    </svg>
  )
}
