plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Where the app looks for the backend by default.
//
//   Emulator        -> http://10.0.2.2:8000/   (10.0.2.2 is the host loopback)
//   Physical device -> http://<laptop LAN IP>:8000/
//
// Override without editing this file:
//   ./gradlew :app:assembleDebug -PdocvaultApiUrl=http://192.168.1.42:8000/
//
// It is also editable inside the app on the sign-in screen, so one APK works
// on both an emulator and a phone.
val defaultApiUrl: String =
    (project.findProperty("docvaultApiUrl") as String?) ?: "http://10.0.2.2:8000/"

android {
    namespace = "com.docvault.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.docvault.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "DEFAULT_API_BASE_URL", "\"$defaultApiUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    // Bearer token at rest
    implementation(libs.androidx.security.crypto)

    // Scan: capture + edge detection + crop UI + multi-page + gallery import,
    // all in one Google-maintained flow (see docvault-be's scan_to_pdf.md —
    // either capture approach uploads through the same endpoint, so this
    // choice has no backend impact).
    implementation(libs.play.services.mlkit.document.scanner)
    // Downsampled, cached thumbnail loading for the page reorder strip and the
    // zoom-inspect canvas — no image-loading infra existed before Scan.
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    // Lets PageTransforms' real Bitmap/Canvas/ColorMatrix operations run on
    // the JVM without a device.
    testImplementation(libs.robolectric)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
