package com.example.suicanfcreader.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.example.suicanfcreader.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class BalanceWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            appWidgetManager.updateAppWidget(widgetId, buildViews(context))
        }
    }

    private fun buildViews(context: Context): RemoteViews {
        val prefs = context.getSharedPreferences("suica_reader_history", Context.MODE_PRIVATE)
        val cards = runCatching {
            val array = JSONArray(prefs.getString("history", "[]") ?: "[]")
            List(array.length()) { index -> array.getJSONObject(index) }
        }.getOrDefault(emptyList())

        val aliases = runCatching {
            val obj = JSONObject(prefs.getString("card_aliases", "{}") ?: "{}")
            obj.keys().asSequence().associateWith { key -> obj.optString(key) }
        }.getOrDefault(emptyMap())

        val latestByCard = cards.groupBy { it.optString("cardId", "legacy") }
            .mapValues { (_, records) -> records.maxByOrNull { it.optInt("number", 0) } }
            .filterValues { it != null }

        val totalSpending = cards.sumOf { record ->
            val amount = record.optString("amount").toIntOrNull() ?: 0
            if (amount < 0) -amount else 0
        }
        val movementCount = cards.count { record ->
            !record.optString("inStation").isNullOrBlank() || !record.optString("outStation").isNullOrBlank()
        }

        val lines = latestByCard.entries.take(3).joinToString("\n") { (cardId, record) ->
            val title = aliases[cardId] ?: cardId.maskForDisplay()
            "$title  ${record?.optString("balance").formatYen()}"
        }.ifBlank { "交通系ICカードをかざしてください" }

        return RemoteViews(context.packageName, R.layout.balance_widget).apply {
            setTextViewText(R.id.widget_title, "suicanfc kd")
            setTextViewText(R.id.widget_balances, lines)
            setTextViewText(
                R.id.widget_stats,
                "支出 ${totalSpending.formatYen()} / 移動 ${movementCount}回 / 利用 ${cards.size}件"
            )
        }
    }

    private fun String?.formatYen(): String {
        val value = this?.toIntOrNull() ?: return "¥--"
        return String.format(Locale.JAPAN, "¥%,d", value)
    }

    private fun Int.formatYen(): String =
        String.format(Locale.JAPAN, "¥%,d", this)

    private fun String.maskForDisplay(): String =
        if (length <= 4) this else "****${takeLast(4)}"
}
