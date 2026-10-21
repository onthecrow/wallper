package com.onthecrow.wallper.buildlogic

import androidx.room.gradle.RoomExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * `wallper.android.room` — Room with exported schemas in `<module>/schemas`.
 * Once the app is released every schema change needs a migration and a migration test.
 */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("androidx.room")

        extensions.configure<RoomExtension> {
            schemaDirectory("$projectDir/schemas")
        }

        dependencies {
            add("implementation", libs.library("room-runtime"))
            add("implementation", libs.library("room-ktx"))
            add("ksp", libs.library("room-compiler"))
        }
    }
}
