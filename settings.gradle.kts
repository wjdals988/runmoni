pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google { content { excludeGroup("com.google.devtools.ksp") } }
        mavenCentral()
        maven("https://repository.map.naver.com/archive/maven") { content { includeGroup("com.naver.maps") } }
        maven("https://jitpack.io") { content { includeGroup("com.github.davidliu") } }
    }
}
rootProject.name = "Runmoni"
include(":app")
