import { initBotId } from "botid/client/core"

// Dev only: set to false
if (true) {
  initBotId({
    protect: [
      { path: "/*/report/*", method: "POST" },
      { path: "/*/lost/*", method: "POST" },
      { path: "/*/found/*", method: "POST" },
    ],
  })
}
