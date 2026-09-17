plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Applied conditionally in app/build.gradle.kts, only once google-services.json
    // exists — see the comment there. Declared here so the classpath is
    // resolvable either way.
    alias(libs.plugins.google.services) apply false
    // Uploads a signed release APK to Firebase App Distribution's tester
    // group. Declared here (classpath only) — actually running
    // `appDistributionUploadRelease` needs a Firebase service-account key or
    // an authenticated `firebase login:ci`, set up when distribution
    // actually happens, not part of this deploy.
    alias(libs.plugins.firebase.appdistribution) apply false
}
