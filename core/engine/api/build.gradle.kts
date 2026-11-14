plugins {
    id("wallper.android.library")
}

android {
    namespace = "com.onthecrow.wallper.core.engine"
}

dependencies {
    api(projects.core.model)
    // A series' slide is handed the GL host its renderer runs on (SlideStart).
    api(projects.core.gl)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
}
