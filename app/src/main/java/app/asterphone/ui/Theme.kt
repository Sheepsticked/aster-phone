package app.asterphone.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val CallGreen = Color(0xFF1E8E3E)
val CallRed = Color(0xFFD93025)
val WaitAmber = Color(0xFFF9AB00)

@Composable
fun AsterPhoneTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFF6FD8AE))
        else -> lightColorScheme(primary = Color(0xFF0B6E4F))
    }
    MaterialTheme(colorScheme = colors, content = content)
}
