import { expect, test } from "../fixtures/base"
import { guideTexts } from "../support/guides"

test.describe("guide pages", () => {
  test("a link in a guide's contents takes the reader to its section", async ({
    page,
    go,
    appLocale,
  }) => {
    await go("/guide/documents")
    await page
      .getByRole("complementary")
      .getByRole("link", { name: guideTexts(appLocale).faqTitle, exact: true })
      .click()

    await expect(page).toHaveURL(/\/guide\/documents#faq$/)
    const section = page.locator("#faq")
    await expect(section).toBeFocused()
    await expect(section).toBeInViewport()
  })
})
