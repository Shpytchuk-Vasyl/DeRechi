import { initBotId } from "botid/client/core"

initBotId({
  protect: [{ path: "/*/report/*", method: "POST" }],
})
