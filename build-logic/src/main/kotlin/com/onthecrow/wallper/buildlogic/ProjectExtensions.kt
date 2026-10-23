package com.onthecrow.wallper.buildlogic

import com.android.build.api.dsl.Lint
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

internal val JAVA_VERSION = JavaVersion.VERSION_17

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): Int =
    findVersion(alias).get().requiredVersion.toInt()

internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).get()

/**
 * `unitTest` runs a module's JVM unit tests whatever its kind: `test` in plain Kotlin modules,
 * `testDebugUnitTest` in Android ones. `./gradlew unitTest` runs them all.
 */
internal fun Project.registerUnitTestTask(testTask: String) {
    tasks.register("unitTest") {
        group = "verification"
        description = "Runs the unit tests of this module."
        dependsOn(testTask)
    }
}

/**
 * Warnings fail the build; the ones from before this rule live in each module's lint-baseline.xml.
 * Checks that depend on the date (a newer library, a newer Android) are off: as warnings they would fail
 * the build on a release elsewhere. Dependabot and the targetSdk bump take care of those.
 */
internal fun Lint.configureLint(project: Project) {
    warningsAsErrors = true
    baseline = project.file("lint-baseline.xml")
    disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
}
