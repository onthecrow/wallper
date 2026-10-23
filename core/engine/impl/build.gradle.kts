// The live wallpaper: WallpaperService, its engines and the system-facing controller.
// UI-free on purpose (checked by build-logic): the service starts in direct boot and has no screens.
plugins {
    id("wallper.android.library")
    id("wallper.hilt")
}

android {
    namespace = "com.onthecrow.wallper.core.engine.impl"
}

dependencies {
    api(projects.core.engine.api)
    implementation(projects.core.blackhole.renderer)
    implementation(projects.core.diagnostics)
    implementation(projects.core.gl)
    implementation(projects.core.player)
    implementation(projects.core.resources)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.appcompat.resources)
    implementation(libs.timber)
}
