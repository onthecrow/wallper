// The black hole on OpenGL ES 3.0 (BLACKHOLE_TZ.md §6–§9): the renderer (lens, disk, sky and post passes,
// GPU-generated textures), its wallpaper engine, the list stills, the lens table cache and the settings
// store. Bound into the service as a BuiltInWallpaperProvider; the screens see only logic-api.
// UI-free on purpose (checked by build-logic): the wallpaper runs in direct boot.
plugins {
    id("wallper.android.library")
    id("wallper.hilt")
}

android {
    namespace = "com.onthecrow.wallper.blackhole.logic"
}

dependencies {
    api(projects.feature.blackhole.logicApi)
    implementation(projects.core.engine.api)
    implementation(projects.core.gl)
    implementation(projects.core.diagnostics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
}

// Compiles the shaders for every quality level without a device (scripts/validate_blackhole_shaders.py).
val validateShaders = tasks.register<Exec>("validateBlackHoleShaders") {
    group = "verification"
    description = "Compiles the black hole shaders as GLSL ES 3.00 for every quality level."
    workingDir = rootDir
    commandLine("python3", "scripts/validate_blackhole_shaders.py")
}
tasks.named("check") { dependsOn(validateShaders) }
