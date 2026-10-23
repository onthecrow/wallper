plugins {
    id("wallper.android.library")
    alias(libs.plugins.serialization)
}

android {
    namespace = "com.onthecrow.wallper.settings.ui.api"
}

dependencies {
    api(projects.core.navigation.api)
}
