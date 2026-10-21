plugins {
    id("wallper.android.library")
    id("wallper.hilt")
}

android {
    namespace = "com.onthecrow.wallper.core.storage"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.timber)
}
