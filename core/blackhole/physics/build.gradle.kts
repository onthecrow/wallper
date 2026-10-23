// Black hole wallpaper math without Android: lens tables (BLACKHOLE_TZ.md §5), camera, input smoothing,
// animation phases, frame pacing and every tunable parameter. Tested on the JVM.
plugins {
    id("wallper.jvm.library")
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}
