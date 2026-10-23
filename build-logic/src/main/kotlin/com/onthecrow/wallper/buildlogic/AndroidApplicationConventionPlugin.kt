package com.onthecrow.wallper.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * `wallper.android.application` — the `:app` module.
 *
 * AGP 9 has Kotlin built in, so `org.jetbrains.kotlin.android` is deliberately not applied.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")

        extensions.configure<ApplicationExtension> {
            compileSdk = libs.version("android-compileSdk")
            defaultConfig {
                minSdk = libs.version("android-minSdk")
                targetSdk = libs.version("android-targetSdk")
            }
            compileOptions {
                sourceCompatibility = JAVA_VERSION
                targetCompatibility = JAVA_VERSION
            }
            lint { configureLint(target) }
        }

        registerUnitTestTask(testTask = "testDebugUnitTest")
    }
}
