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
import androidx.activity.result.contract.ActivityResultContracts
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

    @JavascriptInterface fun checkUpdate() { Updater.check(this@MainActivity) }

    @JavascriptInterface fun installUpdate(url: String) { Updater.download(this@MainActivity, url) }

    @JavascriptInterface fun openExternal(url: String) = runOnUiThread { this@MainActivity.openExternal(url) }
  }
}
