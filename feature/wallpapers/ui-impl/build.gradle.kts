plugins {
    id("wallper.android.library")
    id("wallper.android.compose")
    id("wallper.hilt")
}

android {
    namespace = "com.onthecrow.wallper.wallpapers.ui"
}

dependencies {
    implementation(projects.feature.wallpapers.uiApi)
    implementation(projects.feature.wallpapers.logicApi)
    implementation(projects.feature.editor.uiApi)
    implementation(projects.feature.editor.logicApi)
    implementation(projects.core.engine.api)
    implementation(projects.core.coroutines)
    implementation(projects.core.mvi)
    implementation(projects.core.navigation.api)
    implementation(projects.core.designsystem)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.coil)
    testImplementation(libs.kotlinx.coroutines.test)
}
