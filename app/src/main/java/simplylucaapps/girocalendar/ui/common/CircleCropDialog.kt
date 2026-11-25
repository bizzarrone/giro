package simplylucaapps.girocalendar.ui.common

import androidx.compose.ui.res.stringResource

import simplylucaapps.girocalendar.R

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint as AndroidPaint
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val OUT_SIZE = 192
private const val PICK_MAX = 1400

private fun loadBitmap(context: android.content.Context, uri: Uri): Bitmap? = runCatching {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= PICK_MAX || bounds.outHeight / (sample * 2) >= PICK_MAX) sample *= 2
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}.getOrNull()

/**
 * Sceglie la porzione di foto che finisce dentro un cerchio: trascina per spostare, pizzica
 * (o usa il cursore) per ingrandire. Restituisce un JPEG quadrato 192×192 in base64 puro
 * (da mostrare ritagliato a cerchio), pochi KB.
 */
@Composable
fun CircleCropDialog(uri: Uri, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val context = LocalContext.current
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(uri) {
        val b = withContext(Dispatchers.IO) { loadBitmap(context, uri) }
        if (b == null) failed = true else bmp = b
    }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxPx by remember { mutableFloatStateOf(0f) }

    fun clamp(o: Offset, z: Float, b: Bitmap): Offset {
        if (boxPx <= 0f) return o
        val base = boxPx / minOf(b.width, b.height)
        val maxX = ((b.width * base * z) - boxPx).coerceAtLeast(0f) / 2f
        val maxY = ((b.height * base * z) - boxPx).coerceAtLeast(0f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.s_profile_photo)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val b = bmp
                when {
                    failed -> Text(stringResource(R.string.c_read_fail))
                    b == null -> CircularProgressIndicator()
                    else -> {
                        Text(
                            stringResource(R.string.c_hint),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        val img = remember(b) { b.asImageBitmap() }
                        Canvas(
                            Modifier.fillMaxWidth().aspectRatio(1f)
                                .onSizeChanged { boxPx = it.width.toFloat() }
                                .pointerInput(b) {
                                    detectTransformGestures { _, pan, z, _ ->
                                        zoom = (zoom * z).coerceIn(1f, 6f)
                                        offset = clamp(offset + pan, zoom, b)
                                    }
                                }
                        ) {
                            val s = size.width
                            val base = s / minOf(b.width, b.height)
                            drawRect(Color.Black)
                            withTransform({
                                translate(s / 2f + offset.x, s / 2f + offset.y)
                                scale(base * zoom, base * zoom, pivot = Offset.Zero)
                                translate(-b.width / 2f, -b.height / 2f)
                            }) { drawImage(img) }
                            val circle = Path().apply {
                                addOval(androidx.compose.ui.geometry.Rect(Offset.Zero, Size(s, s)))
                            }
                            clipPath(circle, ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.6f)) }
                            drawCircle(Color.White, radius = s / 2f - 1f, style = Stroke(2f))
                        }
                        Slider(
                            value = zoom, onValueChange = { zoom = it; offset = clamp(offset, it, b) },
                            valueRange = 1f..6f,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = bmp != null,
                onClick = {
                    val b = bmp ?: return@TextButton
                    val k = OUT_SIZE / boxPx
                    val base = boxPx / minOf(b.width, b.height)
                    val out = Bitmap.createBitmap(OUT_SIZE, OUT_SIZE, Bitmap.Config.ARGB_8888)
                    val m = Matrix().apply {
                        postTranslate(-b.width / 2f, -b.height / 2f)
                        postScale(base * zoom * k, base * zoom * k)
                        postTranslate(OUT_SIZE / 2f + offset.x * k, OUT_SIZE / 2f + offset.y * k)
                    }
                    AndroidCanvas(out).apply {
                        drawColor(android.graphics.Color.WHITE)
                        drawBitmap(b, m, AndroidPaint(AndroidPaint.FILTER_BITMAP_FLAG))
                    }
                    val bos = ByteArrayOutputStream()
                    out.compress(Bitmap.CompressFormat.JPEG, 85, bos)
                    onDone(Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP))
                },
            ) { Text(stringResource(R.string.c_use)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
