import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing comes from a properties file kept outside the repository.
// Keys: storeFile, storePassword, keyAlias, keyPassword.
val signingProperties = Properties().apply {
    val path = providers.gradleProperty("asterphone.signing").orNull
        ?: "${System.getProperty("user.home")}/.android-keys/asterphone-signing.properties"
    val file = File(path)
    if (file.isFile) file.inputStream().use { load(it) }
}

android {
    namespace = "app.asterphone"
    // Current AndroidX libraries require compiling against 37; runtime behaviour follows targetSdk.
    compileSdk = 37

    defaultConfig {
        applicationId = "app.asterphone"
        minSdk = 29
        targetSdk = 36
        versionCode = 11
        versionName = "0.6.1"

        // The SIP library ships native code for four ABIs; current phones only need arm64.
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        if (!signingProperties.isEmpty) {
            create("release") {
                storeFile = File(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Installs next to the release build, so it can be tested on a phone that uses the app.
            applicationIdSuffix = ".debug"
        }
        release {
            // The SIP library ships its own keep rules for the classes its native code uses.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
    }

    androidResources {
        // Drop SIP library sounds the library itself never refers to (extra ringtones, test and chat tones).
        // The first part repeats aapt's default ignore list, which this setting replaces.
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~:" +
            "its_a_game.mkv:soft_as_snow.mkv:house_keeping.mkv:four_hands_together.mkv:leaving_dreams.mkv:" +
            "hello8000.wav:hello16000.wav:incoming_chat.wav"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Compose UI tests run on the JVM through Robolectric, which needs the app's resources.
            isIncludeAndroidResources = true
            all { test ->
                test.maxHeapSize = "1g"
                // Robolectric's Android 16 runtime reaches into JDK internals for file descriptors.
                test.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
                test.systemProperty("asterphone.screenshotDir", layout.buildDirectory.dir("screenshots").get().asFile.path)
            }
        }
    }
}

dependencies {
    implementation(libs.linphone)
    implementation(libs.androidx.core.ktx)
    // Registers calls with Android's Telecom service, which coordinates them with mobile calls.
    implementation(libs.androidx.core.telecom)
    // Lets the SIP library manage audio focus and play the system ringtone.
    implementation(libs.androidx.media)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // Registers the empty activity that Compose UI tests render into.
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
