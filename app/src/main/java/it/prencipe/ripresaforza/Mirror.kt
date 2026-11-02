package it.prencipe.ripresaforza

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/**
 * Copia di foto, video e figure dei manuali nella sottocartella "TrainUP media" della cartella del backup automatico,
 * così sopravvivono a una disinstallazione. Dopo una reinstallazione, scelta di nuovo la cartella, i file tornano nell'app.
 * Un file cancellato nell'app viene cancellato anche dalla copia (si ricordano i nomi già copiati).
 */
object Mirror {
  const val FOLDER = "TrainUP media"
  const val PACK = "figure-manuali.zip"
  private const val PREFS = "mirror"
  private const val SEP = "__"

  private data class Doc(val name: String, val uri: Uri, val dir: Boolean, val size: Long)

  private fun docUri(tree: Uri, id: String) = DocumentsContract.buildDocumentUriUsingTree(tree, id)

  private fun kids(ctx: Context, tree: Uri, parent: Uri): List<Doc> {
    val out = mutableListOf<Doc>()
    val q = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
    ctx.contentResolver.query(q, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
      DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { c ->
      while (c.moveToNext()) out.add(Doc(c.getString(1), docUri(tree, c.getString(0)), c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
        if (c.isNull(3)) -1L else c.getLong(3)))
    }
    return out
  }

  private fun folder(ctx: Context, tree: Uri): Uri? {
    val root = docUri(tree, DocumentsContract.getTreeDocumentId(tree))
    kids(ctx, tree, root).firstOrNull { it.dir && it.name == FOLDER }?.let { return it.uri }
    return DocumentsContract.createDocument(ctx.contentResolver, root, DocumentsContract.Document.MIME_TYPE_DIR, FOLDER)
  }

  private fun copyOut(ctx: Context, parent: Uri, name: String, src: File, mime: String): Boolean {
    val u = DocumentsContract.createDocument(ctx.contentResolver, parent, mime, name) ?: return false
    ctx.contentResolver.openOutputStream(u, "w")?.use { o -> src.inputStream().use { it.copyTo(o) } } ?: return false
    return true
  }

  private fun mime(f: String) = when (f.substringAfterLast('.').lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "mp4" -> "video/mp4"; "webm" -> "video/webm"
    "3gp" -> "video/3gpp"; "mkv" -> "video/x-matroska"; "mov" -> "video/quicktime"; "zip" -> "application/zip"
    else -> "application/octet-stream"
  }

  /** Allinea app e cartella. Restituisce "" se va bene, altrimenti il motivo. */
  @Synchronized fun sync(ctx: Context): String {
    val tree = Backup.dir(ctx) ?: return ""
    return try {
      val dir = folder(ctx, tree) ?: return "impossibile creare la cartella"
      val remote = kids(ctx, tree, dir).filter { !it.dir }.associateBy { it.name }
      val prefs = ctx.getSharedPreferences(PREFS, 0)
      val copied = prefs.getStringSet("names", emptySet())!!.toMutableSet()
      val mediaRoot = File(ctx.filesDir, "media")
      val local = mutableMapOf<String, File>()
      mediaRoot.listFiles()?.filter { it.isDirectory }?.forEach { d ->
        d.listFiles()?.filter { it.isFile && it.length() > 0 }?.forEach { f -> local[d.name + SEP + f.name] = f }
      }
      val pack = File(ctx.filesDir, PACK)
      if (pack.exists() && pack.length() > 0) local[PACK] = pack

      // 1) nella cartella ma non nell'app: se non l'avevamo mai copiato è un ritorno dopo una reinstallazione → si riporta nell'app
      for ((name, doc) in remote) {
        if (local.containsKey(name)) continue
        if (copied.contains(name)) { try { DocumentsContract.deleteDocument(ctx.contentResolver, doc.uri) } catch (_: Exception) {}; copied.remove(name); continue }
        if (name == PACK) {
          ctx.contentResolver.openInputStream(doc.uri)?.use { inp -> pack.outputStream().use { inp.copyTo(it) } }
          if (pack.inputStream().use { Figure.importZip(ctx, it) } < 0) pack.delete() else { local[PACK] = pack; copied.add(name) }
          continue
        }
        val i = name.indexOf(SEP); if (i <= 0) continue
        val ex = name.substring(0, i).replace(Regex("[^A-Za-z0-9_]"), "_"); val fn = name.substring(i + SEP.length)
        if (fn.isEmpty() || fn.contains('/') || fn.startsWith(".")) continue
        val f = File(Media.dir(ctx, ex), fn)
        ctx.contentResolver.openInputStream(doc.uri)?.use { inp -> f.outputStream().use { inp.copyTo(it) } }
        if (f.length() > 0) { local[name] = f; copied.add(name) } else f.delete()
      }
      // 2) nell'app ma non nella cartella → si copia
      for ((name, f) in local) {
        val r = remote[name]
        if (r != null) {
          if (r.size >= 0 && r.size != f.length()) ctx.contentResolver.openOutputStream(r.uri, "wt")?.use { o -> f.inputStream().use { it.copyTo(o) } }
          copied.add(name); continue
        }
        if (copyOut(ctx, dir, name, f, mime(name))) copied.add(name)
      }
      // 3) nomi copiati che non esistono più da nessuna parte
      copied.retainAll(local.keys)
      prefs.edit().putStringSet("names", copied).apply()
      ""
    } catch (e: SecurityException) { "permesso sulla cartella perso: sceglila di nuovo" }
    catch (e: Exception) { e.message ?: "errore" }
  }

  fun forget(ctx: Context) { ctx.getSharedPreferences(PREFS, 0).edit().clear().apply() }
}
