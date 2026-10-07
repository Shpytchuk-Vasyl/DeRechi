import { withBotId } from "botid/next/config"
import type { NextConfig } from "next"
import createNextIntlPlugin from "next-intl/plugin"

const withNextIntl = createNextIntlPlugin("./src/i18n/request.ts")

function filesOrigin(): NonNullable<NonNullable<NextConfig["images"]>["remotePatterns"]> {
  const raw = process.env.NEXT_PUBLIC_FILES_URL
  if (!raw) return []
  const url = new URL(raw)
  return [
    {
      protocol: url.protocol.replace(/:$/, "") as "http" | "https",
      hostname: url.hostname,
      port: url.port,
      pathname: `${url.pathname.replace(/\/+$/, "")}/**`,
    },
  ]
}

const securityHeaders = [
  { key: "Content-Security-Policy", value: "frame-ancestors 'none'" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
]

const nextConfig: NextConfig = {
  reactCompiler: true,
  typedRoutes: true,
  allowedDevOrigins: process.env.VERCEL ? undefined : ["192.168.*.*", "10.*.*.*"],
  poweredByHeader: false,
  async headers() {
    return [
      { source: "/:path*", headers: securityHeaders },
      {
        source: "/:locale/claims/:token",
        headers: [{ key: "Referrer-Policy", value: "no-referrer" }],
      },
    ]
  },
  images: {
    formats: ["image/webp"],
    deviceSizes: [640, 828, 1080, 1920],
    imageSizes: [256, 384],
    minimumCacheTTL: 2678400,
    remotePatterns: filesOrigin(),
    dangerouslyAllowLocalIP: !process.env.VERCEL,
  },
  experimental: {
    turbopackRustReactCompiler: true,
    optimizePackageImports: ["radix-ui", "framer-motion"],
  },
}

export default withBotId(withNextIntl(nextConfig))
