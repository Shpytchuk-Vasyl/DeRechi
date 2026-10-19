import { initBotId } from "botid/client/core"

initBotId({
  protect: [
    { path: "/*/report/*", method: "POST" },
    { path: "/*/lost/*", method: "POST" },
    { path: "/*/found/*", method: "POST" },
  ],
})
