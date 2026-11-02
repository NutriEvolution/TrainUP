package it.prencipe.ripresaforza

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

// Backup automatico in una cartella del telefono scelta dall'utente (Storage Access Framework, permesso persistente).
// Un file al giorno (trainup-auto-AAAA-MM-GG.json, sovrascritto se cambia qualcosa nello stesso giorno); si tengono gli ultimi `keep`.
object Backup {
  private const val PREFS = "backup"
  private const val KEY = "dir"
  private const val PREFIX = "trainup-auto-"

  fun dir(ctx: Context): Uri? = ctx.getSharedPreferences(PREFS, 0).getString(KEY, null)?.let { Uri.parse(it) }

  fun setDir(ctx: Context, uri: Uri?) {
    val p = ctx.getSharedPreferences(PREFS, 0).edit()
    if (uri == null) p.remove(KEY) else p.putString(KEY, uri.toString())
    p.apply()
  }

  fun dirName(ctx: Context): String {
    val tree = dir(ctx) ?: return ""
    return try {
      val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
      ctx.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else ""
      } ?: ""
    } catch (e: Exception) { "" }
  }

  private fun children(ctx: Context, tree: Uri): List<Pair<String, Uri>> {
    val out = mutableListOf<Pair<String, Uri>>()
    val kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    ctx.contentResolver.query(kids, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
      while (c.moveToNext()) out.add(c.getString(1) to DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)))
    }
    return out
  }

  /** Scrive il backup del giorno; restituisce "" se va bene, altrimenti il motivo. */
  fun write(ctx: Context, day: String, content: String, keep: Int): String {
    val tree = dir(ctx) ?: return "nessuna cartella"
    return try {
      val name = "$PREFIX$day.json"
      val files = children(ctx, tree)
      val existing = files.firstOrNull { it.first == name }?.second
      val target = existing ?: DocumentsContract.createDocument(ctx.contentResolver,
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)), "application/json", name)
        ?: return "impossibile creare il file"
      ctx.contentResolver.openOutputStream(target, "wt")?.use { it.write(content.toByteArray(Charsets.UTF_8)) } ?: return "impossibile scrivere"
      // tiene solo gli ultimi `keep` backup automatici
      val autos = children(ctx, tree).filter { it.first.startsWith(PREFIX) && it.first.endsWith(".json") }.sortedByDescending { it.first }
      autos.drop(keep).forEach { try { DocumentsContract.deleteDocument(ctx.contentResolver, it.second) } catch (_: Exception) {} }
      ""
    } catch (e: SecurityException) { "permesso sulla cartella perso: sceglila di nuovo" }
    catch (e: Exception) { e.message ?: "errore" }
  }
}
