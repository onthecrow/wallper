plugins {
    id("wallper.android.library")
}

android {
    namespace = "com.onthecrow.wallper.core.mvi"
}

dependencies {
    api(libs.androidx.lifecycle.viewmodel)
    api(libs.kotlinx.coroutines.core)
}
