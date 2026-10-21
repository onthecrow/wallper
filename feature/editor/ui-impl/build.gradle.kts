plugins {
    id("wallper.android.library")
    id("wallper.android.compose")
    id("wallper.hilt")
}

android {
    namespace = "com.onthecrow.wallper.editor.ui"
}

dependencies {
    implementation(projects.feature.editor.uiApi)
    implementation(projects.feature.editor.logicApi)
    implementation(projects.core.coroutines)
    implementation(projects.core.mvi)
    implementation(projects.core.navigation.api)
    implementation(projects.core.designsystem)
    implementation(projects.core.player)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.window)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.timber)
    testImplementation(libs.kotlinx.coroutines.test)
}
