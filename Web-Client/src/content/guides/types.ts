export type GuideSlug = "documents" | "keys" | "wallet" | "electronics" | "animals"

export type GuideCallout = { tone: string; text: string }

export type GuideBlock = string | string[] | GuideCallout

export type GuideStep = { title: string; text: string }

export type GuideSection = {
  id: string
  heading: string
  body?: GuideBlock[]
  steps?: GuideStep[]
}

export type GuideFaqItem = { q: string; a: string }

export type GuideDoc = {
  title: string
  shortTitle: string
  description: string
  lead: string
  linkLost: string
  linkFound: string
  ctaFound: string
  railTitle: string
  railEmpty: string
  sections: GuideSection[]
  faq: GuideFaqItem[]
}

export type GuideTexts = {
  eyebrow: string
  contents: string
  readingTime: string
  checked: string
  checkNowTitle: string
  checkNowText: string
  ctaLost: string
  faqTitle: string
  relatedTitle: string
  cityNote: string
  cityLink: string
  index: { title: string; description: string; lead: string; read: string }
  guides: Record<GuideSlug, GuideDoc>
}

export type GuideMeta = {
  category: string
  published: string
  checked: string
  related: GuideSlug[]
}
