import "server-only"
import { checkBotId } from "botid/server"

export async function passesBotCheck(): Promise<boolean> {
  try {
    const verification = await checkBotId(
      process.env.VERCEL
        ? undefined
        : { developmentOptions: { isDevelopment: true, bypass: "HUMAN" } },
    )
    return !verification.isBot
  } catch (error) {
    console.error("BotID check failed", error)
    return false
  }
}
