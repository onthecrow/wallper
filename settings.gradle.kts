rootProject.name = "Wallper"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":app")
include(":core:model")
include(":core:diagnostics")
include(":core:coroutines")
include(":core:mvi")
include(":core:navigation:api")
include(":core:navigation:impl")
include(":core:designsystem")
include(":core:resources")
include(":core:storage")
include(":core:engine:api")
include(":feature:wallpapers:logic-api")
include(":feature:wallpapers:logic-impl")
include(":core:player")
include(":core:gl")
include(":core:engine:impl")
include(":core:blackhole:physics")
include(":core:blackhole:renderer")
include(":feature:wallpapers:ui-api")
include(":feature:wallpapers:ui-impl")
include(":feature:editor:logic-api")
include(":feature:editor:logic-impl")
include(":feature:editor:ui-api")
include(":feature:editor:ui-impl")
include(":feature:blackhole:ui-api")
include(":feature:blackhole:ui-impl")
