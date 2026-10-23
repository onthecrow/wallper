package com.onthecrow.wallper.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * `wallper.android.library` — every Android library module. `namespace` is set per module,
 * Compose is opt-in via `wallper.android.compose`.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")

        extensions.configure<LibraryExtension> {
            compileSdk = libs.version("android-compileSdk")
            defaultConfig {
                minSdk = libs.version("android-minSdk")
            }
            compileOptions {
                sourceCompatibility = JAVA_VERSION
                targetCompatibility = JAVA_VERSION
            }
            lint { configureLint(target) }
        }

        registerUnitTestTask(testTask = "testDebugUnitTest")

        dependencies {
            add("testImplementation", libs.library("kotlin-test"))
            add("testImplementation", libs.library("junit"))
        }

        enforceModuleBoundaries()
    }
}
