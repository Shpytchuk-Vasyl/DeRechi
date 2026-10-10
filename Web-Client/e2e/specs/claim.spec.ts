import { randomUUID } from "node:crypto"
import { expect, test } from "../fixtures/base"
import { ClaimCard, exactText, Toasts } from "../fixtures/pages"
import { CLAIM_COOKIE_MAX_AGE, claimCookie, withPlusTag } from "../support/claims"
import type { ItemKind } from "../support/data"
import { setCountry } from "../support/site"

const DAY = 24 * 60 * 60

test.describe("claim on a lost item", () => {
  test("sends the claimant's contacts, sets the claim cookie and remembers the claim", async ({
    page,
    context,
    go,
    t,
    shared,
    data,
    flags,
  }) => {
    const kind: ItemKind = "lost"
    const item = shared.items.claimLost
    const claimant = data.contact()
    await go(item.path)

    const card = new ClaimCard(page, t, kind)
    await expect(card.title()).toBeVisible()
    await card.open()
    await expect(card.phone()).toBeFocused()
    await new Toasts(page, t, flags).dismissSmsOutage()

    await card.fill(claimant, { messengers: ["TELEGRAM"] })
    await expect(card.messenger("TELEGRAM")).toBeChecked()
    await expect(card.consent()).toBeChecked()
    await card.submit()

    await expect(card.done()).toBeVisible()
    await expect(card.root.getByText(t("claim.doneOwner"), { exact: true })).toBeVisible()
    await expect(card.repeated()).toHaveCount(0)

    const cookie = await claimCookie(context, kind, item.id)
    if (!cookie) throw new Error(`no DERECHI_CLAIM_${kind}_${item.id} cookie after the claim`)
    expect(cookie.value).toMatch(/^[1-9]\d*$/)
    expect(cookie.path).toBe("/")
    expect(cookie.httpOnly).toBe(false)
    const now = Date.now() / 1000
    expect(cookie.expires).toBeGreaterThan(now + CLAIM_COOKIE_MAX_AGE - DAY)
    expect(cookie.expires).toBeLessThan(now + CLAIM_COOKIE_MAX_AGE + DAY)

    await page.reload()
    await expect(card.done()).toBeVisible()
    await expect(card.repeated()).toBeVisible()
    await expect(card.openButton()).toHaveCount(0)
  })
})

test.describe("claim form validation", () => {
  test("phone, email and consent errors are alerts; legal links carry the item's country", async ({
    page,
    context,
    go,
    t,
    shared,
    flags,
    appLocale,
  }) => {
    const item = shared.items.claimLost
    expect(item.input.place.countryCode).toBe("UA")
    await setCountry(context, "PL")
    await go(item.path)

    const card = new ClaimCard(page, t, "lost")
    await card.open()
    await new Toasts(page, t, flags).dismissSmsOutage()

    const alert = (
      key: "form.error.phoneFormat" | "form.error.emailFormat" | "form.error.consent",
    ) => card.root.getByRole("alert").filter({ hasText: exactText(t(key)) })

    await card.submit()
    await expect(alert("form.error.phoneFormat")).toBeVisible()
    await expect(alert("form.error.emailFormat")).toBeVisible()
    await expect(alert("form.error.consent")).toBeVisible()
    await expect(card.phone()).toHaveAttribute("aria-invalid", "true")
    await expect(card.email()).toHaveAttribute("aria-invalid", "true")

    await card.fill({ phone: "+12", email: "not-an-email" })
    await card.submit()
    await expect(alert("form.error.phoneFormat")).toBeVisible()
    await expect(alert("form.error.emailFormat")).toBeVisible()
    await expect(alert("form.error.consent")).toHaveCount(0)
    await expect(card.done()).toHaveCount(0)
    expect(await claimCookie(context, "lost", item.id)).toBeUndefined()

    for (const which of ["terms", "privacy"] as const) {
      const link = card.legalLink(which)
      await expect(link).toHaveAttribute(
        "href",
        new RegExp(`^/${appLocale}/${which}\\?country=${item.input.place.countryCode}$`),
      )
      await expect(link).toHaveAttribute("target", "_blank")
    }
  })
})

test.describe("disposable email", () => {
  test("a disposable address is refused on the email field", async ({
    page,
    context,
    go,
    t,
    shared,
    data,
    flags,
  }) => {
    const item = shared.items.claimLost

    await go(item.path)
    const card = new ClaimCard(page, t, "lost")
    await card.open()
    await new Toasts(page, t, flags).dismissSmsOutage()
    await card.fill({
      phone: data.contact().phone,
      email: `e2e-${randomUUID().slice(0, 8)}@mailinator.com`,
    })
    await card.submit()

    await expect(
      card.root.getByRole("alert").filter({ hasText: exactText(t("form.error.disposableEmail")) }),
      "Client-API refuses disposable domains (its list from DISPOSABLE_EMAILS_URL loaded)",
    ).toBeVisible()
    await expect(card.email()).toHaveAttribute("aria-invalid", "true")
    await expect(card.email()).toBeFocused()
    await expect(card.done()).toHaveCount(0)
    expect(await claimCookie(context, "lost", item.id)).toBeUndefined()
  })
})

test.describe("repeated claim", () => {
  test("the same phone, or the same email with a +tag, is the claim already sent", async ({
    page,
    context,
    go,
    t,
    shared,
    data,
    flags,
  }) => {
    const kind: ItemKind = "found"
    const item = shared.items.claimFound
    const first = await data.claim(kind, item.id, data.contact("c"))
    expect(first.repeated).toBe(false)

    const cases = [
      {
        name: "same phone, another email",
        contact: { phone: first.contact.phone, email: data.contact("c").email },
      },
      {
        name: "another phone, same email with a +tag",
        contact: { phone: data.contact("c").phone, email: withPlusTag(first.contact.email, "e2e") },
      },
    ]

    const card = new ClaimCard(page, t, kind)
    for (const { name, contact } of cases) {
      await test.step(name, async () => {
        await context.clearCookies()
        await go(item.path)
        await card.open()
        await new Toasts(page, t, flags).dismissSmsOutage()
        await card.fill(contact)
        await card.submit()

        await expect(card.done()).toBeVisible()
        await expect(card.repeated()).toBeVisible()
        expect((await claimCookie(context, kind, item.id))?.value).toBe(first.id)
      })
    }
  })
})
