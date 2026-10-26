package com.farmcare.app.util

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.math.BigDecimal

const val QR_PREFIX = "farmcare://a/"   // QR carries only an opaque token, never customer data

fun qrBitmap(text: String, size: Int = 768): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf<EncodeHintType, Any>(EncodeHintType.MARGIN to 1))
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { b ->
        for (x in 0 until size) for (y in 0 until size) b.setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE) }
}

/** Printable tag: farm name, animal ID, species and QR. */
fun tagBitmap(farm: String, code: String, species: String, token: String): Bitmap {
    val w = 800; val h = 1000
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); val c = Canvas(bmp); c.drawColor(Color.WHITE)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Color.parseColor("#1B4D3E") }
    p.textSize = 48f; p.typeface = Typeface.DEFAULT_BOLD; c.drawText(farm, w / 2f, 90f, p)
    c.drawBitmap(qrBitmap(QR_PREFIX + token, 640), 80f, 140f, null)
    p.color = Color.BLACK; p.textSize = 110f; c.drawText(code, w / 2f, 880f, p)
    p.typeface = Typeface.DEFAULT; p.textSize = 48f; c.drawText(species.replaceFirstChar { it.uppercase() }, w / 2f, 950f, p)
    val border = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = Color.parseColor("#1B4D3E") }
    c.drawRect(3f, 3f, w - 3f, h - 3f, border)
    return bmp
}

private fun exportsDir(ctx: Context) = File(ctx.cacheDir, "exports").apply { mkdirs() }

fun writePng(ctx: Context, name: String, bmp: Bitmap): File = File(exportsDir(ctx), "$name.png").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }

fun writeCsv(ctx: Context, name: String, rows: List<List<String>>): File {
    fun esc(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    return File(exportsDir(ctx), "$name.csv").also { it.writeText(rows.joinToString("\n") { r -> r.joinToString(",") { c -> esc(c) } }) }
}

/** Simple print-friendly PDF (A4, monospace, paginated). */
fun writePdf(ctx: Context, name: String, title: String, lines: List<String>): File {
    val doc = PdfDocument(); val paint = Paint().apply { textSize = 9f; typeface = Typeface.MONOSPACE }
    val head = Paint().apply { textSize = 15f; typeface = Typeface.DEFAULT_BOLD; color = Color.parseColor("#1B4D3E") }
    val perPage = 52; val pages = lines.chunked(perPage).ifEmpty { listOf(emptyList()) }
    pages.forEachIndexed { i, chunk ->
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, i + 1).create())
        page.canvas.drawText(title, 36f, 44f, head)
        chunk.forEachIndexed { r, l -> page.canvas.drawText(l.take(105), 36f, 74f + r * 14f, paint) }
        page.canvas.drawText("Page ${i + 1} of ${pages.size}", 480f, 820f, paint)
        doc.finishPage(page)
    }
    return File(exportsDir(ctx), "$name.pdf").also { f -> f.outputStream().use { doc.writeTo(it) }; doc.close() }
}

fun shareFile(ctx: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = mime; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Share"))
}

fun shareText(ctx: Context, text: String) {
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Share"))
}

fun money(v: BigDecimal, symbol: String = "\u20B9") = symbol + "%,.2f".format(v)
fun money(v: Double, symbol: String = "\u20B9") = symbol + "%,.2f".format(v)
