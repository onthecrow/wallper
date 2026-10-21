plugins {
    id("wallper.android.library")
    id("wallper.android.compose")
}

android {
    namespace = "com.onthecrow.wallper.core.designsystem"
}

dependencies {
    implementation(libs.androidx.core.ktx)
}
