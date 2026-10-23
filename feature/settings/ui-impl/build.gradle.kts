// The app's settings screen. Its first item sets Wallper as the device's wallpaper.
plugins {
    id("wallper.android.library")
    id("wallper.android.compose")
    id("wallper.hilt")
}

android {
    namespace = "com.onthecrow.wallper.settings.ui"
}

dependencies {
    implementation(projects.feature.settings.uiApi)
    implementation(projects.core.engine.api)
    implementation(projects.core.coroutines)
    implementation(projects.core.mvi)
    implementation(projects.core.navigation.api)
    implementation(projects.core.designsystem)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.timber)
    testImplementation(libs.kotlinx.coroutines.test)
}
