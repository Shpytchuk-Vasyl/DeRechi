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

const nextConfig: NextConfig = {
  reactCompiler: true,
  typedRoutes: true,
  poweredByHeader: false,
  images: {
    formats: ["image/avif", "image/webp"],
    remotePatterns: filesOrigin(),
    dangerouslyAllowLocalIP: true,
    // dangerouslyAllowLocalIP: process.env.NODE_ENV !== "production",
  },
  experimental: {
    turbopackRustReactCompiler: true,
    optimizePackageImports: ["radix-ui", "framer-motion"],
  },
}

export default withBotId(withNextIntl(nextConfig))
