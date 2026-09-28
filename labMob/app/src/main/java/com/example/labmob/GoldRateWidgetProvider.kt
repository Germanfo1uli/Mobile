package com.example.labmob

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
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
        if (ids.isEmpty()) return
        val cache = context.getSharedPreferences(CACHE_NAME, Context.MODE_PRIVATE)
        val cachedValue = cache.getString(KEY_VALUE, null)
        val currentCaption = "ЗОЛОТО • ${phoneDate()}"
        ids.forEach {
            manager.updateAppWidget(
                it,
                views(context, cachedValue ?: "ОБНОВЛЯЕМ…", if (cachedValue == null) "ЗОЛОТО • ЦБ РФ" else currentCaption),
            )
        }
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val result = runCatching { withTimeout(10_000) { GoldRateRepository().latest() } }
                val rate = result.getOrNull()
                val value = rate?.let {
                    NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU")).apply {
                        minimumFractionDigits = 2
                        maximumFractionDigits = 2
                    }.format(it.rublesPerGram) + " ₽/Г"
                } ?: cachedValue ?: "НЕТ СВЯЗИ"
                val caption = if (rate != null || cachedValue != null) {
                    currentCaption
                } else {
                    "КОСНИСЬ, ЧТОБЫ ПОВТОРИТЬ"
                }
                if (rate != null) {
                    cache.edit().putString(KEY_VALUE, value).putString(KEY_CAPTION, caption).apply()
                } else {
                    Log.w(TAG, "Unable to refresh gold widget", result.exceptionOrNull())
                }
                ids.forEach { manager.updateAppWidget(it, views(context, value, caption)) }
            } finally {
                pending.finish()
            }
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
        private const val CACHE_NAME = "gold_widget_cache"
        private const val KEY_VALUE = "value"
        private const val KEY_CAPTION = "caption"
        private const val TAG = "GoldRateWidget"
    }
}

private fun phoneDate(): String = SimpleDateFormat("dd.MM.yyyy", Locale.forLanguageTag("ru-RU")).format(Date())
