import { expect, test } from "../fixtures/base"
import { ItemDetail } from "../fixtures/pages"
import { noticeText, oversizedImage, textFile } from "../fixtures/pages/report-data"
import { openReportPage } from "../fixtures/pages/report-flow"
import { env } from "../support/env"
import { PHOTO_FIXTURE } from "../support/minio"

const _UPLOAD_KEY = /^items\/\d{4}\/\d{2}\/[0-9a-f-]{36}\.png$/

test.describe("found notice with a photo", () => {
  test("the chosen photo is uploaded and shown on the published notice", async ({
    page,
    go,
    t,
    shared,
    data,
    appLocale,
  }) => {
    const place = shared.place
    const text = noticeText(data, appLocale, "ELECTRONICS")
    const contact = data.contact("author")

    const form = await openReportPage(page, go, t, "found")
    await form.fillStepDetails(text)
    await form.throughWhereWhen({ place: place.name, photo: PHOTO_FIXTURE })
    await form.fillStepContacts({ phone: contact.phone, email: contact.email })
    const id = await form.publish()

    await go(`/found/${id}`)
    await expect(
      page.getByRole("heading", { level: 1, name: text.title, exact: true }),
    ).toBeVisible()
    const detail = new ItemDetail(page, t, "found")
    await expect(detail.openPhotoButton()).toBeVisible()
    const photo = detail
      .root()
      .getByAltText(t("item.photoOf", { title: text.title }), { exact: true })
      .first()
    await expect
      .poll(() =>
        photo.evaluate((image) => {
          const element = image as HTMLImageElement
          return element.complete && element.naturalWidth > 0
        }),
      )
      .toBe(true)
  })
})

test.describe("upload errors", () => {
  test("a text file and a file over the size limit are refused with a message", async ({
    page,
    go,
    t,
    data,
    appLocale,
  }) => {
    const form = await openReportPage(page, go, t, "found")
    await form.fillStepDetails(noticeText(data, appLocale))
    await form.next()
    await form.expectStep(1)
    await expect(
      form
        .root()
        .getByText(t("upload.hint", { size: Math.round(env.maxUploadBytes / (1024 * 1024)) }), {
          exact: true,
        }),
    ).toBeVisible()

    await form.dropFile(textFile())
    await expect(form.error(t("upload.unsupportedType"))).toBeVisible()
    await expect(form.photoPreview()).toHaveCount(0)

    await form.dropFile(oversizedImage())
    await expect(form.error(t("upload.tooLarge"))).toBeVisible()
    await expect(form.error(t("upload.unsupportedType"))).toHaveCount(0)
    await expect(form.photoPreview()).toHaveCount(0)

    await form.photo(PHOTO_FIXTURE)
    await expect(form.error(t("upload.tooLarge"))).toHaveCount(0)
  })

  test("“Remove photo” clears the preview, and Next asks for a photo again", async ({
    page,
    go,
    t,
    shared,
    data,
    appLocale,
  }) => {
    const place = shared.place
    const form = await openReportPage(page, go, t, "found")
    await form.fillStepDetails(noticeText(data, appLocale))
    await form.next()
    await form.expectStep(1)
    await form.pickPlace(place.name)

    await form.photo(PHOTO_FIXTURE)
    await form.removePhotoButton().click()
    await expect(form.photoPreview()).toHaveCount(0)
    await expect(form.removePhotoButton()).toHaveCount(0)

    await form.next()
    await form.expectStep(1)
    await expect(form.error(t("form.error.photoRequired"))).toBeVisible()
  })

  test("a failed upload sends the form back to step 1 with an error; the retry publishes", async ({
    page,
    go,
    t,
    shared,
    data,
    appLocale,
    consoleErrors,
  }) => {
    consoleErrors.allow(/net::ERR_FAILED/, "the test aborts the photo PUT on purpose")
    const place = shared.place
    const text = noticeText(data, appLocale, "WALLET")
    const contact = data.contact("author")

    const bucket = `**/${env.s3.bucket}/**`
    await page.route(bucket, (route) =>
      route.request().method() === "PUT" ? route.abort("failed") : route.fallback(),
    )

    const form = await openReportPage(page, go, t, "found")
    await form.fillStepDetails(text)
    await form.throughWhereWhen({ place: place.name, photo: PHOTO_FIXTURE })
    await form.fillStepContacts({ phone: contact.phone, email: contact.email })
    await form.publishButton().click()

    await form.expectStep(1)
    await expect(form.error(t("upload.failed"))).toBeVisible()
    await expect(form.photoPreview()).toBeVisible()
    await expect(page).toHaveURL(/\/report\/found$/)

    await page.unroute(bucket)
    await form.next()
    await form.expectStep(2)
    const id = await form.publish()
    await go(`/found/${id}`)
    await expect(new ItemDetail(page, t, "found").openPhotoButton()).toBeVisible()
  })
})

test.describe("document photo", () => {
  test("a photo of DOCUMENTS warns about personal data, other categories do not", async ({
    page,
    go,
    t,
    data,
    appLocale,
  }) => {
    const form = await openReportPage(page, go, t, "found")
    const warning = form.error(t("form.photoPrivacy"))

    await form.fillStepDetails(noticeText(data, appLocale, "DOCUMENTS"))
    await form.next()
    await form.expectStep(1)
    await expect(warning).toHaveCount(0)
    await form.photo(PHOTO_FIXTURE)
    await expect(warning).toBeVisible()

    await form.stepButton(0).click()
    await form.expectStep(0)
    await form.category("KEYS")
    await form.next()
    await form.expectStep(1)
    await expect(form.photoPreview()).toBeVisible()
    await expect(warning).toHaveCount(0)
  })
})
