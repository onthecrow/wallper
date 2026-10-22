plugins {
    id("wallper.android.library")
    id("wallper.hilt")
}

android {
    namespace = "com.onthecrow.wallper.core.player"
    // Tests hand fake engines real android.view.Surface objects; with stubbed android.jar they are inert.
    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    api(libs.media3.exoplayer)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.timber)
    testImplementation(libs.kotlinx.coroutines.test)
}
