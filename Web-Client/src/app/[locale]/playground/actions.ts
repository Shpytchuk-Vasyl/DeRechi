"use server"

import { serverEnv } from "@/lib/env/server"
import {
  FOURTHWALL_SIGNATURE_HEADER,
  FOURTHWALL_WEBHOOK_PATH,
  fakeOrderPlaced,
  signFourthwallBody,
  variantIdOf,
} from "./fourthwall-webhook"

export type FakePaymentResult =
  | { ok: true; status: number; url: string; variantId: string; body: string }
  | { ok: false; reason: "production" | "variant" | "failed"; detail?: string }

const STATUSES = ["COMPLETED", "CONFIRMED", "CANCELLED"] as const
type OrderStatus = (typeof STATUSES)[number]

export async function sendFakePayment(
  checkoutUrlOrVariantId: string,
  status: string,
): Promise<FakePaymentResult> {
  if (process.env.NODE_ENV === "production") {
    return { ok: false, reason: "production" }
  }
  const variantId =
    typeof checkoutUrlOrVariantId === "string" ? variantIdOf(checkoutUrlOrVariantId) : null
  if (!variantId) {
    return { ok: false, reason: "variant" }
  }
  const orderStatus: OrderStatus = STATUSES.includes(status as OrderStatus)
    ? (status as OrderStatus)
    : "COMPLETED"

  const body = JSON.stringify(fakeOrderPlaced({ variantId, status: orderStatus }), null, 2)
  const url = new URL(FOURTHWALL_WEBHOOK_PATH, serverEnv.GRAPHQL_URL).toString()

  try {
    const response = await fetch(url, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        [FOURTHWALL_SIGNATURE_HEADER]: signFourthwallBody(
          process.env.FOURTHWALL_WEBHOOK_SECRET || "dev-secret",
          body,
        ),
      },
      body,
      cache: "no-store",
    })
    return { ok: true, status: response.status, url, variantId, body }
  } catch (error) {
    console.error("Fake payment failed", error)
    return {
      ok: false,
      reason: "failed",
      detail: error instanceof Error ? error.message : String(error),
    }
  }
}
