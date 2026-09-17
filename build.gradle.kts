plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Applied conditionally in app/build.gradle.kts, only once google-services.json
    // exists — see the comment there. Declared here so the classpath is
    // resolvable either way.
    alias(libs.plugins.google.services) apply false
}
