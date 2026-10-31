package it.prencipe.ripresaforza

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Foto e video personali degli esercizi, salvati solo nella memoria interna dell'app. */
object Media {
  private val VIDEO = setOf("mp4", "3gp", "webm", "mkv", "mov")
  private fun safe(ex: String) = ex.replace(Regex("[^A-Za-z0-9_]"), "_")

  fun dir(ctx: Context, ex: String): File = File(ctx.filesDir, "media/" + safe(ex)).apply { mkdirs() }

  fun newFile(ctx: Context, ex: String, ext: String): File = File(dir(ctx, ex), "${System.currentTimeMillis()}.$ext")

  fun importUri(ctx: Context, ex: String, uri: Uri): Boolean = try {
    val mime = ctx.contentResolver.getType(uri) ?: ""
    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: if (mime.startsWith("video")) "mp4" else "jpg"
    val out = newFile(ctx, ex, ext)
    ctx.contentResolver.openInputStream(uri)?.use { inp -> out.outputStream().use { inp.copyTo(it) } }
    out.length() > 0
  } catch (e: Exception) { false }

  fun list(ctx: Context, ex: String): String {
    val arr = JSONArray()
    dir(ctx, ex).listFiles()?.filter { it.isFile && it.length() > 0 }?.sortedBy { it.name }?.forEach { f ->
      arr.put(JSONObject().put("name", f.name).put("url", "file://" + f.absolutePath).put("video", f.extension.lowercase() in VIDEO))
    }
    return arr.toString()
  }

  fun delete(ctx: Context, ex: String, name: String): Boolean {
    val d = dir(ctx, ex); val f = File(d, name)
    return f.parentFile?.canonicalPath == d.canonicalPath && f.delete()
  }
}
