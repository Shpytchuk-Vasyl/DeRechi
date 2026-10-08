import { hasLocale } from "next-intl"
import { getTranslations } from "next-intl/server"
import { routing } from "@/i18n/routing"
import { clientEnv } from "@/lib/env/client"

const RETRY_AFTER_SECONDS = 3600

const ESCAPES: Record<string, string> = {
  "&": "&amp;",
  "<": "&lt;",
  ">": "&gt;",
  '"': "&quot;",
  "'": "&#39;",
}

function escape(text: string): string {
  return text.replace(/[&<>"']/g, (char) => ESCAPES[char])
}

export async function GET(_request: Request, ctx: RouteContext<"/[locale]/maintenance">) {
  const { locale } = await ctx.params
  if (!clientEnv.NEXT_PUBLIC_MAINTENANCE || !hasLocale(routing.locales, locale)) {
    return new Response(null, { status: 404 })
  }

  const t = await getTranslations({ locale, namespace: "maintenance" })
  const html = `<!doctype html>
<html lang="${locale}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>${escape(t("title"))} | DeRechi</title>
<meta name="description" content="${escape(t("metaDescription"))}">
<link rel="icon" href="/icon.svg" type="image/svg+xml">
<style>
  :root { --bg: #f0e9ff; --ink: #3a2e5c; --muted: #71609b; --accent: #c9a8ff; --on-accent: #3a2e5c; color-scheme: light dark; }
  @media (prefers-color-scheme: dark) { :root { --bg: #12111a; --ink: #f7f3ff; --muted: #b8afcb; --on-accent: #2a2145; } }
  * { box-sizing: border-box; }
  body { margin: 0; min-height: 100svh; display: grid; place-items: center; padding: 24px 16px;
         background: var(--bg); color: var(--ink); font-family: system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
  main { max-width: 32rem; text-align: center; }
  h1 { margin: 0 0 12px; font-size: clamp(1.75rem, 5vw, 2.5rem); line-height: 1.15; }
  p { margin: 0 0 28px; font-size: 1.125rem; line-height: 1.5; color: var(--muted); }
  a { display: inline-block; padding: 12px 24px; border-radius: 999px; background: var(--accent);
      color: var(--on-accent); font-weight: 600; text-decoration: none; }
  a:focus-visible { outline: 3px solid var(--ink); outline-offset: 3px; }
</style>
</head>
<body>
<main>
  <h1>${escape(t("title"))}</h1>
  <p>${escape(t("text"))}</p>
  <a href="/${locale}">${escape(t("home"))}</a>
</main>
</body>
</html>`

  return new Response(html, {
    status: 503,
    headers: {
      "Content-Type": "text/html; charset=utf-8",
      "Retry-After": String(RETRY_AFTER_SECONDS),
      "Cache-Control": "no-store",
    },
  })
}
