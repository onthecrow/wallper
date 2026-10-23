// The black hole on OpenGL ES 3.0 (BLACKHOLE_TZ.md §6–§9): lens, disk, sky and post passes, GPU-generated
// textures, lens table cache and the user settings. Knows nothing about WallpaperService: the wallpaper
// engine, the debug screen and the list preview all drive the same BlackHoleRenderer.
plugins {
    id("wallper.android.library")
    id("wallper.hilt")
}

android {
    namespace = "com.onthecrow.wallper.core.blackhole.renderer"
}

dependencies {
    api(projects.core.blackhole.physics)
    api(projects.core.gl)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
}
