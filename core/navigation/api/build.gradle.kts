plugins {
    id("wallper.android.library")
    id("wallper.android.compose")
    alias(libs.plugins.serialization)
}

android {
    namespace = "com.onthecrow.wallper.core.navigation"
}

dependencies {
    api(libs.androidx.navigation3.runtime)
    api(libs.kotlinx.serialization.core)
}
