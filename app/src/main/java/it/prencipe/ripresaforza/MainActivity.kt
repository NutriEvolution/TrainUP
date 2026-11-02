package it.prencipe.ripresaforza

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import org.json.JSONObject

class MainActivity : ComponentActivity() {
  companion object {
    @Volatile var isForeground = false
  }

  lateinit var web: WebView
  private var pendingSave: String? = null

  private val createDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
    val content = pendingSave; pendingSave = null
    if (uri != null && content != null) {
      try {
        contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
        js("window.onFileSaved && window.onFileSaved(true)")
      } catch (e: Exception) { js("window.onFileSaved && window.onFileSaved(false)") }
    } else js("window.onFileSaved && window.onFileSaved(false)")
  }

  private val openDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) {
      try {
        val text = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
        js("window.onFileOpened && window.onFileOpened(${JSONObject.quote(text)})")
      } catch (e: Exception) { js("window.onFileOpened && window.onFileOpened(null)") }
    }
  }

  private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

  private val pickDir = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    if (uri != null) {
      try {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        Backup.setDir(this, uri)
        Mirror.forget(this)
        Thread { val n = Backup.dirName(this); js("window.onBackupDir && window.onBackupDir(${JSONObject.quote(n)})"); mirror() }.start()
      } catch (e: Exception) { js("window.onBackupDir && window.onBackupDir(null)") }
    } else js("window.onBackupDir && window.onBackupDir(null)")
  }

  // ---- foto e video personali degli esercizi ----
  private var mediaEx: String? = null
  private var pendingCapture: File? = null
  private fun mediaChanged(ex: String) { js("window.onMediaChanged && window.onMediaChanged(${JSONObject.quote(ex)})"); Thread { mirror() }.start() }

  // copia di foto, video e figure nella cartella del backup (chiamata fuori dal thread principale)
  fun mirror() {
    if (Backup.dir(this) == null) return
    val before = File(filesDir, Mirror.PACK).exists()
    val r = Mirror.sync(this)
    if (!before && File(filesDir, Mirror.PACK).exists()) js("window.onFigureImported && window.onFigureImported(-2)")
    js("window.onMirror && window.onMirror(${JSONObject.quote(r)})")
  }

  // ---- figure dei manuali (pacchetto .zip) ----
  private val pickFigure = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri == null) { js("window.onFigureImported && window.onFigureImported(null)"); return@registerForActivityResult }
    Thread {
      val pack = File(filesDir, Mirror.PACK); val tmp = File(filesDir, Mirror.PACK + ".new")
      val n = try {
        contentResolver.openInputStream(uri)?.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
        val k = tmp.inputStream().use { Figure.importZip(this, it) }
        if (k >= 0) { pack.delete(); tmp.renameTo(pack) } else tmp.delete()
        k
      } catch (e: Exception) { tmp.delete(); -1 }
      js("window.onFigureImported && window.onFigureImported($n)")
      if (n >= 0) mirror()
    }.start()
  }
  private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    val ex = mediaEx ?: return@registerForActivityResult
    if (uri != null) Thread { Media.importUri(this, ex, uri); mediaChanged(ex) }.start()
  }
  private fun captured(ok: Boolean) {
    val f = pendingCapture; pendingCapture = null
    val ex = mediaEx ?: return
    if (!ok || f == null || f.length() == 0L) f?.delete()
    mediaChanged(ex)
  }
  private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok -> captured(ok) }
  private val takeVideo = registerForActivityResult(ActivityResultContracts.CaptureVideo()) { ok -> captured(ok) }
  private fun uriFor(f: File): Uri = FileProvider.getUriForFile(this, "$packageName.files", f)

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    web = WebView(this)
    setContentView(web)
    web.settings.apply {
      javaScriptEnabled = true
      domStorageEnabled = true
      mediaPlaybackRequiresUserGesture = false
      allowFileAccess = true
    }
    web.webViewClient = object : WebViewClient() {
      override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val u = request.url
        if (u.scheme == "file") return false
        openExternal(u.toString()); return true
      }
    }
    web.webChromeClient = android.webkit.WebChromeClient() // abilita confirm()/alert()
    web.addJavascriptInterface(Bridge(), "Android")
    web.loadUrl("file:///android_asset/www/index.html")

    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        web.evaluateJavascript("(window.androidBack && window.androidBack()) ? 1 : 0") { r ->
          if (r != "1") { isEnabled = false; onBackPressedDispatcher.onBackPressed(); isEnabled = true }
        }
      }
    })

    if (Build.VERSION.SDK_INT >= 33 &&
      checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  override fun onResume() { super.onResume(); isForeground = true; js("window.onAndroidResume && window.onAndroidResume()") }
  override fun onPause() { super.onPause(); isForeground = false }

  fun js(code: String) = runOnUiThread { web.evaluateJavascript(code, null) }

  fun openExternal(url: String) {
    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
  }

  private fun alarmIntent(title: String = "", text: String = ""): PendingIntent {
    val i = Intent(this, AlarmReceiver::class.java).putExtra("title", title).putExtra("text", text)
    return PendingIntent.getBroadcast(this, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  }

  inner class Bridge {
    @JavascriptInterface fun version(): String = BuildConfig.VERSION_NAME

    @JavascriptInterface fun keepScreenOn(on: Boolean) = runOnUiThread {
      if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    @JavascriptInterface fun scheduleAlarm(atMs: String, title: String, text: String) {
      val at = atMs.toDoubleOrNull()?.toLong() ?: return
      val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
      val pi = alarmIntent(title, text)
      try {
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
      } catch (_: SecurityException) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
    }

    @JavascriptInterface fun cancelAlarm() {
      (getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(alarmIntent())
    }

    @JavascriptInterface fun saveFile(name: String, content: String) = runOnUiThread {
      pendingSave = content; createDoc.launch(name)
    }

    @JavascriptInterface fun openFile() = runOnUiThread {
      openDoc.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
    }

    @JavascriptInterface fun chooseBackupDir() = runOnUiThread { try { pickDir.launch(null) } catch (e: Exception) { js("window.onBackupDir && window.onBackupDir(null)") } }

    @JavascriptInterface fun backupDirName(): String = Backup.dirName(this@MainActivity)

    @JavascriptInterface fun clearBackupDir() {
      Backup.dir(this@MainActivity)?.let { try { contentResolver.releasePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (_: Exception) {} }
      Backup.setDir(this@MainActivity, null)
      Mirror.forget(this@MainActivity)
    }

    @JavascriptInterface fun figureList(): String = Figure.list(this@MainActivity)

    @JavascriptInterface fun figureImport() = runOnUiThread {
      try { pickFigure.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }
      catch (e: Exception) { js("window.onFigureImported && window.onFigureImported(-1)") }
    }

    @JavascriptInterface fun figureClear() { Figure.clear(this@MainActivity); File(filesDir, Mirror.PACK).delete(); Thread { mirror() }.start() }

    @JavascriptInterface fun mirrorNow() { Thread { mirror() }.start() }

    @JavascriptInterface fun autoBackup(day: String, content: String, keep: Int) {
      Thread { val r = Backup.write(this@MainActivity, day, content, keep); js("window.onAutoBackup && window.onAutoBackup(${JSONObject.quote(r)})") }.start()
    }

    @JavascriptInterface fun checkUpdate() { Updater.check(this@MainActivity) }

    @JavascriptInterface fun installUpdate(url: String) { Updater.download(this@MainActivity, url) }

    @JavascriptInterface fun openExternal(url: String) = runOnUiThread { this@MainActivity.openExternal(url) }

    @JavascriptInterface fun mediaList(ex: String): String = Media.list(this@MainActivity, ex)

    @JavascriptInterface fun mediaDelete(ex: String, name: String): Boolean {
      val ok = Media.delete(this@MainActivity, ex, name); if (ok) Thread { mirror() }.start(); return ok
    }

    @JavascriptInterface fun mediaAdd(ex: String, mode: String) = runOnUiThread {
      mediaEx = ex
      try {
        when (mode) {
          "photo" -> { val f = Media.newFile(this@MainActivity, ex, "jpg"); pendingCapture = f; takePhoto.launch(uriFor(f)) }
          "video" -> { val f = Media.newFile(this@MainActivity, ex, "mp4"); pendingCapture = f; takeVideo.launch(uriFor(f)) }
          else -> pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        }
      } catch (e: Exception) { pendingCapture?.delete(); pendingCapture = null; js("window.onMediaError && window.onMediaError()") }
    }
  }
}
