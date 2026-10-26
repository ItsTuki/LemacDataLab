package com.losk.lemacdatalab

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.losk.lemacdatalab.data.LemacRepository
import com.losk.lemacdatalab.model.Recordatorio
import com.losk.lemacdatalab.notifications.ReminderReceiver
import com.losk.lemacdatalab.notifications.ReminderScheduler
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class ReminderTest {
    @Test fun avisoDiscretoPermaneceVisibleAlProgramarElSiguiente() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val repo = LemacRepository.get(context)
        repo.cargar()
        repo.ingresar("demo", "Demo12345!", false)
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        try {
            repo.recordatorio(Recordatorio(activo = true))
            ReminderReceiver().onReceive(context, Intent(context, ReminderReceiver::class.java))
            val manager = context.getSystemService(NotificationManager::class.java)
            val notification = manager.activeNotifications.first { it.id == 100 }.notification
            assertEquals(Notification.VISIBILITY_SECRET, notification.visibility)
            assertEquals("Tienes una actividad pendiente.", notification.extras.getString(Notification.EXTRA_TEXT))
        } finally {
            repo.recordatorio(Recordatorio(activo = false))
            ReminderScheduler.cancelar(context)
        }
    }
}
