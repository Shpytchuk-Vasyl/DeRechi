import type { Page } from "@playwright/test"
import { expect, test } from "../fixtures/base"
import { ItemCard, ItemDetail, ItemPage, RouteDialog, Shell, Toasts } from "../fixtures/pages"
import { locateAt, nationalUaPhone, noticeText } from "../fixtures/pages/report-data"
import {
  chooseWithKeyboard,
  openReportPage,
  ReportFlow,
  tabTo,
} from "../fixtures/pages/report-flow"
import * as api from "../support/api"
import { browserMoney, browserNoticeDate } from "../support/browser-intl"
import { CITIES, kyivDate, MAX_DESCRIPTION, MAX_TITLE } from "../support/data"
import { waitForHydration } from "../support/hydration"
import { PHOTO_FIXTURE } from "../support/minio"
import { setCountry } from "../support/site"
import { isWide, placement } from "../support/viewport"

function publishedHeading(page: Page, title: string) {
  return page.getByRole("heading", { level: 1, name: title, exact: true })
}

test.describe("lost notice on the full page", () => {
  test("three steps without a photo publish the notice with what was typed", async ({
    page,
    go,
    t,
    shared,
    data,
    token,
    appLocale,
  }) => {
    const place = shared.place
    const text = noticeText(data, appLocale, "WALLET")
    const contact = data.contact("author")
    const date = kyivDate(-2)

    const form = await openReportPage(page, go, t, "lost")
    await form.fillStepDetails(text)
    await form.throughWhereWhen({ place: place.name, date })
    await form.fillStepContacts({ phone: contact.phone, email: contact.email })
    const id = await form.publish()

    await expect(page).toHaveURL(new RegExp(`/${appLocale}/lost/${id}$`))
    await expect(publishedHeading(page, text.title)).toBeVisible()

    await go(`/lost?search=${token}`)
    const card = new ItemCard(page, t).byTitle(text.title)
    await expect(card).toBeVisible()
    await expect(card).toHaveAttribute("href", `/${appLocale}/lost/${id}`)

    await go(`/lost/${id}`)
    const detail = new ItemPage(page, t, "lost")
    await expect(detail.heading(text.title)).toBeVisible()
    await expect(detail.categoryBadge("WALLET")).toBeVisible()
    await expect(detail.date()).toHaveText(await browserNoticeDate(page, appLocale, date))
    await expect(detail.place()).toContainText(place.name)
    await expect(detail.root().getByText(text.description, { exact: true })).toBeVisible()
    await expect(detail.openPhotoButton()).toHaveCount(0)
    await expect(detail.contact("phone")).toContainText("*")
    await expect(detail.contact("email")).toContainText("*")
  })
})

test.describe("found notice without a photo", () => {
  test("Publish from a restored draft without a photo goes back to step 1 and asks for it", async ({
    page,
    go,
    t,
    shared,
    data,
    appLocale,
    flags,
  }) => {
    const place = shared.place
    const text = noticeText(data, appLocale, "KEYS")
    const contact = data.contact("author")

    const form = await openReportPage(page, go, t, "found")
    await form.seedDraft(2, {
      title: text.title,
      description: text.description,
      date: kyivDate(0),
      categoryId: await api.categoryId(text.category),
      place,
      contact: { phone: contact.phone, email: contact.email, socialMedias: [] },
      consent: true,
    })
    await page.reload()
    await waitForHydration(page)
    await expect(new Toasts(page, t, flags).byText(t("form.draftRestored"))).toBeVisible()
    await form.expectStep(2)

    await form.publishButton().click()
    await form.expectStep(1)
    await expect(form.error(t("form.error.photoRequired"))).toBeVisible()
    await expect(page).toHaveURL(/\/report\/found$/)
  })
})

test.describe("validation", () => {
  test("step 0: title and category are required, title and description stop at their limits", async ({
    page,
    go,
    t,
  }) => {
    const form = await openReportPage(page, go, t, "lost")
    const required = t("form.error.required")

    await form.next()
    await form.expectStep(0)
    await expect(form.title()).toHaveAccessibleDescription(required)
    await expect(form.categorySelect()).toHaveAccessibleDescription(required)
    await expect(form.error(required)).toHaveCount(2)

    await form.category("KEYS")
    await expect(form.categorySelect()).not.toHaveAccessibleDescription(required)
    await expect(form.error(required)).toHaveCount(1)

    await expect(form.title()).toHaveAttribute("maxlength", String(MAX_TITLE))
    await form.title().fill("x".repeat(MAX_TITLE + 1))
    await expect(form.title()).toHaveValue("x".repeat(MAX_TITLE))

    await expect(form.description()).toHaveAttribute("maxlength", String(MAX_DESCRIPTION))
    await form.description().fill("y".repeat(MAX_DESCRIPTION + 1))
    await expect(form.description()).toHaveValue("y".repeat(MAX_DESCRIPTION))
    await expect(form.descriptionCounter(MAX_DESCRIPTION)).toBeVisible()
  })

  test("step 1: only the last 30 days can be picked and the place is required", async ({
    page,
    go,
    t,
    data,
    appLocale,
  }) => {
    const form = await openReportPage(page, go, t, "lost")
    const { calendar } = form
    await form.fillStepDetails(noticeText(data, appLocale))
    await form.next()
    await form.expectStep(1)

    await form.next()
    await form.expectStep(1)
    await expect(form.placeSearch()).toHaveAccessibleDescription(t("form.error.placeRequired"))

    await form.openCalendar()
    await expect(calendar.dayButton(kyivDate(0))).toBeEnabled()
    await calendar.showMonthOf(kyivDate(1))
    await expect(calendar.dayButton(kyivDate(1))).toBeDisabled()
    await calendar.showMonthOf(kyivDate(-31))
    await expect(calendar.dayButton(kyivDate(-31))).toBeDisabled()
    await calendar.showMonthOf(kyivDate(-30))
    await expect(calendar.dayButton(kyivDate(-30))).toBeEnabled()
    await page.keyboard.press("Escape")
    await expect(calendar.root()).toBeHidden()
  })

  test("step 2: reward, phone, email and consent are checked on Publish", async ({
    page,
    go,
    t,
    shared,
    data,
    appLocale,
  }) => {
    const place = shared.place
    const form = await openReportPage(page, go, t, "lost")
    await form.fillStepDetails(noticeText(data, appLocale))
    await form.throughWhereWhen({ place: place.name })

    await form.reward().fill("12a3")
    await expect(form.reward()).toHaveValue("123")
    await form.reward().fill("100000")
    await form.phone().fill("123")
    await form.phone().blur()
    await form.email().fill("not-an-email")

    await form.publishButton().click()
    await form.expectStep(2)
    await expect(form.reward()).toHaveAccessibleDescription(t("form.error.rewardMax"))
    await expect(form.phone()).toHaveAccessibleDescription(t("form.error.phoneFormat"))
    await expect(form.email()).toHaveAccessibleDescription(t("form.error.emailFormat"))
    await expect(form.consent()).toHaveAccessibleDescription(t("form.error.consent"))
    await expect(form.error(t("form.error.consent"))).toBeVisible()
    await expect(page).toHaveURL(/\/report\/lost$/)

    await form.consent().check()
    await expect(form.error(t("form.error.consent"))).toHaveCount(0)
    await form.reward().fill("500")
    await expect(form.reward()).not.toHaveAccessibleDescription(t("form.error.rewardMax"))
  })
})

test.describe("back to the step with an error", () => {
  test("Back keeps the values; Publish with a broken step 0 goes back to step 0", async ({
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
    await form.throughWhereWhen({ place: place.name })
    await form.fillStepContacts({ phone: contact.phone, email: contact.email })

    await form.back()
    await form.expectStep(1)
    await expect(form.placeSearch()).toHaveValue(place.name)
    await form.back()
    await form.expectStep(0)
    await expect(form.title()).toHaveValue(text.title)
    await expect(form.description()).toHaveValue(text.description)
    await expect(form.categorySelect()).toContainText(t(`category.${text.category}`))

    await form.title().fill("")
    await form.next()
    await form.expectStep(0)
    await expect(form.title()).toHaveAccessibleDescription(t("form.error.required"))

    const draft = await form.waitForDraft((saved) => saved.values.title === "")
    await form.seedDraft(2, draft.values)
    await page.reload()
    await waitForHydration(page)
    await expect(new Toasts(page, t, flags).byText(t("form.draftRestored"))).toBeVisible()
    await form.expectStep(2)
    await expect(form.consent()).toBeChecked()

    await form.publishButton().click()
    await form.expectStep(0)
    await expect(form.title()).toHaveAccessibleDescription(t("form.error.required"))
    await expect(page).toHaveURL(/\/report\/lost$/)
  })
})

test.describe("step indicator", () => {
  test("only passed steps are clickable, the current one is marked, values survive", async ({
    page,
    go,
    t,
    shared,
    data,
    appLocale,
  }) => {
    const place = shared.place
    const text = noticeText(data, appLocale, "ELECTRONICS")
    const form = await openReportPage(page, go, t, "lost")

    await expect(form.stepButton(0)).toHaveAttribute("aria-current", "step")
    await expect(form.stepButton(1)).toBeDisabled()
    await expect(form.stepButton(2)).toBeDisabled()

    await form.fillStepDetails(text)
    await form.next()
    await form.expectStep(1)
    await expect(form.stepButton(0)).toBeEnabled()
    await expect(form.stepButton(0)).not.toHaveAttribute("aria-current", "step")
    await expect(form.stepButton(1)).toHaveAttribute("aria-current", "step")
    await expect(form.stepButton(2)).toBeDisabled()
    await form.pickPlace(place.name)

    await form.stepButton(0).click()
    await form.expectStep(0)
    await expect(form.stepButton(0)).toHaveAttribute("aria-current", "step")
    await expect(form.stepButton(1)).toBeDisabled()
    await expect(form.title()).toHaveValue(text.title)
    await expect(form.description()).toHaveValue(text.description)

    await form.next()
    await form.expectStep(1)
    await expect(form.placeSearch()).toHaveValue(place.name)
    await form.next()
    await form.expectStep(2)
    await expect(form.stepButton(0)).toBeEnabled()
    await expect(form.stepButton(1)).toBeEnabled()
    await expect(form.stepButton(2)).toHaveAttribute("aria-current", "step")

    await form.back()
    await form.expectStep(1)
    await expect(form.placeSearch()).toHaveValue(place.name)
  })
})

test.describe("a new place", () => {
  test("“Add … here” pins the typed name at the browser's location", async ({
    page,
    go,
    t,
    data,
    token,
    appLocale,
  }) => {
    await locateAt(page, "lviv")
    const name = `${token} ${CITIES.lviv.names[appLocale]}`
    const text = noticeText(data, appLocale, "ANIMALS")
    const contact = data.contact("author")

    const form = await openReportPage(page, go, t, "lost")
    await form.fillStepDetails(text)
    await form.next()
    await form.expectStep(1)

    await form.placeSearch().fill(name)
    await expect(form.addHereOption(name)).toContainText(t("form.placeNoResults"))
    await expect(form.placeOptions()).toHaveCount(1)
    await form.addPlaceHere(name)
    await form.next()
    await form.expectStep(2)
    await form.fillStepContacts({ phone: contact.phone, email: contact.email })
    const id = await form.publish()

    await go(`/lost/${id}`)
    await expect(new ItemDetail(page, t, "lost").place()).toContainText(name)
  })

  test("without location access the form says so and keeps the place empty", async ({
    page,
    go,
    t,
    data,
    token,
    appLocale,
  }) => {
    await page.context().clearPermissions()
    const name = `${token} ${CITIES.kyiv.names[appLocale]}`

    const form = await openReportPage(page, go, t, "lost")
    await form.fillStepDetails(noticeText(data, appLocale))
    await form.next()
    await form.expectStep(1)

    await form.placeSearch().fill(name)
    await form.addHereOption(name).click()
    await expect(form.placeStatus(t("form.placeAddDenied"))).toBeVisible()

    await form.next()
    await form.expectStep(1)
    await expect(form.placeSearch()).toHaveAccessibleDescription(t("form.error.placeRequired"))
  })
})

test.describe("keyboard in the place search", () => {
  test("the arrows move the selection, Enter picks the option, Escape closes the list", async ({
    page,
    go,
    t,
    shared,
    data,
    appLocale,
  }) => {
    const form = await openReportPage(page, go, t, "lost")
    await form.fillStepDetails(noticeText(data, appLocale))
    await form.next()
    await form.expectStep(1)

    const query = shared.marker
    const search = form.placeSearch()
    const options = form.placeOptions()
    await search.fill(query)
    await expect(form.addHereOption(query)).toBeVisible()
    await expect(options.nth(0)).toHaveAttribute("aria-selected", "true")

    await search.press("ArrowDown")
    await expect(options.nth(1)).toHaveAttribute("aria-selected", "true")
    const second = await options.nth(1).getAttribute("id")
    if (!second) throw new Error("the place option has no id")
    await expect(search).toHaveAttribute("aria-activedescendant", second)
    await search.press("ArrowUp")
    await expect(options.nth(0)).toHaveAttribute("aria-selected", "true")

    const chosen = (await options.nth(0).innerText()).trim()
    expect(chosen).toContain(shared.marker)
    await search.press("Enter")
    await expect(search).toHaveValue(chosen)
    await expect(form.placeList()).toBeHidden()

    await search.fill(query)
    await expect(form.placeList()).toBeVisible()
    await search.press("Escape")
    await expect(form.placeList()).toBeHidden()
    await expect(search).toHaveAttribute("aria-expanded", "false")
  })
})

test.describe("currency and phone follow the country", () => {
  const OTHER_CURRENCY = "EUR"

  test("UA: UAH by default, a national number gets +380, a chosen currency is kept", async ({
    page,
    context,
    go,
    t,
    shared,
    data,
    appLocale,
  }) => {
    const currencies = (await api.countries()).map((country) => country.currency)
    expect(currencies, `Client-API supports a ${OTHER_CURRENCY} country`).toContain(OTHER_CURRENCY)

    await setCountry(context, "UA")
    const place = shared.place
    const text = noticeText(data, appLocale, "JEWELRY")
    const contact = data.contact("author")

    const form = await openReportPage(page, go, t, "lost")
    await form.fillStepDetails(text)
    await form.throughWhereWhen({ place: place.name })

    await expect(form.currencySelect()).toContainText("UAH")
    await form.phone().fill(nationalUaPhone(contact.phone))
    await form.phone().blur()
    await expect(form.phone()).toHaveValue(contact.phone)

    await form.reward().fill("500")
    await form.currency(OTHER_CURRENCY)
    await expect(form.currencySelect()).toContainText(OTHER_CURRENCY)
    await form.email().fill(contact.email)
    await form.consent().check()
    const id = await form.publish()

    await go(`/lost/${id}`)
    const amount = await browserMoney(page, appLocale, 500, OTHER_CURRENCY)
    await expect(new ItemDetail(page, t, "lost").reward(amount)).toBeVisible()
  })

  test("PL: a place added here gets PLN, a national number gets +48", async ({
    page,
    context,
    go,
    t,
    data,
    token,
    appLocale,
  }) => {
    const countries = await api.countries()
    const poland = countries.find((country) => country.code === "PL")
    if (!poland) throw new Error("e2e: Client-API does not support PL")

    await setCountry(context, "PL")
    await locateAt(page, "warsaw")

    const form = await openReportPage(page, go, t, "lost")
    await form.fillStepDetails(noticeText(data, appLocale))
    await form.next()
    await form.expectStep(1)
    await form.addPlaceHere(`${token} ${CITIES.warsaw.names[appLocale]}`)
    await form.next()
    await form.expectStep(2)

    await expect(form.currencySelect()).toContainText(poland.currency)
    await form.phone().fill("512 345 678")
    await form.phone().blur()
    await expect(form.phone()).toHaveValue("+48512345678")
    await expect(form.root().locator('form a[href*="/terms?country=PL"]')).toHaveCount(1)
  })
})

test.describe("report modal", () => {
  test("from a list: the report link opens the modal, Cancel goes back to the list", {
    tag: "@responsive",
  }, async ({ page, go, t, appLocale }) => {
    await go("/found")
    await new Shell(page, t).openReport("found")

    const form = new ReportFlow(page, t, "found", { modal: true })
    const dialog = new RouteDialog(page, t, form.dialog())
    await dialog.expectOpen()
    await dialog.expectShape()

    await form.cancelButton().click()
    await expect(page).toHaveURL(new RegExp(`/${appLocale}/found$`))
    await expect(form.dialog()).toBeHidden()
  })

  test("publishing from the modal lands in the new item's modal", async ({
    page,
    go,
    t,
    shared,
    data,
    token,
    appLocale,
  }) => {
    const place = shared.place
    const text = noticeText(data, appLocale, "WALLET")
    const contact = data.contact("author")

    await go(`/lost?search=${token}`)
    await new Shell(page, t).openReport("lost")
    const form = new ReportFlow(page, t, "lost", { modal: true })
    await expect(form.dialog()).toBeVisible()
    await form.fillStepDetails(text)
    await form.throughWhereWhen({ place: place.name })
    await form.fillStepContacts({ phone: contact.phone, email: contact.email })
    const id = await form.publish()

    await expect(page).toHaveURL(new RegExp(`/${appLocale}/lost/${id}$`))
    const itemModal = page.getByRole("dialog", { name: t("item.lost"), exact: true })
    await expect(
      itemModal.getByRole("heading", { level: 1, name: text.title, exact: true }),
    ).toBeVisible()
    await expect(form.dialog()).toHaveCount(0)
  })
})

test.describe("live preview", () => {
  test("follows title, photo, date, place and reward", async ({
    page,
    go,
    t,
    shared,
    data,
    appLocale,
  }) => {
    const place = shared.place
    const text = noticeText(data, appLocale, "BAGS")
    const form = await openReportPage(page, go, t, "found")
    const preview = form.preview()

    await expect(preview).toBeVisible()
    await expect(form.previewTitle()).toHaveText(t("form.previewEmpty"))
    await expect(preview.getByText(t("form.previewNoPlace"), { exact: true })).toBeVisible()
    await expect(form.previewDate()).toHaveAttribute("datetime", kyivDate(0))

    await form.fillStepDetails(text)
    await expect(form.previewTitle()).toHaveText(text.title)

    await form.next()
    await form.expectStep(1)
    await form.photo(PHOTO_FIXTURE)
    await expect(preview.locator('img[src^="blob:"]')).toBeVisible()
    await form.pickDate(kyivDate(-2))
    await expect(form.previewDate()).toHaveAttribute("datetime", kyivDate(-2))
    await form.pickPlace(place.name)
    await expect(preview.getByText(place.name, { exact: true })).toBeVisible()

    await form.next()
    await form.expectStep(2)
    await form.reward().fill("500")
    const amount = await browserMoney(page, appLocale, 500, "UAH")
    await expect(preview.getByText(t("item.reward", { amount }), { exact: true })).toBeVisible()
  })

  test("sits beside the form from 1024 px and under it below", {
    tag: "@responsive",
  }, async ({ page, go, t }) => {
    const form = await openReportPage(page, go, t, "found")
    await expect(form.preview()).toBeVisible()

    await expect
      .poll(() => placement(form.nextButton(), form.preview()))
      .toBe(isWide(page) ? "beside" : "below")
  })
})

test.describe("keyboard only", () => {
  test("the form can be filled and published without a mouse", async ({
    page,
    go,
    t,
    shared,
    data,
    appLocale,
  }) => {
    const place = shared.place
    const text = noticeText(data, appLocale, "DOCUMENTS")
    const contact = data.contact("author")
    const form = await openReportPage(page, go, t, "lost")
    const keyboard = page.keyboard

    await tabTo(page, form.title())
    await keyboard.type(text.title)
    await tabTo(page, form.categorySelect())
    await chooseWithKeyboard(page, form.categorySelect(), t(`category.${text.category}`))
    await tabTo(page, form.description())
    await keyboard.type(text.description)
    await tabTo(page, form.nextButton())
    await keyboard.press("Enter")
    await form.expectStep(1)

    await tabTo(page, form.placeSearch())
    await keyboard.type(place.name)
    const option = form.placeList().getByRole("option", { name: place.name, exact: true })
    await expect(option).toHaveAttribute("aria-selected", "true")
    await keyboard.press("Enter")
    await expect(form.placeSearch()).toHaveValue(place.name)
    await tabTo(page, form.nextButton())
    await keyboard.press("Enter")
    await form.expectStep(2)

    await tabTo(page, form.phone())
    await keyboard.type(contact.phone)
    await tabTo(page, form.email())
    await keyboard.type(contact.email)
    await tabTo(page, form.consent())
    await keyboard.press("Space")
    await expect(form.consent()).toBeChecked()
    await tabTo(page, form.publishButton())
    await keyboard.press("Enter")

    await expect(page).toHaveURL(new RegExp(`/${appLocale}/lost/\\d+$`), { timeout: 30_000 })
    await expect(publishedHeading(page, text.title)).toBeVisible()
  })
})
