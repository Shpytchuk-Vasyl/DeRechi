import type { MetadataRoute } from "next"
import { INDEXABLE, SITE_URL, sitemapIds } from "@/lib/seo"

export default function robots(): MetadataRoute.Robots {
  if (!INDEXABLE) {
    return { rules: { userAgent: "*", disallow: "/" } }
  }

  return {
    rules: {
      userAgent: "*",
      allow: "/",
      disallow: ["/api/", "/*/prize", "/*/playground", "/*/claims/"],
    },
    sitemap: sitemapIds().map(({ id }) => `${SITE_URL}/sitemap/${id}.xml`),
  }
}
