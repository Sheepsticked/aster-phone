plugins {
    alias(libs.plugins.android.application) apply false
    // Not applied: only puts this Kotlin version on the classpath for AGP's built-in Kotlin.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
