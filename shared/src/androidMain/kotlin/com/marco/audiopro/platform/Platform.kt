package com.marco.audiopro.platform

import android.graphics.Bitmap
import android.graphics.Paint
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import android.content.ClipData
import android.content.Intent
import java.io.ByteArrayOutputStream
import java.io.File
import coil3.Image
import coil3.toBitmap

// Mismas firmas que en desktopMain: el código de src/jvmShared las usa sin expect/actual.

fun coilImageToImageBitmap(image: Image): ImageBitmap {
    val b = image.toBitmap()
    // Los bitmaps de hardware no se pueden leer pixel por pixel.
    val readable = if (b.config == Bitmap.Config.HARDWARE) b.copy(Bitmap.Config.ARGB_8888, false) else b
    return readable.asImageBitmap()
}

/** Bitmap ARGB que se puede reescribir cada frame (espectrograma). */
class PixelBuffer(val width: Int, val height: Int) {
    val pixels = IntArray(width * height)
    private val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val img = bmp.asImageBitmap()

    fun image(): ImageBitmap {
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        return img
    }
}

private val pointPaint = Paint().apply { isAntiAlias = true; strokeCap = Paint.Cap.ROUND }

/** Nube de puntos sin asignaciones (goniómetro). `points` = x0,y0,x1,y1… */
fun DrawScope.drawPointCloud(points: FloatArray, count: Int, color: Color, strokePx: Float) {
    pointPaint.color = color.toArgb()
    pointPaint.strokeWidth = strokePx
    drawIntoCanvas { it.nativeCanvas.drawPoints(points, 0, count * 2, pointPaint) }
}

/** Modifier.blur sólo funciona en Android 12+. */
val supportsBlur: Boolean = Build.VERSION.SDK_INT >= 31

/** Selector de imagen (portadas propias): el selector de fotos del sistema. */
@Composable
fun rememberImagePicker(onPicked: (ByteArray) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val callback by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) ctx.contentResolver.openInputStream(uri)?.use { callback(it.readBytes()) }
    }
    return { launcher.launch("image/*") }
}

fun encodePng(img: ImageBitmap): ByteArray =
    ByteArrayOutputStream().also { img.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()

/** En el celular la imagen va directo a Instagram. */
val sharesToInstagram: Boolean = true

private const val INSTAGRAM = "com.instagram.android"

/**
 * Compartir la imagen de historia: directo al editor de historias de Instagram si está; si no,
 * a la de la app de Instagram o, con `instagram = false`, al menú de compartir del sistema.
 */
@Composable
fun rememberStoryShare(): (png: ByteArray, name: String, instagram: Boolean) -> String? {
    val ctx = LocalContext.current
    return remember(ctx) {
        { png, _, instagram ->
            val dir = File(ctx.cacheDir, "share").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val f = File(dir, "flacker-${System.currentTimeMillis()}.png").apply { writeBytes(png) }
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".share", f)
            val send = Intent(Intent.ACTION_SEND).setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                .apply { clipData = ClipData.newRawUri("", uri) }
            val pm = ctx.packageManager
            val story = Intent(send).setClassName(INSTAGRAM, "com.instagram.share.handleractivity.StoryShareHandlerActivity")
            val app = Intent(send).setPackage(INSTAGRAM)
            when {
                !instagram -> { ctx.startActivity(Intent.createChooser(send, "Compartir").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); null }
                story.resolveActivity(pm) != null -> { ctx.startActivity(story); null }
                app.resolveActivity(pm) != null -> { ctx.startActivity(app); null }
                else -> {
                    ctx.startActivity(Intent.createChooser(send, "Compartir").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    "No encontré Instagram: elige dónde compartirla"
                }
            }
        }
    }
}

/** Modo inmersivo: esconde las barras del sistema, deja la pantalla prendida y "atrás" sale. */
@Composable
fun ImmersiveSystem(on: Boolean, onBack: () -> Unit) {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.activity.compose.BackHandler(enabled = on, onBack = onBack)
    androidx.compose.runtime.DisposableEffect(on) {
        var ctx = view.context
        while (ctx is android.content.ContextWrapper && ctx !is android.app.Activity) ctx = ctx.baseContext
        val window = (ctx as? android.app.Activity)?.window
        val ctl = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        if (on) {
            ctl?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            ctl?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

/** Modo sin servidor: pide permiso para leer la música del celular ("device" si lo dio, null si no). */
@Composable
fun rememberLocalMusicPicker(onPicked: (String?) -> Unit): () -> Unit {
    val callback by rememberUpdatedState(onPicked)
    val perm = if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> callback(if (ok) "device" else null) }
    return { launcher.launch(perm) }
}

/** En la PC se eligen carpetas; en el celular, toda la música del teléfono. */
val localMusicIsFolders: Boolean = false

/** Fondos por shader (AGSL): Android 13 en adelante. */
val supportsFxShaders: Boolean = Build.VERSION.SDK_INT >= 33

/** Mismo contrato que en la PC; en Android < 13 no hay shaders y [ok] es false. */
class FxShader(sksl: String) {
    private val shader: Any? = if (Build.VERSION.SDK_INT >= 33) {
        runCatching { android.graphics.RuntimeShader(sksl) }.onFailure { android.util.Log.w("FxShader", it.message ?: "") }.getOrNull()
    } else null
    /** Uniformes que el compilador quitó por no usarse (fijarlos truena): se brincan. */
    private val missing = HashSet<String>()
    val ok: Boolean get() = shader != null

    fun brush(w: Float, h: Float, time: Float, flow: Float, audio: FloatArray, cols: List<Color>, extra: FloatArray? = null): androidx.compose.ui.graphics.Brush? {
        if (Build.VERSION.SDK_INT < 33) return null
        val s = shader as? android.graphics.RuntimeShader ?: return null
        fun set(name: String, vararg v: Float) {
            if (name in missing) return
            try {
                when (v.size) {
                    1 -> s.setFloatUniform(name, v[0])
                    2 -> s.setFloatUniform(name, v[0], v[1])
                    3 -> s.setFloatUniform(name, v[0], v[1], v[2])
                    else -> s.setFloatUniform(name, v[0], v[1], v[2], v[3])
                }
            } catch (_: IllegalArgumentException) { missing.add(name) }
        }
        set("iRes", w, h); set("iTime", time); set("iFlow", flow)
        if (extra != null) for (k in 0 until extra.size / 4) set("iP$k", extra[k * 4], extra[k * 4 + 1], extra[k * 4 + 2], extra[k * 4 + 3])
        set("iAudio", audio[0], audio[1], audio[2], audio[3])
        set("c0", cols[0].red, cols[0].green, cols[0].blue)
        set("c1", cols[1].red, cols[1].green, cols[1].blue)
        set("c2", cols[2].red, cols[2].green, cols[2].blue)
        return androidx.compose.ui.graphics.ShaderBrush(s)
    }
}
