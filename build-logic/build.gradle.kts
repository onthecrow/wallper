plugins {
    `kotlin-dsl`
}

// Types only: the plugins themselves are put on the classpath by the root build.gradle.kts
// (`apply false`), so the conventions apply them by id.
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "wallper.android.application"
            implementationClass = "com.onthecrow.wallper.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "wallper.android.library"
            implementationClass = "com.onthecrow.wallper.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "wallper.android.compose"
            implementationClass = "com.onthecrow.wallper.buildlogic.AndroidComposeConventionPlugin"
        }
        register("androidRoom") {
            id = "wallper.android.room"
            implementationClass = "com.onthecrow.wallper.buildlogic.AndroidRoomConventionPlugin"
        }
        register("hilt") {
            id = "wallper.hilt"
            implementationClass = "com.onthecrow.wallper.buildlogic.HiltConventionPlugin"
        }
        register("jvmLibrary") {
            id = "wallper.jvm.library"
            implementationClass = "com.onthecrow.wallper.buildlogic.JvmLibraryConventionPlugin"
        }
    }
}
