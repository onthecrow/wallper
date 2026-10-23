plugins {
    id("wallper.android.library")
}

android {
    namespace = "com.onthecrow.wallper.core.engine"
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)
}
