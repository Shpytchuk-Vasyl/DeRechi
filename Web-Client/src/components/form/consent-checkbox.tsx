"use client"

import type { ReactNode } from "react"
import { Controller, useFormContext } from "react-hook-form"
import { Checkbox } from "@/components/pouf/checkbox"
import { useFieldMessage } from "@/hooks/use-field-message"
import { ERROR_KEYS } from "@/schema/report-schema"

type Props = {
  children: ReactNode
}

export function ConsentCheckbox({ children }: Props) {
  const message = useFieldMessage(ERROR_KEYS)
  const { control } = useFormContext()

  return (
    <Controller
      control={control}
      name="consent"
      render={({ field, fieldState }) => (
        <Checkbox
          ref={field.ref}
          checked={field.value === true}
          onChange={(checked) => field.onChange(checked === true)}
          label={children}
          error={message(fieldState.error?.message)}
        />
      )}
    />
  )
}
