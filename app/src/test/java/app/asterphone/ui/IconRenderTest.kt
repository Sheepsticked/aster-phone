package app.asterphone.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import app.asterphone.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/** Renders the launcher and status bar icons under build/screenshots and checks the mark stays in the safe zone. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi", application = Application::class)
class IconRenderTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    private fun drawable(id: Int): Drawable = ContextCompat.getDrawable(context, id)!!.mutate()

    private fun render(drawable: Drawable, size: Int, tint: Int? = null): Bitmap {
        tint?.let { drawable.colorFilter = PorterDuffColorFilter(it, PorterDuff.Mode.SRC_IN) }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun layer(id: Int, tint: Int? = null): (Int) -> Bitmap = { size -> render(drawable(id), size, tint) }

    private fun solid(color: Int): (Int) -> Bitmap = { size -> Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { eraseColor(color) } }

    /** The visible 72 dp window of the 108 dp icon, cut to [mask] and drawn over a light backdrop. */
    private fun icon(size: Int, mask: Path.(Float) -> Unit, background: (Int) -> Bitmap, foreground: (Int) -> Bitmap): Bitmap {
        val scale = size / 72f
        val whole = (108 * scale).roundToInt()
        val masked = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(masked).apply {
            translate(-18 * scale, -18 * scale)
            drawBitmap(background(whole), 0f, 0f, null)
            drawBitmap(foreground(whole), 0f, 0f, null)
        }
        val shape = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(shape).drawPath(Path().apply { mask(size.toFloat()) }, Paint(Paint.ANTI_ALIAS_FLAG))
        Canvas(masked).drawBitmap(shape, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(BACKDROP)
            drawBitmap(masked, 0f, 0f, null)
        }
        return out
    }

    private val circle: Path.(Float) -> Unit = { size -> addCircle(size / 2, size / 2, size / 2, Path.Direction.CW) }

    // The superellipse |x|^4 + |y|^4 = 1, close to the rounded squares launchers use.
    private val squircle: Path.(Float) -> Unit = { size ->
        val half = size / 2
        for (step in 0..SQUIRCLE_STEPS) {
            val t = 2 * Math.PI * step / SQUIRCLE_STEPS
            val x = half + half * sign(cos(t)).toFloat() * abs(cos(t)).pow(0.5).toFloat()
            val y = half + half * sign(sin(t)).toFloat() * abs(sin(t)).pow(0.5).toFloat()
            if (step == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    private fun save(name: String, bitmap: Bitmap) {
        val dir = File(System.getProperty("asterphone.screenshotDir") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun launcherIcons() {
        val background = layer(R.drawable.ic_launcher_background)
        val foreground = layer(R.drawable.ic_launcher_foreground)
        save("icon-circle", icon(192, circle, background, foreground))
        save("icon-squircle", icon(192, squircle, background, foreground))
        save("icon-48", icon(48, circle, background, foreground))

        // Themed icons tint the single-colour layer over a light tonal background.
        val themed = layer(R.drawable.ic_launcher_monochrome, Color.rgb(0x3F, 0x2B, 0x7A))
        save("icon-themed", icon(192, circle, solid(Color.rgb(0xE9, 0xDD, 0xFF)), themed))

        // Debug builds use white arms on a blue background.
        val debug = drawable(R.mipmap.ic_launcher) as AdaptiveIconDrawable
        save("icon-debug", icon(192, circle, { size -> render(debug.background, size) }, { size -> render(debug.foreground, size) }))
    }

    @Test
    fun statusIconsAreWhiteOnDarkGrey() {
        for ((name, id) in listOf("stat-call" to R.drawable.ic_stat_call, "stat-app" to R.drawable.ic_stat_app)) {
            val bitmap = solid(Color.rgb(0x30, 0x30, 0x30))(72)
            Canvas(bitmap).drawBitmap(render(drawable(id), 72), 0f, 0f, null)
            save(name, bitmap)
        }
    }

    @Test
    fun markStaysInsideTheSafeZone() {
        val px = 8
        val foreground = layer(R.drawable.ic_launcher_foreground)(108 * px)
        val reach = farthestOpaqueDp(foreground, px)
        assertTrue("the mark reaches $reach dp from the centre", reach <= SAFE_ZONE_DP / 2)
        assertEquals("the mark reaches about 24 dp from the centre", 24f, reach, 1f)
    }

    @Test
    fun monochromeLayerMatchesTheMark() {
        val px = 8
        val foreground = layer(R.drawable.ic_launcher_foreground)(108 * px)
        val mono = layer(R.drawable.ic_launcher_monochrome)(108 * px)
        assertEquals(farthestOpaqueDp(foreground, px), farthestOpaqueDp(mono, px), 0.5f)
    }

    /** Distance in dp from the viewport centre to the farthest pixel that is not transparent. */
    private fun farthestOpaqueDp(bitmap: Bitmap, px: Int): Float {
        val centre = bitmap.width / 2f
        var far = 0.0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            if (Color.alpha(bitmap.getPixel(x, y)) > 0x10) far = maxOf(far, hypot(x + 0.5 - centre, y + 0.5 - centre))
        }
        return (far / px).toFloat()
    }

    private companion object {
        const val SAFE_ZONE_DP = 66f
        const val SQUIRCLE_STEPS = 360
        val BACKDROP = Color.rgb(0xDD, 0xDD, 0xDD)
    }
}
