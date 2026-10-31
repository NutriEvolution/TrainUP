package it.prencipe.ripresaforza

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class AlarmReceiver : BroadcastReceiver() {
  override fun onReceive(ctx: Context, intent: Intent) {
    if (MainActivity.isForeground) return // l'app è aperta: suona già il timer interno
    val nm = ctx.getSystemService(NotificationManager::class.java)
    val ch = "rest"
    if (nm.getNotificationChannel(ch) == null) {
      nm.createNotificationChannel(NotificationChannel(ch, "Fine recupero", NotificationManager.IMPORTANCE_HIGH).apply {
        enableVibration(true); vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 400)
      })
    }
    val open = PendingIntent.getActivity(ctx, 0,
      Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
      PendingIntent.FLAG_IMMUTABLE)
    val n = NotificationCompat.Builder(ctx, ch)
      .setSmallIcon(R.drawable.ic_notif)
      .setContentTitle(intent.getStringExtra("title")?.ifBlank { null } ?: "Recupero finito")
      .setContentText(intent.getStringExtra("text")?.ifBlank { null } ?: "Tocca per la prossima serie")
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_ALARM)
      .setDefaults(NotificationCompat.DEFAULT_ALL)
      .setAutoCancel(true)
      .setContentIntent(open)
      .build()
    try { NotificationManagerCompat.from(ctx).notify(7, n) } catch (_: SecurityException) {}
  }
}
