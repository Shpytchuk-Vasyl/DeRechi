"use client"

import { type ReactNode, useId } from "react"
import { Controller, useFormContext } from "react-hook-form"
import { Checkbox } from "@/components/pouf/checkbox"
import { FieldError } from "@/components/pouf/Input"
import { useFieldMessage } from "@/hooks/use-field-message"
import { ERROR_KEYS } from "@/schema/report-schema"

type Props = {
  children: ReactNode
}

export function ConsentCheckbox({ children }: Props) {
  const id = useId()
  const message = useFieldMessage(ERROR_KEYS)
  const { control } = useFormContext()

  return (
    <Controller
      control={control}
      name="consent"
      render={({ field, fieldState }) => {
        const error = message(fieldState.error?.message)
        return (
          <div className="flex flex-col gap-2">
            <div className="flex items-start gap-3">
              <Checkbox
                ref={field.ref}
                id={id}
                checked={field.value === true}
                onChange={(checked) => field.onChange(checked === true)}
                invalid={Boolean(error)}
                describedBy={error ? `${id}-err` : undefined}
              />
              <label htmlFor={id} className="cursor-pointer pt-1 text-muted-foreground text-sm">
                {children}
              </label>
            </div>
            {error ? <FieldError id={`${id}-err`}>{error}</FieldError> : null}
          </div>
        )
      }}
    />
  )
}
