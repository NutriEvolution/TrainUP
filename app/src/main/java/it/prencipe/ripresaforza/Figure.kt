package it.prencipe.ripresaforza

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Figure degli esercizi tratte dai manuali dell'utente: arrivano in un pacchetto .zip importato a mano
 * (manifest.json + immagini) e restano solo nella memoria interna dell'app. Non fanno parte dell'APK.
 */
object Figure {
  private const val MAX_FILE = 4L * 1024 * 1024
  private const val MAX_TOTAL = 150L * 1024 * 1024
  private val NAME = Regex("^[A-Za-z0-9_]+/[A-Za-z0-9_.-]+\\.(jpg|jpeg|png)$")

  fun dir(ctx: Context): File = File(ctx.filesDir, "figure")

  private fun isImage(b: ByteArray): Boolean =
    (b.size > 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte()) ||
    (b.size > 8 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte() && b[3] == 'G'.code.toByte())

  private fun readCapped(inp: InputStream, max: Long): ByteArray? {
    val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(16384); var tot = 0L
    while (true) { val n = inp.read(buf); if (n < 0) break; tot += n; if (tot > max) return null; out.write(buf, 0, n) }
    return out.toByteArray()
  }

  /** Importa il pacchetto; restituisce il numero di esercizi con figure, oppure -1 se il file non è valido. */
  fun importZip(ctx: Context, input: InputStream): Int {
    val tmp = File(ctx.filesDir, "figure.tmp"); tmp.deleteRecursively(); tmp.mkdirs()
    var manifest: JSONObject? = null; var total = 0L
    try {
      ZipInputStream(input).use { z ->
        while (true) {
          val e = z.nextEntry ?: break
          if (e.isDirectory) continue
          val name = e.name
          if (name == "manifest.json") {
            val b = readCapped(z, 1024 * 1024) ?: return fail(tmp)
            manifest = JSONObject(String(b, Charsets.UTF_8)); continue
          }
          if (!NAME.matches(name)) continue
          val b = readCapped(z, MAX_FILE) ?: return fail(tmp)
          total += b.size; if (total > MAX_TOTAL || !isImage(b)) return fail(tmp)
          val f = File(tmp, name); f.parentFile?.mkdirs(); f.writeBytes(b)
        }
      }
      val m = manifest ?: return fail(tmp)
      if (m.optString("app") != "trainup-figure") return fail(tmp)
      File(tmp, "manifest.json").writeText(m.toString(), Charsets.UTF_8)
      val d = dir(ctx); d.deleteRecursively()
      if (!tmp.renameTo(d)) return fail(tmp)
      return m.optJSONObject("ex")?.length() ?: 0
    } catch (e: Exception) { return fail(tmp) }
  }

  private fun fail(tmp: File): Int { tmp.deleteRecursively(); return -1 }

  fun importUri(ctx: Context, uri: Uri): Int =
    try { ctx.contentResolver.openInputStream(uri)?.use { importZip(ctx, it) } ?: -1 } catch (e: Exception) { -1 }

  /** manifest con gli indirizzi dei file, per la pagina. */
  fun list(ctx: Context): String {
    val d = dir(ctx); val mf = File(d, "manifest.json")
    if (!mf.exists()) return "{}"
    return try {
      val m = JSONObject(mf.readText(Charsets.UTF_8))
      m.put("base", "file://" + d.absolutePath + "/")
      m.toString()
    } catch (e: Exception) { "{}" }
  }

  fun clear(ctx: Context) { dir(ctx).deleteRecursively() }
}
