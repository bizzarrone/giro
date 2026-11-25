package simplylucaapps.girocalendar.ui.trails

import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Se si esce dall'app con un dito ancora appoggiato, il WebView non riceve mai "dito sollevato" e resta con un tocco
 *  fantasma: al rientro un dito solo si comporta come un pinch (zoom/rotazione impazziti). Un ACTION_CANCEL sintetico azzera lo stato. */
fun WebView.cancelTouches() {
    val t = SystemClock.uptimeMillis()
    val e = MotionEvent.obtain(t, t, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
    e.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
    runCatching { dispatchTouchEvent(e) }
    e.recycle()
}

@Composable
fun CancelTouchesOnLifecycle(get: () -> WebView?) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val o = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_PAUSE || ev == Lifecycle.Event.ON_RESUME) get()?.cancelTouches()
        }
        lifecycle.addObserver(o)
        onDispose { lifecycle.removeObserver(o) }
    }
}
