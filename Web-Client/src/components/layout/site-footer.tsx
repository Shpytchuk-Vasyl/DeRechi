import { getLocale, getTranslations } from "next-intl/server"
import { BrandMark } from "@/components/layout/brand-mark"
import { Footer, type FooterColumn } from "@/components/pouf/footer"
import { paths } from "@/i18n/paths"
import { clientEnv } from "@/lib/env/client"
import { feedbackForm, feedbackUrl } from "@/lib/feedback"

export async function SiteFooter() {
  const t = await getTranslations("nav")
  const app = await getTranslations("app")
  const locale = await getLocale()
  const form = feedbackForm(clientEnv.NEXT_PUBLIC_TALLY_FORM, locale)
  const contact: FooterColumn[] = form
    ? [
        {
          title: t("footerContact"),
          links: [
            {
              label: t("feedbackLink"),
              href: feedbackUrl(form, { page: "footer" }),
              external: true,
            },
          ],
        },
      ]
    : []

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
            ],
          },
          {
            title: t("footerDocuments"),
            links: [
              { label: t("terms"), href: paths.terms },
              { label: t("privacy"), href: paths.privacy },
              { label: t("safety"), href: paths.safety },
            ],
          },
          ...contact,
        ]}
      />
    </div>
  )
}
