import type { Locator } from "@playwright/test"
import { ItemDetail } from "./item-detail"

export class ItemPage extends ItemDetail {
  photo(title: string): Locator {
    return this.root().getByRole("img", {
      name: this.t("item.photoOf", { title }),
      exact: true,
    })
  }

  noPhoto(): Locator {
    return this.root().getByText(this.t("item.noPhoto"), { exact: true })
  }

  photoDialog(title: string): Locator {
    return this.page.getByRole("dialog", {
      name: this.t("item.photoOf", { title }),
      exact: true,
    })
  }

  closePhotoButton(title: string): Locator {
    return this.photoDialog(title).getByRole("button", {
      name: this.t("item.closePhoto"),
      exact: true,
    })
  }

  contact(which: "phone" | "email"): Locator {
    return this.definition(this.t(which === "phone" ? "item.phone" : "item.email"))
  }
}
