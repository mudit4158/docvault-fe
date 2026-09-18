import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing. keystore.properties (and the .jks it points at) are
// gitignored — never committed. Absent entirely for anyone who hasn't been
// handed the real keystore: assembleDebug still works either way, and
// assembleRelease fails loudly (missing signingConfig) rather than silently
// producing an unsigned APK.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// The google-services plugin FAILS THE BUILD if google-services.json is
// missing — so it's applied conditionally, not in the plugins {} block
// above, until a real Firebase project's config file is dropped into app/.
// OTP login's Kotlin code compiles either way (firebase-auth is a normal
// dependency, unaffected by this); only signing in with it needs the file.
// See docvault-be's equivalent GCS_CREDENTIALS_PATH story for the same
// "code ready, not yet live" shape.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.appdistribution")
    // serviceCredentialsFile: a dedicated service account (roles/
    // firebaseappdistro.admin only, nothing broader) — see app-distribution-
    // key.json, gitignored, never committed. No Firebase CLI / Node.js
    // needed this way.
    // groups: the tester group's alias from the Firebase console ->
    // Release & Monitor -> App Distribution -> Testers & Groups. Passed at
    // build time (-PappDistributionGroups=...) rather than hardcoded here,
    // since the group didn't exist yet when this was wired up.
    firebaseAppDistribution {
        serviceCredentialsFile = rootProject.file("app-distribution-key.json").path
        groups = (project.findProperty("appDistributionGroups") as String?).orEmpty()
    }
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
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "DEFAULT_API_BASE_URL", "\"$defaultApiUrl\"")
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            // Without keystore.properties present, this build type has no
            // signingConfig at all — assembleRelease then fails at the
            // packaging step instead of quietly emitting an unsigned APK.
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

    // OTP login: Firebase Phone Auth is entirely client-driven — this app
    // talks to Firebase directly, and Firebase sends the SMS. The backend
    // only ever verifies the resulting ID token (see docvault-be's
    // FirebaseOtpProvider). Compiles either way; actually signing in needs
    // google-services.json (see the conditional plugin application above).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    // .await() on the Task<T> Firebase's callback-based APIs return.
    implementation(libs.kotlinx.coroutines.play.services)

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
