// The black hole without Android: physics (lens tables, BLACKHOLE_TZ.md §5), camera, input smoothing,
// animation phases, frame pacing, every tunable parameter, the user settings, and what the screens ask of
// logic-impl (settings, lens tables, a renderer for the live preview). Tested on the JVM.
plugins {
    id("wallper.jvm.library")
    alias(libs.plugins.serialization)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}
