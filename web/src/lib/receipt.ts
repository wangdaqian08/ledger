/**
 * Shrinks a receipt photo before it travels. A phone camera's 12 MP original is megabytes the
 * server would only cap; re-encoded at 1600px it is a few hundred kilobytes that still reads
 * fine zoomed in — which keeps uploads quick, the bucket inside its free tier, and the bytes
 * served back small. Re-encoding through a canvas also drops EXIF wholesale: where somebody
 * stood when they photographed a bill is nobody's business.
 *
 * When the browser cannot decode the file at all (an exotic format, a corrupt file), the
 * original is sent as-is — the server still enforces type and size, so the fallback can never
 * smuggle anything past the rules, only skip the shrink.
 */

const MAX_EDGE = 1600
const JPEG_QUALITY = 0.8

/** The largest-edge fit, in integer pixels, never below 1 on either side. */
export function fitWithin(width: number, height: number, maxEdge: number): { width: number; height: number } {
  const longest = Math.max(width, height)
  if (longest <= maxEdge) return { width, height }
  const scale = maxEdge / longest
  return {
    width: Math.max(1, Math.round(width * scale)),
    height: Math.max(1, Math.round(height * scale)),
  }
}

export async function prepareReceipt(file: File): Promise<{ image: Blob; filename: string }> {
  try {
    const bitmap = await createImageBitmap(file)
    const { width, height } = fitWithin(bitmap.width, bitmap.height, MAX_EDGE)
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    const context = canvas.getContext('2d')
    if (!context) throw new Error('no 2d context')
    context.drawImage(bitmap, 0, 0, width, height)
    bitmap.close()
    const encoded = await new Promise<Blob | null>((resolve) =>
      canvas.toBlob(resolve, 'image/jpeg', JPEG_QUALITY),
    )
    if (!encoded) throw new Error('encoding failed')
    return { image: encoded, filename: 'receipt.jpg' }
  } catch {
    return { image: file, filename: file.name || 'receipt' }
  }
}
