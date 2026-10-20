package app.asterphone.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

val CallGreen = Color(0xFF1E8E3E)
val CallRed = Color(0xFFD93025)
val WaitAmber = Color(0xFFF9AB00)

/** Background for white text on the dialer's return-to-call banner; reaches 4.5:1 where [CallGreen] does not. */
val CallBannerGreen = Color(0xFF137333)

@Composable
fun AsterPhoneTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFFCDBDFF))
        else -> lightColorScheme(primary = Color(0xFF5B3FA8))
    }
    // Cards and other medium surfaces share one corner radius with the app's bars.
    MaterialTheme(colorScheme = colors, shapes = Shapes(medium = RoundedCornerShape(16.dp)), content = content)
}
