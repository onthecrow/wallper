plugins {
    id("wallper.android.library")
    alias(libs.plugins.serialization)
}

android {
    namespace = "com.onthecrow.wallper.editor.ui.api"
}

dependencies {
    api(projects.core.navigation.api)
}
