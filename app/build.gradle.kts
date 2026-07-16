import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// RevenueCat Android SDK (public) API key — kept out of git in local.properties as
// `revenuecat.apiKey=goog_...`, falling back to the REVENUECAT_API_KEY env var for CI. Blank is
// fine: BillingManager treats an unconfigured key as "free tier, no purchases" (see its comment),
// so the app builds and runs before RevenueCat/Play are set up.
val revenueCatApiKey: String = run {
    val props = Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    props.getProperty("revenuecat.apiKey") ?: System.getenv("REVENUECAT_API_KEY") ?: ""
}

// Release signing config, read from the gitignored keystore.properties. Absent on machines/CI
// without the upload key — release builds there fall back to unsigned (debug builds unaffected).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.vasilisneo.trackstar"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vasilisneo.trackstar"
        minSdk = 26
        targetSdk = 36
        // versionCode is Play's internal build counter — it MUST increase on every upload and is
        // not user-facing. versionName is the public "1.0.0" shown on the store; bump it only for
        // real marketing releases, not per test build.
        versionCode = 6
        versionName = "1.0.0"

        buildConfigField("String", "REVENUECAT_API_KEY", "\"$revenueCatApiKey\"")
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    // TODO: this pulls in the full icon set just for Visibility/VisibilityOff (login's
    // show/hide-password toggle) — fine for now, but swap for two hand-picked vector
    // drawables before release to avoid the APK size hit.
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    // iOS-style frosted-glass backdrop blur (the collapsing workout header). Uses RenderEffect
    // on API 31+, degrades to a translucent scrim on older devices.
    implementation("dev.chrisbanes.haze:haze:1.2.2")
    // QR code generation (the "My QR" tab) + decoding (the Scan tab reuses zxing's
    // MultiFormatReader on CameraX frames, so no separate ML Kit dependency is needed).
    implementation("com.google.zxing:core:3.5.3")
    // CameraX — live camera preview + frame analysis for the QR scanner.
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    // In-app subscriptions via RevenueCat (wraps Google Play Billing). iOS uses the RC SDK too,
    // and the backend already syncs plans from RC's webhook.
    implementation("com.revenuecat.purchases:purchases:8.10.1")
    // Networking — talks to the Spring Boot API (fitness-book-api).
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
