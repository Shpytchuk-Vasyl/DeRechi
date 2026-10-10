import { expect, test } from "../fixtures/base"
import { Toasts } from "../fixtures/pages"
import { HomePage } from "../fixtures/pages/home-page"
import { noticeText } from "../fixtures/pages/report-data"
import { openReportPage, ReportFlow, type SavedDraft } from "../fixtures/pages/report-flow"
import { waitForHydration } from "../support/hydration"
import { PHOTO_FIXTURE } from "../support/minio"

function isStepOneSaved(text: { title: string; description: string }) {
  return (draft: SavedDraft) =>
    draft.values.title === text.title &&
    draft.values.description === text.description &&
    draft.values.categoryId !== undefined
}

test.describe("report draft", () => {
  test("a reload brings back the values and the step but not the photo; publishing clears it", async ({
    page,
    go,
    t,
    shared,
    data,
    appLocale,
    flags,
  }) => {
    const place = shared.place
    const text = noticeText(data, appLocale, "BAGS")
    const contact = data.contact("author")

    const form = await openReportPage(page, go, t, "lost")
    await form.fillStepDetails(text)
    await form.next()
    await form.expectStep(1)
    await form.photo(PHOTO_FIXTURE)
    await form.pickPlace(place.name)
    await form.waitForDraft(
      (draft) =>
        draft.step === 1 &&
        draft.values.title === text.title &&
        draft.values.description === text.description &&
        draft.values.categoryId !== undefined &&
        draft.values.place?.id === place.id,
    )

    await page.reload()
    await waitForHydration(page)
    const toasts = new Toasts(page, t, flags)
    const restored = toasts.byText(t("form.draftRestored"))
    await expect(restored).toBeVisible()
    await toasts.dismiss(restored)

    await form.expectStep(1)
    await expect(form.placeSearch()).toHaveValue(place.name)
    await expect(form.photoPreview()).toHaveCount(0)
    await form.back()
    await form.expectStep(0)
    await expect(form.title()).toHaveValue(text.title)
    await expect(form.description()).toHaveValue(text.description)
    await expect(form.categorySelect()).toContainText(t(`category.${text.category}`))

    await form.next()
    await form.expectStep(1)
    await form.next()
    await form.expectStep(2)
    await form.fillStepContacts({ phone: contact.phone, email: contact.email })
    await form.publish()
    await expect.poll(() => form.readDraft()).toBeNull()
  })

  test("a draft older than 5 minutes is dropped", async ({
    page,
    go,
    t,
    data,
    appLocale,
    flags,
  }) => {
    const text = noticeText(data, appLocale, "KEYS")
    await page.clock.install()

    const form = await openReportPage(page, go, t, "lost")
    await form.fillStepDetails(text)
    await form.waitForDraft(isStepOneSaved(text))

    await page.clock.fastForward("06:00")
    await page.reload()
    await waitForHydration(page)

    await expect.poll(() => form.readDraft()).toBeNull()
    await expect(new Toasts(page, t, flags).byText(t("form.draftRestored"))).toHaveCount(0)
    await form.expectStep(0)
    await expect(form.title()).toHaveValue("")
  })

  test("the modal and the full page share the draft", async ({ page, go, t, data, appLocale }) => {
    const text = noticeText(data, appLocale, "WALLET")

    const full = await openReportPage(page, go, t, "lost")
    await full.fillStepDetails(text)
    await full.waitForDraft(isStepOneSaved(text))

    await go("/")
    await new HomePage(page, t).heroReportLink("lost").click()

    const modal = new ReportFlow(page, t, "lost", { modal: true })
    await expect(modal.dialog()).toBeVisible()
    await modal.expectStep(0)
    await expect(modal.title()).toHaveValue(text.title)
    await expect(modal.description()).toHaveValue(text.description)
    await expect(modal.categorySelect()).toContainText(t(`category.${text.category}`))
  })
})
