import { getTranslations } from "next-intl/server"
import { BrandMark } from "@/components/layout/brand-mark"
import { Footer } from "@/components/pouf/footer"
import { paths } from "@/i18n/paths"

export async function SiteFooter() {
  const t = await getTranslations("nav")
  const app = await getTranslations("app")

  return (
    <div className="pt-12 pb-8">
      <Footer
        brand={
          <>
            <BrandMark />
            {app("name")}
          </>
        }
        tagline={app("description")}
        columns={[
          {
            title: t("footerNotices"),
            links: [
              { label: t("lost"), href: paths.list("lost") },
              { label: t("found"), href: paths.list("found") },
              { label: t("safety"), href: paths.safety },
            ],
          },
          {
            title: t("footerDocuments"),
            links: [
              { label: t("terms"), href: paths.terms },
              { label: t("privacy"), href: paths.privacy },
            ],
          },
        ]}
      />
    </div>
  )
}
