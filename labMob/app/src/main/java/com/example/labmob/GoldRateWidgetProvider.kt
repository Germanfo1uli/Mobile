package com.example.labmob

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class GoldRateWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh(context, manager, ids)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            refresh(context, manager, manager.getAppWidgetIds(ComponentName(context, javaClass)))
        }
    }

    private fun refresh(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context, "ОБНОВЛЯЕМ…", "ЦБ РФ")) }
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val result = runCatching { GoldRateRepository().latest() }
            val rate = result.getOrNull()
            val value = rate?.let {
                NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU")).apply {
                    minimumFractionDigits = 2
                    maximumFractionDigits = 2
                }.format(it.rublesPerGram) + " ₽/Г"
            } ?: "НЕТ СВЯЗИ"
            val caption = rate?.let { "ЦБ • ${it.date}" } ?: "КОСНИСЬ, ЧТОБЫ ПОВТОРИТЬ"
            ids.forEach { manager.updateAppWidget(it, views(context, value, caption)) }
            pending.finish()
        }
    }

    private fun views(context: Context, value: String, caption: String): RemoteViews =
        RemoteViews(context.packageName, R.layout.gold_rate_widget).apply {
            setTextViewText(R.id.gold_rate_value, value)
            setTextViewText(R.id.gold_rate_date, caption)
            val intent = Intent(context, GoldRateWidgetProvider::class.java).setAction(ACTION_REFRESH)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 71, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            setOnClickPendingIntent(R.id.gold_widget_sphere, pendingIntent)
        }

    companion object {
        const val ACTION_REFRESH = "com.example.labmob.REFRESH_GOLD_WIDGET"
    }
}
