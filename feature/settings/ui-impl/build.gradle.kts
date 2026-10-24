// The app's settings screen, whose first item sets Wallper as the device's wallpaper, and the storage
// screen. The report the settings share goes out through this module's FileProvider.
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
    implementation(projects.feature.settings.logicApi)
    implementation(projects.feature.wallpapers.logicApi)
    implementation(projects.feature.editor.uiApi)
    implementation(projects.core.engine.api)
    implementation(projects.core.coroutines)
    implementation(projects.core.mvi)
    implementation(projects.core.navigation.api)
    implementation(projects.core.designsystem)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.timber)
    testImplementation(libs.kotlinx.coroutines.test)
}
