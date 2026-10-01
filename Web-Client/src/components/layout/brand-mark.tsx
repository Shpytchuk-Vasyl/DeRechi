export function BrandMark() {
  return (
    <svg viewBox="0 0 64 64" aria-hidden="true" className="size-8">
      <g transform="translate(26.5 36.5) scale(1.05) rotate(-40) translate(-32 -32)">
        <path
          d="M22 14H52a6 6 0 0 1 6 6V44a6 6 0 0 1-6 6H22L8 32Z M23.5 32a4.5 4.5 0 1 0-9 0a4.5 4.5 0 1 0 9 0Z"
          fillRule="evenodd"
          className="fill-purple"
          stroke="currentColor"
          strokeWidth={3.5}
          strokeLinejoin="round"
        />
        <g
          transform="translate(38 32) scale(0.78) translate(-31.5 -28.4)"
          className="text-(--on-accent)"
        >
          <path
            d="M26 21.5a6 6 0 1 1 9 5.2c-2 1.2-3 2.4-3 4.8"
            fill="none"
            stroke="currentColor"
            strokeWidth={4.6}
            strokeLinecap="round"
            strokeLinejoin="round"
          />
          <circle cx="32" cy="38.6" r="2.7" fill="currentColor" />
        </g>
      </g>
    </svg>
  )
}
