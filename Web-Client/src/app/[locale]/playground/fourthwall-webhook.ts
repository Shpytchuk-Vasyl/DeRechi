import { createHmac, randomUUID } from "node:crypto"

export const FOURTHWALL_SIGNATURE_HEADER = "X-Fourthwall-Hmac-SHA256"
export const FOURTHWALL_WEBHOOK_PATH = "/api/client/webhooks/fourthwall"

const CHECKOUT_VARIANT = /[?&]products=([^:&]+)(?::\d+)?/

export function signFourthwallBody(secret: string, body: string): string {
  return createHmac("sha256", secret).update(body, "utf8").digest("base64")
}

export function variantIdOf(checkoutUrlOrVariantId: string): string | null {
  const value = checkoutUrlOrVariantId.trim()
  if (value.length === 0) return null
  const match = CHECKOUT_VARIANT.exec(value)
  if (match) return decodeURIComponent(match[1] ?? "")
  if (/^[\w-]{1,64}$/.test(value)) return value
  return null
}

export type FakeOrder = {
  variantId: string
  status?: string
  email?: string
  testMode?: boolean
  now?: Date
  orderId?: string
}

export function fakeOrderPlaced({
  variantId,
  status = "COMPLETED",
  email = "playground@derechi.local",
  testMode = true,
  now = new Date(),
  orderId = `pg-${randomUUID()}`,
}: FakeOrder) {
  const at = now.toISOString()
  return {
    testMode,
    id: `evt-${randomUUID()}`,
    webhookId: "playground",
    shopId: "playground",
    type: "ORDER_PLACED",
    apiVersion: "V1",
    createdAt: at,
    data: {
      id: orderId,
      friendlyId: `PG-${orderId.slice(-6).toUpperCase()}`,
      status,
      email,
      username: "Playground",
      amounts: {
        subtotal: { value: 1, currency: "USD" },
        total: { value: 1, currency: "USD" },
      },
      offers: [
        {
          id: "playground-product",
          name: "Author's phone number (playground)",
          variant: { id: variantId, quantity: 1, unitPrice: { value: 1, currency: "USD" } },
        },
      ],
      createdAt: at,
    },
  }
}
