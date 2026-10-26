import { type LucideIcon, PackageCheck, PackageSearch } from "lucide-react"
import type { Tone } from "@/components/pouf/tone"
import type { ItemKind } from "@/lib/kinds"

type KindMeta = {
  other: ItemKind
  tone: Extract<Tone, "up" | "down">
  tint: Extract<Tone, "pink" | "mint">
  Icon: LucideIcon
  nav: { list: string; report: string }
  home: { recent: string; seeAll: string; recentError: string }
  list: {
    title: string
    titleIn: string
    description: string
    metaDescription: string
    metaDescriptionIn: string
    empty: string
    emptyHint: string
  }
  cityHint: { textCity: string; textNear: string; go: string }
  item: {
    list: string
    date: string
    contactTitle: string
    claimQuestion: string
    claimText: string
    shareText: string
    publishedText: string
  }
  claim: { send: string; done: string }
  form: {
    pageTitle: string
    metaDescription: string
    title: string
    descriptionHint: string
    date: string
    photo: string
    placeHint: string
    tips: readonly string[]
  }
}

export const KIND_META: Record<ItemKind, KindMeta> = {
  lost: {
    other: "found",
    tone: "down",
    tint: "pink",
    Icon: PackageSearch,
    nav: { list: "lost", report: "reportLost" },
    home: { recent: "recentLost", seeAll: "seeAllLost", recentError: "recentLostError" },
    list: {
      title: "lostTitle",
      titleIn: "lostTitleIn",
      description: "lostDescription",
      metaDescription: "lostMetaDescription",
      metaDescriptionIn: "lostMetaDescriptionIn",
      empty: "emptyLost",
      emptyHint: "emptyLostHint",
    },
    cityHint: { textCity: "textCityLost", textNear: "textNearLost", go: "goLost" },
    item: {
      list: "lost",
      date: "lostOn",
      contactTitle: "contactTitle",
      claimQuestion: "didYouFind",
      claimText: "didYouFindText",
      shareText: "shareText.lost",
      publishedText: "publishedText.lost",
    },
    claim: { send: "sendFound", done: "doneOwner" },
    form: {
      pageTitle: "pageTitleLost",
      metaDescription: "metaDescriptionLost",
      title: "titleLost",
      descriptionHint: "descriptionHintLost",
      date: "dateLost",
      photo: "photo",
      placeHint: "placeHintLost",
      tips: ["lostTip1", "lostTip2", "lostTip3"],
    },
  },
  found: {
    other: "lost",
    tone: "up",
    tint: "mint",
    Icon: PackageCheck,
    nav: { list: "found", report: "reportFound" },
    home: { recent: "recentFound", seeAll: "seeAllFound", recentError: "recentFoundError" },
    list: {
      title: "foundTitle",
      titleIn: "foundTitleIn",
      description: "foundDescription",
      metaDescription: "foundMetaDescription",
      metaDescriptionIn: "foundMetaDescriptionIn",
      empty: "emptyFound",
      emptyHint: "emptyFoundHint",
    },
    cityHint: { textCity: "textCityFound", textNear: "textNearFound", go: "goFound" },
    item: {
      list: "found",
      date: "foundOn",
      contactTitle: "contactFinderTitle",
      claimQuestion: "isItYours",
      claimText: "isItYoursText",
      shareText: "shareText.found",
      publishedText: "publishedText.found",
    },
    claim: { send: "sendMine", done: "doneFinder" },
    form: {
      pageTitle: "pageTitleFound",
      metaDescription: "metaDescriptionFound",
      title: "titleFound",
      descriptionHint: "descriptionHintFound",
      date: "dateFound",
      photo: "photoRequired",
      placeHint: "placeHintFound",
      tips: ["foundTip1", "foundTip2", "foundTip3"],
    },
  },
}
