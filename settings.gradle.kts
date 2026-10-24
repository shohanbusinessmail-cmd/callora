pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Callora"

include(":app")
include(":core:model")
include(":core:common")
include(":core:ui")
include(":core:telephony")
include(":core:forwarding")
include(":core:storage")
include(":feature:dashboard")
include(":feature:sims")
include(":feature:profiles")
include(":feature:history")
include(":feature:settings")
include(":feature:help")
