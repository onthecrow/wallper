// OpenGL ES rendering for the live wallpaper. No DI, no Room, no ExoPlayer: renderers get plain
// sizes, crop and media facts, and hand the video Surface back through a callback.
plugins {
    id("wallper.android.library")
}

android {
    namespace = "com.onthecrow.wallper.core.gl"
}

dependencies {
    api(projects.core.model)
    implementation(libs.timber)
}
