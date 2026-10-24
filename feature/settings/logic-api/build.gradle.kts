// The app's settings without Android: what they are, how they are stored as JSON, and what the screens ask
// of logic-impl (the settings, a report to share).
plugins {
    id("wallper.jvm.library")
    alias(libs.plugins.serialization)
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}
