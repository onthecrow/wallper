package com.onthecrow.wallper.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency

/** Allowed layer names of a feature module: `:feature:<name>:<layer>`. */
private val FEATURE_LAYERS = setOf("logic-api", "logic-impl", "ui-api", "ui-impl")

/** The only layers one feature may see of another one. */
private val CROSS_FEATURE_LAYERS = setOf("logic-api", "ui-api")

/**
 * Modules the wallpaper service is built from. They must stay free of Compose and the design system:
 * the service has no UI and starts in direct boot.
 */
private val UI_FREE_MODULES = setOf(
    ":core:model",
    ":core:diagnostics",
    ":core:coroutines",
    ":core:storage",
    ":core:gl",
    ":core:player",
    ":core:engine:api",
    ":core:engine:impl",
    ":core:blackhole:physics",
    ":core:blackhole:renderer",
)

/**
 * Fails the configuration when a module breaks the dependency rules from CLAUDE.md:
 * - a feature layer has an unknown name;
 * - a `logic-api` module is an Android module (it is plain Kotlin);
 * - anything but `:app` depends on an impl module;
 * - a feature depends on another feature's `-impl`;
 * - a logic layer depends on a UI layer;
 * - `core` depends on `feature`;
 * - a UI-free module depends on Compose or `:core:designsystem`.
 */
internal fun Project.enforceModuleBoundaries() {
    val segments = path.segments()
    val group = segments.firstOrNull()
    val layer = segments.lastOrNull()

    if (group == "feature" && (segments.size != 3 || layer !in FEATURE_LAYERS)) {
        throw GradleException("'$path': a feature module must be :feature:<name>:<one of $FEATURE_LAYERS>")
    }

    if (layer == "logic-api" && pluginManager.hasPlugin("com.android.library")) {
        throw GradleException("'$path': a logic-api module is plain Kotlin; apply wallper.jvm.library")
    }

    val isLogic = group == "feature" && layer?.startsWith("logic-") == true
    val isUiFree = path in UI_FREE_MODULES

    afterEvaluate {
        configurations.forEach { configuration ->
            configuration.dependencies.withType(ProjectDependency::class.java).forEach { dependency ->
                val depPath = dependency.path
                // Test configurations depend on the module itself.
                if (depPath == path) return@forEach
                val depSegments = depPath.segments()
                val depLayer = depSegments.lastOrNull()
                fun fail(rule: String): Nothing =
                    throw GradleException("'$path' -> '$depPath' (${configuration.name}): $rule")

                if (group == "core" && depSegments.firstOrNull() == "feature") {
                    fail("core must not depend on features; declare a port in core and implement it in the feature")
                }
                if (group == "feature" && depSegments.firstOrNull() == "feature" &&
                    depSegments[1] != segments[1] && depLayer !in CROSS_FEATURE_LAYERS
                ) {
                    fail("a feature may see another feature only through $CROSS_FEATURE_LAYERS")
                }
                if (path != ":app" && (depLayer == "impl" || depLayer?.endsWith("-impl") == true)) {
                    fail("only :app may depend on an impl module; depend on its api")
                }
                if (isLogic && depSegments.firstOrNull() == "feature" && depLayer?.startsWith("ui-") == true) {
                    fail("a logic layer must not depend on a UI layer")
                }
                if (isUiFree && depPath == ":core:designsystem") {
                    fail("UI-free module must not depend on the design system")
                }
            }
            if (isUiFree) {
                configuration.dependencies.withType(ExternalModuleDependency::class.java).forEach { dependency ->
                    if (dependency.group?.startsWith("androidx.compose") == true) {
                        throw GradleException(
                            "'$path' (${configuration.name}): UI-free module must not depend on Compose " +
                                "(${dependency.group}:${dependency.name})",
                        )
                    }
                }
            }
        }
    }
}

private fun String.segments() = split(":").filter(String::isNotEmpty)
