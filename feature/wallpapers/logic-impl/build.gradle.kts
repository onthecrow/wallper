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
}
