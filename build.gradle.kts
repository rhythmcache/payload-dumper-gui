plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.gradle.versions)
    alias(libs.plugins.ktfmt) apply false
}

subprojects {
    apply(plugin = "com.ncorti.ktfmt.gradle")
}
