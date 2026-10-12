package remy.irremote

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.view.View
import android.widget.RemoteViews

// prefs "w<id>" = "<remote>:<button>,<button>,..." (1-4 buttons), written by WidgetConfigActivity
class RemoteWidget : AppWidgetProvider() {
    companion object {
        private const val SEND = "remy.irremote.SEND"
        private val SLOTS = intArrayOf(R.id.b0, R.id.b1, R.id.b2, R.id.b3)

        private fun prefs(ctx: Context) = ctx.getSharedPreferences("widgets", Context.MODE_PRIVATE)

        fun save(ctx: Context, id: Int, remote: Int, buttons: List<Int>) =
            prefs(ctx).edit().putString("w$id", "$remote:${buttons.joinToString(",")}").apply()

        private fun load(ctx: Context, id: Int): Pair<Int, List<Int>>? {
            val (r, bs) = prefs(ctx).getString("w$id", null)?.split(":") ?: return null
            return r.toInt() to bs.split(",").map { it.toInt() }
        }

        fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val (ri, bis) = load(ctx, id) ?: return
            val remote = Ir.remotes(ctx).getOrNull(ri) ?: return
            val views = RemoteViews(ctx.packageName, R.layout.widget)
            SLOTS.forEachIndexed { slot, vid ->
                val b = bis.getOrNull(slot)?.let { remote.buttons.getOrNull(it) }
                if (b == null) { views.setViewVisibility(vid, View.GONE); return@forEachIndexed }
                views.setViewVisibility(vid, View.VISIBLE)
                views.setTextViewText(vid, b.label)
                views.setTextColor(vid, labelColor(b.color))
                views.setColorStateList(vid, "setBackgroundTintList", ColorStateList.valueOf(buttonColor(b.color)))
                val intent = Intent(ctx, RemoteWidget::class.java).setAction(SEND)
                    .putExtra("remote", ri).putExtra("button", bis[slot])
                views.setOnClickPendingIntent(vid, PendingIntent.getBroadcast(ctx, id * 4 + slot, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            mgr.updateAppWidget(id, views)
        }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = ids.forEach { update(ctx, mgr, it) }

    override fun onDeleted(ctx: Context, ids: IntArray) =
        prefs(ctx).edit().apply { ids.forEach { remove("w$it") } }.apply()

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != SEND) return super.onReceive(ctx, intent)
        val remote = Ir.remotes(ctx).getOrNull(intent.getIntExtra("remote", -1)) ?: return
        remote.buttons.getOrNull(intent.getIntExtra("button", -1))?.let { Ir.send(ctx, remote, it) }
    }
}
