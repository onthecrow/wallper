// Screens of the built-in black hole: user settings, and in debuggable builds a debug screen with the
// live renderer, FPS, GPU time, parameter sliders and the reference lens.
plugins {
    id("wallper.android.library")
    id("wallper.android.compose")
    id("wallper.hilt")
}

android {
    namespace = "com.onthecrow.wallper.blackhole.ui"
}

dependencies {
    implementation(projects.feature.blackhole.uiApi)
    implementation(projects.feature.blackhole.logicApi)
    implementation(projects.core.coroutines)
    implementation(projects.core.mvi)
    implementation(projects.core.navigation.api)
    implementation(projects.core.designsystem)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.timber)
    testImplementation(libs.kotlinx.coroutines.test)
}
