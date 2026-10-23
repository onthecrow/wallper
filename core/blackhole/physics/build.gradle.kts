// Black hole wallpaper math without Android: lens tables (BLACKHOLE_TZ.md §5), camera, input smoothing,
// animation phases, frame pacing, every tunable parameter and the user settings. Tested on the JVM.
plugins {
    id("wallper.jvm.library")
    alias(libs.plugins.serialization)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}
