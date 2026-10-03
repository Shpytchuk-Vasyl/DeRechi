"use client"

import { useState } from "react"
import { claimStatus } from "@/app/actions/claim"
import { Button } from "@/components/pouf/Button"
import { Card } from "@/components/pouf/card"
import { ErrorNote } from "@/components/pouf/feedback"
import { Field, Input } from "@/components/pouf/Input"
import { Heading, Text } from "@/components/pouf/text"
import { CLAIM_TOKEN } from "@/schema/claim-schema"
import { type FakePaymentResult, sendFakePayment } from "./actions"

const STATUSES = ["COMPLETED", "CONFIRMED", "CANCELLED"] as const

type Status = Awaited<ReturnType<typeof claimStatus>> | null

export function FourthwallBench() {
  const [checkout, setCheckout] = useState("")
  const [status, setStatus] = useState<(typeof STATUSES)[number]>("COMPLETED")
  const [token, setToken] = useState("")
  const [sending, setSending] = useState(false)
  const [result, setResult] = useState<FakePaymentResult | null>(null)
  const [claim, setClaim] = useState<Status>(null)
  const [checking, setChecking] = useState(false)

  async function send() {
    setSending(true)
    setResult(null)
    setResult(await sendFakePayment(checkout, status))
    setSending(false)
  }

  async function check() {
    setChecking(true)
    setClaim(await claimStatus(token.trim()))
    setChecking(false)
  }

  function readToken(cookieHeader: string) {
    const found = cookieHeader
      .split(";")
      .map((part) => part.trim())
      .filter((part) => part.startsWith("DERECHI_CLAIM_"))
      .map((part) => part.slice(part.indexOf("=") + 1))
      .find((value) => CLAIM_TOKEN.test(value))
    if (found) setToken(found)
  }

  return (
    <Card>
      <div className="flex flex-col gap-5">
        <div>
          <Heading level={2}>Fourthwall: оплата без грошей</Heading>
          <Text muted className="mt-1 block leading-relaxed">
            Шле на вебхук Client-API такий самий ORDER_PLACED, як Fourthwall після покупки,
            підписаний секретом з FOURTHWALL_WEBHOOK_SECRET. Встав посилання «Оплатити на
            Fourthwall» з діалогу або сам id варіанта.
          </Text>
        </div>

        <Field label="Checkout URL або variant id">
          {(id, describedBy) => (
            <Input
              id={id}
              describedBy={describedBy}
              value={checkout}
              onChange={setCheckout}
              placeholder="https://derechi-shop.fourthwall.com/cart/checkout?products=…:1"
              mono
            />
          )}
        </Field>

        <div className="flex flex-wrap items-center gap-2">
          <Text size="sm" muted>
            Статус:
          </Text>
          {STATUSES.map((it) => (
            <Button
              key={it}
              size="sm"
              variant={status === it ? "solid" : "quiet"}
              onClick={() => setStatus(it)}
            >
              {it}
            </Button>
          ))}
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <Button onClick={send} loading={sending} disabled={checkout.trim().length === 0}>
            Імітувати оплату
          </Button>
          {result?.ok ? (
            <Text size="sm" mono className={result.status === 200 ? "text-mint" : "text-orange"}>
              HTTP {result.status} · {result.variantId}
            </Text>
          ) : null}
        </div>

        {result && !result.ok ? (
          <ErrorNote>
            {result.reason === "variant"
              ? "Не бачу id варіанта: встав checkout URL з діалогу або сам id."
              : result.reason === "production"
                ? "Тільки для розробки."
                : `Не вдалося надіслати: ${result.detail ?? "невідома помилка"}`}
          </ErrorNote>
        ) : null}

        {result?.ok ? (
          <details className="rounded-control bg-bg px-4 py-3">
            <summary className="cursor-pointer font-bold text-sm">Що пішло на {result.url}</summary>
            <pre className="mt-3 overflow-x-auto text-xs leading-relaxed">{result.body}</pre>
          </details>
        ) : null}

        <div className="border-border border-t pt-5">
          <Field
            label="Token заявки (кукі DERECHI_CLAIM_…)"
            hint="Після імітації статус має стати paid, а за мить contactsSent."
          >
            {(id, describedBy) => (
              <Input
                id={id}
                describedBy={describedBy}
                value={token}
                onChange={setToken}
                placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                mono
              />
            )}
          </Field>
          <div className="mt-3 flex flex-wrap items-center gap-3">
            <Button variant="quiet" size="sm" onClick={() => readToken(document.cookie)}>
              Взяти з кукі
            </Button>
            <Button
              size="sm"
              onClick={check}
              loading={checking}
              disabled={!CLAIM_TOKEN.test(token.trim())}
            >
              Перевірити статус
            </Button>
            {claim ? (
              <Text size="sm" mono>
                {claim.ok
                  ? `paid=${claim.paid} contactsSent=${claim.contactsSent} checkout=${claim.checkoutUrl ? "так" : "ні"}`
                  : "заявку не знайдено"}
              </Text>
            ) : null}
          </div>
        </div>
      </div>
    </Card>
  )
}
