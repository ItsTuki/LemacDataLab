package com.losk.lemacdatalab.notifications

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.losk.lemacdatalab.MainActivity
import com.losk.lemacdatalab.R
import com.losk.lemacdatalab.model.Ambito
import com.losk.lemacdatalab.model.Recordatorio
import java.time.DayOfWeek
import java.time.ZonedDateTime

object ReminderScheduler {
    private fun prefs(context: Context) = context.getSharedPreferences("lemac_preferences", Context.MODE_PRIVATE)
    private fun intent(context: Context) = PendingIntent.getBroadcast(context, 100,
        Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun permitido(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED && NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun leer(context: Context, owner: String): Recordatorio {
        val p = prefs(context)
        return Recordatorio(p.getBoolean("$owner.reminder", false), p.getInt("$owner.hour", 20), p.getInt("$owner.minute", 0),
            p.getString("$owner.frequency", "Diario")!!, p.getString("$owner.scope", null)?.let(Ambito::valueOf))
    }

    fun guardar(context: Context, owner: String, r: Recordatorio) {
        require(!r.activo || permitido(context)) { "Activa las notificaciones de Lemac DataLab en los ajustes de Android." }
        prefs(context).edit().putBoolean("$owner.reminder", r.activo).putInt("$owner.hour", r.hora).putInt("$owner.minute", r.minuto)
            .putString("$owner.frequency", r.frecuencia).putString("$owner.scope", r.ambito?.name).apply()
        if (!r.activo) cancelar(context)
        restaurar(context)
    }

    fun cancelar(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(intent(context))
        NotificationManagerCompat.from(context).cancel(100)
    }

    fun restaurar(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(intent(context))
        val owner = prefs(context).getString("currentUser", null) ?: return
        val r = leer(context, owner)
        if (!r.activo) return
        val now = ZonedDateTime.now()
        var next = now.withHour(r.hora).withMinute(r.minuto).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        if (r.frecuencia == "Lunes a viernes") while (next.dayOfWeek in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) next = next.plusDays(1)
        if (r.frecuencia == "Semanal") {
            val day = prefs(context).getInt("$owner.weekday", now.dayOfWeek.value)
            prefs(context).edit().putInt("$owner.weekday", day).apply()
            while (next.dayOfWeek.value != day) next = next.plusDays(1)
        }
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), intent(context))
    }

    fun notificar(context: Context) {
        val owner = prefs(context).getString("currentUser", null) ?: return
        if (!leer(context, owner).activo || !permitido(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("recordatorios", "Recordatorios", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Avisos discretos de actividades pendientes"
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        })
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("ambito", leer(context, owner).ambito?.name)
        val pending = PendingIntent.getActivity(context, 101, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, "recordatorios").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Lemac DataLab").setContentText("Tienes una actividad pendiente.")
            .setVisibility(NotificationCompat.VISIBILITY_SECRET).setContentIntent(pending).setAutoCancel(true).build()
        manager.notify(100, notification)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == null) ReminderScheduler.notificar(context)
        ReminderScheduler.restaurar(context)
    }
}
