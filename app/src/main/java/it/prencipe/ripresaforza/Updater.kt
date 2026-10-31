package it.prencipe.ripresaforza

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object Updater {
  const val REPO = "NutriEvolution/TrainUP"

  private fun newer(remote: String, local: String): Boolean {
    val r = remote.trimStart('v', 'V').split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    val l = local.split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(r.size, l.size)) {
      val a = r.getOrElse(i) { 0 }; val b = l.getOrElse(i) { 0 }
      if (a != b) return a > b
    }
    return false
  }

  fun check(act: MainActivity) = Thread {
    val out = JSONObject()
    try {
      val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
      c.setRequestProperty("Accept", "application/vnd.github+json")
      c.connectTimeout = 10000; c.readTimeout = 15000
      if (c.responseCode != 200) throw Exception("HTTP ${c.responseCode}")
      val j = JSONObject(c.inputStream.bufferedReader().readText())
      val tag = j.optString("tag_name")
      val assets = j.optJSONArray("assets")
      var url = ""
      if (assets != null) for (i in 0 until assets.length()) {
        val a = assets.getJSONObject(i)
        if (a.optString("name").endsWith(".apk")) { url = a.optString("browser_download_url"); break }
      }
      out.put("available", url.isNotEmpty() && newer(tag, BuildConfig.VERSION_NAME))
      out.put("version", tag.trimStart('v'))
      out.put("notes", j.optString("body"))
      out.put("url", url)
      out.put("current", BuildConfig.VERSION_NAME)
    } catch (e: Exception) {
      out.put("available", false); out.put("error", e.message ?: "errore"); out.put("current", BuildConfig.VERSION_NAME)
    }
    act.js("window.onUpdateInfo && window.onUpdateInfo($out)")
  }.start()

  fun download(act: MainActivity, url: String) = Thread {
    try {
      if (Build.VERSION.SDK_INT >= 26 && !act.packageManager.canRequestPackageInstalls()) {
        act.runOnUiThread {
          act.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${act.packageName}")))
        }
        act.js("window.onUpdateProgress && window.onUpdateProgress(-2)")
        return@Thread
      }
      val dir = File(act.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
      val f = File(dir, "update.apk")
      var u = URL(url); var c: HttpURLConnection
      var hops = 0
      while (true) { // segue i redirect di GitHub
        c = u.openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false; c.connectTimeout = 15000; c.readTimeout = 30000
        val code = c.responseCode
        if (code in 300..399 && hops++ < 5) { u = URL(u, c.getHeaderField("Location")); continue }
        if (code != 200) throw Exception("HTTP $code")
        break
      }
      val total = c.contentLengthLong
      var done = 0L; var last = -1
      c.inputStream.use { inp -> f.outputStream().use { o ->
        val buf = ByteArray(64 * 1024)
        while (true) {
          val n = inp.read(buf); if (n < 0) break
          o.write(buf, 0, n); done += n
          if (total > 0) { val p = (done * 100 / total).toInt(); if (p != last) { last = p; act.js("window.onUpdateProgress && window.onUpdateProgress($p)") } }
        }
      } }
      val uri = FileProvider.getUriForFile(act, "${act.packageName}.files", f)
      act.runOnUiThread {
        act.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
          .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
      }
      act.js("window.onUpdateProgress && window.onUpdateProgress(100)")
    } catch (e: Exception) {
      act.js("window.onUpdateProgress && window.onUpdateProgress(-1)")
    }
  }.start()
}
