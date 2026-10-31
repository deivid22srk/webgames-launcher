pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // espelhos completos do Central (resolvem 429 intermitente)
        maven("https://maven.aliyun.com/repository/public")
        maven("https://repo.huaweicloud.com/repository/maven")
        mavenCentral()
        // fallback resiliente (espelho do portal; resolve 429 do Central)
        maven("https://plugins.gradle.org/m2/")
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // espelhos completos do Central (resolvem 429 intermitente)
        maven("https://maven.aliyun.com/repository/public")
        maven("https://repo.huaweicloud.com/repository/maven")
        mavenCentral()
        maven("https://plugins.gradle.org/m2/") // fallback
    }
}

rootProject.name = "webgames-launcher"
include(":app")
