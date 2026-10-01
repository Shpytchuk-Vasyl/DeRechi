import { cx } from "class-variance-authority"
import { MapPin } from "lucide-react"
import Image, { type StaticImageData } from "next/image"
import { getTranslations } from "next-intl/server"
import bag from "@/assets/3d/bag.png"
import fileText from "@/assets/3d/file-text.png"
import key from "@/assets/3d/key.png"
import wallet from "@/assets/3d/wallet.png"
import zoom from "@/assets/3d/zoom.png"
import { Badge } from "@/components/pouf/media"

const PIECES: { src: StaticImageData; className: string; size: string }[] = [
  {
    src: key,
    className:
      "top-[4%] left-[2%] w-[30%] -rotate-12 [animation-duration:5s] [animation-delay:-1s]",
    size: "150px",
  },
  {
    src: bag,
    className: "top-[2%] right-[2%] w-[29%] rotate-6 [animation-duration:7s] [animation-delay:-2s]",
    size: "150px",
  },
  {
    src: wallet,
    className:
      "bottom-[3%] left-[5%] w-[31%] -rotate-3 [animation-duration:6.5s] [animation-delay:-3s]",
    size: "150px",
  },
  {
    src: fileText,
    className: "right-[4%] bottom-[5%] w-[28%] rotate-6 [animation-duration:5.5s]",
    size: "150px",
  },
  {
    src: zoom,
    className: "top-[16%] left-[27%] z-10 w-[48%] [animation-duration:6s] [animation-delay:-0.5s]",
    size: "240px",
  },
]

export default async function HeroScene() {
  const t = await getTranslations("home.sample.keys")

  return (
    <div className="relative mx-auto aspect-5/4 w-full max-w-120" aria-hidden>
      <div className="absolute inset-[6%] rounded-pill bg-[radial-gradient(circle_at_50%_45%,color-mix(in_srgb,var(--purple)_45%,transparent),transparent_70%)]" />

      <div className="absolute bottom-[14%] left-[34%] h-[6%] w-[32%] rounded-[50%] bg-ink/15 blur-md" />

      {PIECES.map((piece) => (
        <div key={piece.src.src} className={cx("hero-float absolute", piece.className)}>
          <Image
            src={piece.src}
            alt=""
            sizes={piece.size}
            priority
            placeholder="blur"
            className="h-auto w-full"
          />
        </div>
      ))}

      <span className="absolute bottom-[26%] left-1/2 z-20 -translate-x-1/2 -rotate-3 rounded-pill shadow-ink/20 shadow-lg">
        <Badge tone="yellow">
          <MapPin className="size-3.5" />
          {t("place")}
        </Badge>
      </span>
    </div>
  )
}
