plugins {
    id("wallper.android.library")
    id("wallper.hilt")
}

android {
    namespace = "com.onthecrow.wallper.editor.logic"
}

dependencies {
    api(projects.feature.editor.logicApi)
    implementation(projects.feature.wallpapers.logicApi)
    implementation(projects.core.coroutines)
    implementation(projects.core.storage)
    implementation(libs.androidx.core.ktx)
    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)
    implementation(libs.media3.common)
    implementation(libs.timber)
    testImplementation(libs.kotlinx.coroutines.test)
}
