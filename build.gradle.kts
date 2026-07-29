plugins {
    id("com.android.application") version "8.6.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    // KSP — annotation processing for Room (version tracks the Kotlin version).
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    // Google Services — processes google-services.json for Firebase (FCM push).
    id("com.google.gms.google-services") version "4.5.0" apply false
}
