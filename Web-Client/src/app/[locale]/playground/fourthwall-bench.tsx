"use client"

import { useState } from "react"
import { claimStatus } from "@/app/actions/claim"
import { Button } from "@/components/pouf/Button"
import { Card } from "@/components/pouf/card"
import { ErrorNote } from "@/components/pouf/feedback"
import { Field, Input } from "@/components/pouf/Input"
import { Heading, Text } from "@/components/pouf/text"
import { type FakePaymentResult, sendFakePayment } from "./actions"

const STATUSES = ["COMPLETED", "CONFIRMED", "CANCELLED"] as const

type Status = Awaited<ReturnType<typeof claimStatus>> | null

const CLAIM_REF = /^(lost|found)\/([\w-]{1,64})\/([1-9]\d{0,17})$/
const CLAIM_COOKIE = /^DERECHI_CLAIM_(lost|found)_([\w-]{1,64})=([1-9]\d{0,17})$/

export function FourthwallBench() {
  const [checkout, setCheckout] = useState("")
  const [status, setStatus] = useState<(typeof STATUSES)[number]>("COMPLETED")
  const [claimRef, setClaimRef] = useState("")
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

  const ref = CLAIM_REF.exec(claimRef.trim())

  async function check() {
    if (!ref) return
    const [, kind, itemId, claimId] = ref
    setChecking(true)
    setClaim(await claimStatus(kind as "lost" | "found", itemId, claimId))
    setChecking(false)
  }

  function readClaim(cookieHeader: string) {
    const found = cookieHeader
      .split(";")
      .map((part) => CLAIM_COOKIE.exec(part.trim()))
      .find((match) => match !== null)
    if (found) setClaimRef(`${found[1]}/${found[2]}/${found[3]}`)
  }

  return (
    <Card>
      <div className="flex flex-col gap-5">
        <div>
          <Heading level={2}>Fourthwall: оплата без грошей</Heading>
          <Text muted className="mt-1 block leading-relaxed">
            Шле на вебхук Client-API такий самий ORDER_PLACED, як Fourthwall після покупки,
            підписаний секретом з FOURTHWALL_WEBHOOK_SECRET. Встав посилання «Перейти до оплати» з
            діалогу або сам id варіанта.
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
            label="Заявка: вид/id речі/id заявки (кукі DERECHI_CLAIM_…)"
            hint="Після імітації статус має стати paid, а за мить contactsSent."
          >
            {(id, describedBy) => (
              <Input
                id={id}
                describedBy={describedBy}
                value={claimRef}
                onChange={setClaimRef}
                placeholder="lost/7/11"
                mono
              />
            )}
          </Field>
          <div className="mt-3 flex flex-wrap items-center gap-3">
            <Button variant="quiet" size="sm" onClick={() => readClaim(document.cookie)}>
              Взяти з кукі
            </Button>
            <Button size="sm" onClick={check} loading={checking} disabled={!ref}>
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
