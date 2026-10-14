package com.footpredict.local.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.footpredict.local.MainActivity
import com.footpredict.local.R
import com.footpredict.local.domain.model.BettingAlert
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notifications système pour les alertes HIGH uniquement.
 * Canal créé à l’injection ; envoi plafonné par [HighAlertPolicy].
 * Sans permission (Android 13+) : silence, jamais de crash.
 */
@Singleton
class AlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val CHANNEL_ID = "high_alerts"
        private const val PREFS = "alert_notifs"
        private const val KEY_DAY = "day"
        private const val KEY_COUNT = "count"
        private const val KEY_IDS = "ids"
    }

    private val manager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.notif_channel_desc) }
        manager.createNotificationChannel(channel)
    }

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Envoie les HIGH du jour (max 2), idempotent par alerte. */
    fun notifyHigh(alerts: List<BettingAlert>) {
        if (!hasPermission()) return
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val today = todayKey()
            val sentToday = if (prefs.getString(KEY_DAY, "") == today) {
                prefs.getInt(KEY_COUNT, 0)
            } else 0
            val sentIds = prefs.getStringSet(KEY_IDS, emptySet()) ?: emptySet()

            val decision = HighAlertPolicy.select(alerts, sentIds, sentToday)
            if (decision.toSend.isEmpty()) return

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            decision.toSend.forEachIndexed { index, alert ->
                val pending = PendingIntent.getActivity(
                    context, alert.id.hashCode(), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notification = android.app.Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(alert.title)
                    .setContentText(alert.message)
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .build()
                manager.notify(alert.id.hashCode(), notification)
            }

            prefs.edit()
                .putString(KEY_DAY, today)
                .putInt(KEY_COUNT, sentToday + decision.toSend.size)
                .putStringSet(KEY_IDS, decision.newSentIds.toList().takeLast(50).toSet())
                .apply()
        } catch (_: Exception) {
            // Notification best-effort : jamais de crash prod.
        }
    }

    private fun todayKey(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
}
