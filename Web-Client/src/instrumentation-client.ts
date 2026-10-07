import { initBotId } from "botid/client/core"

// Dev only
if (process.env.VERCEL) {
  initBotId({
    protect: [
      { path: "/*/report/*", method: "POST" },
      { path: "/*/lost/*", method: "POST" },
      { path: "/*/found/*", method: "POST" },
    ],
  })
}
