plugins {
    id("wallper.android.library")
    id("wallper.android.compose")
    id("wallper.hilt")
}

android {
    namespace = "com.onthecrow.wallper.core.navigation.impl"
}

dependencies {
    api(projects.core.navigation.api)
    implementation(libs.androidx.navigation3.ui)
    // Per-entry ViewModelStore with a default factory, so hiltViewModel() and SavedStateHandle work.
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
