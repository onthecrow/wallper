// The app's settings in device-protected SharedPreferences, read by the wallpaper too (as the engine's
// VideoWallpaperSettingsSource), and the report the settings screen shares.
// UI-free on purpose (checked by build-logic): the wallpaper reads the settings in direct boot.
plugins {
    id("wallper.android.library")
    id("wallper.hilt")
}

android {
    namespace = "com.onthecrow.wallper.settings.logic"
}

dependencies {
    api(projects.feature.settings.logicApi)
    implementation(projects.core.engine.api)
    implementation(projects.core.coroutines)
    implementation(libs.androidx.core.ktx)
    implementation(libs.timber)
}
