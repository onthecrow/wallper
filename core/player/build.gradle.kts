plugins {
    id("wallper.android.library")
    id("wallper.hilt")
}

android {
    namespace = "com.onthecrow.wallper.core.player"
}

dependencies {
    api(libs.media3.exoplayer)
    implementation(libs.androidx.core.ktx)
    implementation(libs.timber)
}
