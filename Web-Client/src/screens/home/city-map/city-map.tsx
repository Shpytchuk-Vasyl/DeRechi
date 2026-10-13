import { cx } from "class-variance-authority"
import { MapPin } from "lucide-react"
import { getLocale, getTranslations } from "next-intl/server"
import { Stack } from "@/components/pouf/layout"
import { Heading } from "@/components/pouf/text"
import { Link } from "@/i18n/navigation"
import { paths } from "@/i18n/paths"
import { CITIES, type City } from "@/lib/cities"
import CityPicker from "./city-picker"
import { MAP, UKRAINE_PATH } from "./outline"

type Side = "top" | "bottom" | "left" | "right"

const LABEL_SIDE: Partial<Record<string, Side>> = {
  chernivtsi: "bottom",
  kherson: "bottom",
  khmelnytskyi: "bottom",
  kropyvnytskyi: "bottom",
  odesa: "bottom",
  uzhhorod: "bottom",
  vinnytsia: "bottom",
  zaporizhzhia: "bottom",
  zhytomyr: "bottom",
  rivne: "right",
}

const UNLABELLED_ON_PHONE = new Set(["ivano-frankivsk", "khmelnytskyi"])

const LABEL_PLACEMENT: Record<Side, string> = {
  top: "max-lg:bottom-full max-lg:left-1/2 max-lg:-translate-x-1/2",
  bottom: "max-lg:top-full max-lg:left-1/2 max-lg:-translate-x-1/2",
  left: "max-lg:right-full max-lg:top-1/2 max-lg:-translate-y-1/2",
  right: "max-lg:left-full max-lg:top-1/2 max-lg:-translate-y-1/2",
}

function position(city: City): React.CSSProperties {
  const x = MAP.pad + (city.lon - MAP.lonMin) * MAP.k * MAP.scale
  const y = MAP.pad + (MAP.latMax - city.lat) * MAP.scale
  return { left: `${(x / MAP.width) * 100}%`, top: `${(y / MAP.height) * 100}%` }
}

export default async function CityMap() {
  const [locale, t, tc] = await Promise.all([
    getLocale(),
    getTranslations("home"),
    getTranslations("city"),
  ])
  const cities = CITIES.map((city) => ({ ...city, name: tc(`${city.slug}.name`) }))

  return (
    <Stack asChild gap={4}>
      <section aria-labelledby="cities-heading">
        <Heading level={2} id="cities-heading">
          {t("citiesTitle")}
        </Heading>

        <div className="relative mx-auto aspect-1000/683 w-full max-w-5xl -rotate-1">
          <svg
            viewBox={`0 0 ${MAP.width} ${MAP.height}`}
            className="absolute inset-0 size-full overflow-visible"
            aria-hidden
          >
            <path d={UKRAINE_PATH} transform="translate(8 12)" className="fill-ink/15" />
            <path
              d={UKRAINE_PATH}
              className="fill-purple stroke-surface"
              strokeWidth={14}
              strokeLinejoin="round"
            />
          </svg>

          <ul>
            {cities.map((city) => (
              <li
                key={city.slug}
                className="absolute focus-within:z-10 hover:z-10"
                style={position(city)}
              >
                <Link
                  href={paths.city(city.slug)}
                  className="group absolute -translate-x-1/2 -translate-y-full px-0.5 pt-0.5 no-underline lg:px-1 lg:pt-1"
                >
                  <MapPin
                    className="block size-4 fill-primary  drop-shadow-ink/30 drop-shadow-md transition-transform group-hover:-translate-y-0.5 group-hover:scale-110 group-focus-visible:-translate-y-0.5 group-focus-visible:scale-110 motion-reduce:transition-none sm:size-6 lg:size-8 [&_circle]:fill-surface"
                    strokeWidth={2}
                    aria-hidden
                  />
                  <span
                    className={cx(
                      "pointer-events-none absolute w-min rounded-pill bg-surface px-1 text-center font-extrabold text-[9px] text-ink leading-tight shadow-ink/20 shadow-sm transition-opacity motion-reduce:transition-none sm:px-2 sm:py-0.5 sm:text-xs lg:bottom-full lg:left-1/2 lg:w-auto lg:-translate-x-1/2 lg:whitespace-nowrap lg:px-2.5 lg:text-sm pointer-fine:opacity-0 pointer-fine:group-hover:opacity-100 pointer-fine:group-focus-visible:opacity-100",
                      LABEL_PLACEMENT[LABEL_SIDE[city.slug] ?? "top"],
                      UNLABELLED_ON_PHONE.has(city.slug) && "max-sm:sr-only",
                    )}
                  >
                    {city.name}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </div>

        <div className="lg:hidden">
          <CityPicker
            cities={[...cities].sort((a, b) => a.name.localeCompare(b.name, locale))}
            label={t("citiesPick")}
          />
        </div>
      </section>
    </Stack>
  )
}
