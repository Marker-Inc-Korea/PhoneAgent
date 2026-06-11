package ai.markr.phoneagent.platform

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A small status bar drawn on top of every app while the agent runs, using the
 * accessibility overlay window type (no SYSTEM_ALERT_WINDOW permission needed).
 */
class OverlayStatus(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null
    private var statusText: TextView? = null

    /** Set by the runner so the overlay's stop button can cancel the run. */
    var onStop: (() -> Unit)? = null

    fun show(text: String) {
        if (view == null) {
            view = buildView()
            wm.addView(view, layoutParams())
        }
        statusText?.text = text
    }

    fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        statusText = null
    }

    private fun buildView(): View {
        val pad = dp(12)
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#E6201A3B"))
            setPadding(pad, dp(8), pad, dp(8))
        }
        val tv = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            text = "PhoneAgent 실행 중"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val stop = Button(context).apply {
            text = "중지"
            isAllCaps = false
            setTypeface(typeface, Typeface.BOLD)
            setOnClickListener { onStop?.invoke() }
        }
        statusText = tv
        row.addView(tv)
        row.addView(stop)
        return row
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.TOP }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
}
