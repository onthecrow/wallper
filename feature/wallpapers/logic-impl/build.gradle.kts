plugins {
    id("wallper.android.library")
    id("wallper.hilt")
    id("wallper.android.room")
}

android {
    namespace = "com.onthecrow.wallper.wallpapers.logic"
}

dependencies {
    api(projects.feature.wallpapers.logicApi)
    implementation(projects.core.engine.api)
    implementation(projects.core.storage)
    testImplementation(libs.kotlinx.coroutines.test)
    // Migration tests: Room on a real SQLite, with the exported schemas read as JSON.
    testImplementation(libs.sqlite.bundled.jvm)
    testImplementation(libs.kotlinx.serialization.json)
}
