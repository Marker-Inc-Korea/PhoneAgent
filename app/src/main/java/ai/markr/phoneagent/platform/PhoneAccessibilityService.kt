package ai.markr.phoneagent.platform

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * The on-device engine. Holds a global reference to itself while connected so
 * the agent (via [AccessibilityController]) can read the screen and act.
 */
class PhoneAccessibilityService : AccessibilityService() {

    @Volatile var lastPackage: String = ""
        private set
    @Volatile var lastActivity: String = ""
        private set

    private var overlay: OverlayStatus? = null

    /** Set by the runner so the overlay's stop button can cancel the active run. */
    @Volatile var onStopRequested: (() -> Unit)? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        overlay = OverlayStatus(this).apply { onStop = { onStopRequested?.invoke() } }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.let { lastPackage = it.toString() }
            event.className?.let { lastActivity = it.toString().substringAfterLast('.') }
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        overlay?.hide()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        overlay?.hide()
        super.onDestroy()
    }

    fun currentTree(): SerializedTree = TreeSerializer.serialize(rootInActiveWindow)

    fun showStatus(text: String) = overlay?.show(text)
    fun hideStatus() = overlay?.hide()

    suspend fun dispatchTap(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 60)
        return dispatch(GestureDescription.Builder().addStroke(stroke).build())
    }

    suspend fun dispatchSwipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int): Boolean {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.toLong().coerceIn(50, 3000))
        return dispatch(GestureDescription.Builder().addStroke(stroke).build())
    }

    private suspend fun dispatch(gesture: GestureDescription): Boolean =
        suspendCancellableCoroutine { cont ->
            val ok = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(d: GestureDescription?) { if (cont.isActive) cont.resume(true) }
                    override fun onCancelled(d: GestureDescription?) { if (cont.isActive) cont.resume(false) }
                },
                null,
            )
            if (!ok && cont.isActive) cont.resume(false)
        }

    suspend fun captureScreenshotJpeg(): ByteArray? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val bitmap = suspendCancellableCoroutine<Bitmap?> { cont ->
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val bmp = try {
                            Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                        } catch (_: Exception) {
                            null
                        } finally {
                            result.hardwareBuffer.close()
                        }
                        if (cont.isActive) cont.resume(bmp)
                    }

                    override fun onFailure(errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }
                },
            )
        } ?: return null
        return encodeJpeg(bitmap)
    }

    private fun encodeJpeg(bitmap: Bitmap): ByteArray {
        val scaled = downscale(bitmap, MAX_EDGE)
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
            out.toByteArray()
        }
    }

    private fun downscale(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt(),
            (bitmap.height * ratio).toInt(),
            true,
        )
    }

    companion object {
        private const val MAX_EDGE = 1280

        @Volatile
        var instance: PhoneAccessibilityService? = null
            private set

        val isConnected: Boolean get() = instance != null
    }
}
