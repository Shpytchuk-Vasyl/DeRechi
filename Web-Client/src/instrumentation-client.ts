import { initBotId } from "botid/client/core"

// Dev only
// if (window.isSecureContext) {
  initBotId({
    protect: [
      { path: "/*/report/*", method: "POST" },
      { path: "/*/lost/*", method: "POST" },
      { path: "/*/found/*", method: "POST" },
    ],
  })
// }
