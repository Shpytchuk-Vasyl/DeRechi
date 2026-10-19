import qrcode from "qrcode-generator"

const QUIET_ZONE = 4

export function qrModules(text: string): boolean[][] {
  const qr = qrcode(0, "M")
  qr.addData(text)
  qr.make()
  const size = qr.getModuleCount()
  return Array.from({ length: size }, (_, row) =>
    Array.from({ length: size }, (_, col) => qr.isDark(row, col)),
  )
}

export function qrSvg(text: string, color = "#000000"): string {
  const modules = qrModules(text)
  const size = modules.length + QUIET_ZONE * 2
  const path = modules
    .flatMap((cells, row) =>
      cells.map((dark, col) => (dark ? `M${col + QUIET_ZONE} ${row + QUIET_ZONE}h1v1h-1z` : "")),
    )
    .join("")
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${size} ${size}" shape-rendering="crispEdges"><rect width="${size}" height="${size}" fill="#ffffff"/><path d="${path}" fill="${color}"/></svg>`
}

export function qrDataUrl(text: string, color?: string): string {
  return `data:image/svg+xml;base64,${Buffer.from(qrSvg(text, color)).toString("base64")}`
}
