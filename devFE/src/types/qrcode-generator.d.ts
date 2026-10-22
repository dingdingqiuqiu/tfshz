declare module '*/vendor/qrcode-generator.js' {
  interface QrCodeOptions {
    cellSize?: number
    margin?: number
    scalable?: boolean
    alt?: string
    title?: string
  }
  interface QrCode {
    addData(data: string, mode?: string): void
    make(): void
    getModuleCount(): number
    createSvgTag(options?: QrCodeOptions): string
    createDataURL(cellSize?: number, margin?: number): string
  }
  function qrcode(typeNumber: number, errorCorrectionLevel: 'L' | 'M' | 'Q' | 'H'): QrCode
  export default qrcode
}
