import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ABIs suportadas (arm64 obrigatório; x86_64 para emulador; armeabi-v7a opcional)
val rustTargets = providers.gradleProperty("rustTargets").orElse("arm64-v8a,x86_64").get()
val rustTriples = mapOf(
    "arm64-v8a" to "aarch64-linux-android",
    "x86_64" to "x86_64-linux-android",
    "armeabi-v7a" to "armv7-linux-androideabi",
)
val rustJniLibs = layout.buildDirectory.dir("rust/jniLibs")
val skipRust = providers.environmentVariable("SKIP_RUST").orElse("").get() == "1"
val cargoBin = System.getenv("CARGO_HOME")?.let { File(it, "bin").absolutePath }
    ?: File(System.getProperty("user.home"), ".cargo/bin").absolutePath
val ndkHome = System.getenv("ANDROID_NDK_HOME")
    ?: File(
        System.getenv("ANDROID_HOME") ?: File(System.getProperty("user.home"), "android-sdk").absolutePath,
        "ndk/${libs.versions.ndk.get()}",
    ).absolutePath

android {
    namespace = "com.deivid22srk.webgames"
    compileSdk = libs.versions.compileSdk.get().toInt()
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        applicationId = "com.deivid22srk.webgames"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 2
        versionName = "0.1.2"

        ndk {
            abiFilters += rustTargets.split(",").filter { rustTriples.containsKey(it.trim()) }
        }
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        val ksPath = System.getenv("CI_KEYSTORE_PATH")
        val ksPass = System.getenv("CI_KEYSTORE_PASSWORD")
        val ksAlias = System.getenv("CI_KEY_ALIAS")
        if (ksPath != null && ksPass != null && File(ksPath).exists()) {
            create("ciRelease") {
                storeFile = File(ksPath)
                storePassword = ksPass
                keyAlias = ksAlias ?: "webgames"
                keyPassword = System.getenv("CI_KEY_PASSWORD") ?: ksPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("ciRelease") ?: signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

// ---- integração Rust (cargo-ndk) ----
val buildRustTasks = rustTargets.split(",").mapNotNull { abiRaw ->
    val abi = abiRaw.trim()
    val triple = rustTriples[abi] ?: return@mapNotNull null
    tasks.register<Exec>("buildRust${abi.replace("-", "_")}") {
        group = "rust"
        description = "Compila libwebgames_core.so para $abi via cargo-ndk"
        workingDir = rootProject.projectDir
        executable = File(cargoBin, "cargo").absolutePath
        args(
            "ndk", "-t", triple,
            "-o", rustJniLibs.get().asFile.absolutePath,
            "build", "--release", "-p", "webgames-core",
        )
        environment("ANDROID_NDK_HOME", ndkHome)
        environment("PATH", "$cargoBin:${System.getenv("PATH") ?: ""}")
        outputs.dir(rustJniLibs.map { it.dir(abi) })
        onlyIf { !skipRust }
    }
}
if (!skipRust) {
    tasks.named("preBuild") { dependsOn(buildRustTasks) }
}
android {
    sourceSets["main"].jniLibs.srcDir(rustJniLibs.get().asFile)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.windowsizeclass)
    implementation(libs.compose.material.icons.core)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
