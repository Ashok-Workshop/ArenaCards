pluginManagement {
    repositories {
        google()
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

rootProject.name = "ArenaCards"
include(":app")
include(":core:designsystem")
include(":core:navigation")
include(":core:common")
include(":core:network")
include(":core:security")
include(":data")
include(":domain")
include(":feature:auth")
include(":feature:onboarding")
include(":feature:home")
include(":feature:profile")
include(":feature:wallet")
include(":feature:game")
include(":feature:offline")
include(":feature:multiplayer")
include(":feature:friends")
include(":feature:chat")
include(":feature:leaderboard")
include(":feature:settings")
include(":feature:notifications")
