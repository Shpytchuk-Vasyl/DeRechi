import { randomUUID } from "node:crypto"
import { expect, test } from "../fixtures/base"
import { ClaimReturnPage } from "../fixtures/pages/claim-return"

test.describe("confirming a return", () => {
  test("an unknown token and a token over 64 characters are invalid", async ({ page, go, t }) => {
    const view = new ClaimReturnPage(page, t)
    const tokens = [
      { name: "unknown", token: randomUUID() },
      { name: "65 characters", token: "e2e".padEnd(65, "x") },
    ]

    for (const { name, token } of tokens) {
      await test.step(name, async () => {
        const response = await go(`/claims/${token}`)
        expect(response?.status()).toBe(200)
        expect(response?.headers()["referrer-policy"]).toBe("no-referrer")
        await expect(view.title()).toBeVisible()
        await expect(view.confirmButton()).toBeEnabled()

        await view.confirm()
        await expect(view.invalidTitle()).toBeVisible()
        await expect(view.homeLink()).toBeVisible()
        await expect(view.confirmButton()).toHaveCount(0)
      })
    }
  })
})
