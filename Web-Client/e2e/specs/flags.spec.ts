import { expect, test } from "../fixtures/base"

test.describe("maintenance off", () => {
  test("/<locale>/maintenance is a 404", async ({ request, appLocale }) => {
    const response = await request.get(`/${appLocale}/maintenance`)
    expect(response.status()).toBe(404)
  })
})
