import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// The Android target is only configured when an Android SDK is available
// (ANDROID_HOME / ANDROID_SDK_ROOT / local.properties with a platform installed),
// so the desktop (JVM) target can always be built and tested.
val androidSdkDir: String? = run {
    val props = Properties()
    val lp = rootProject.file("local.properties")
    if (lp.exists()) lp.inputStream().use { props.load(it) }
    listOf(
        props.getProperty("sdk.dir"),
        System.getenv("ANDROID_HOME"),
        System.getenv("ANDROID_SDK_ROOT"),
        System.getenv("LOCALAPPDATA")?.let { "$it/Android/Sdk" },
    ).filterNotNull().firstOrNull { File(it, "platforms").isDirectory && (File(it, "platforms").list()?.isNotEmpty() == true) }
}
val hasAndroid = androidSdkDir != null && System.getProperty("solfi.noAndroid") == null
if (hasAndroid) {
    apply(plugin = "com.android.application")
    apply(from = "android.gradle")
}

kotlin {
    if (hasAndroid) {
        androidTarget {
            compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    // Browser build (Kotlin/Wasm), published at https://fsoftt.github.io/solfi/app/
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "composeApp"
        browser {
            commonWebpackConfig { outputFileName = "composeApp.js" }
        }
        binaries.executable()
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
        if (hasAndroid) {
            val androidMain by getting {
                dependencies {
                    implementation(libs.androidx.activity.compose)
                    implementation(libs.androidx.core.ktx)
                }
            }
        }
    }
}

compose.resources {
    packageOfResClass = "com.solfi.app.resources"
    generateResClass = always
}

compose.desktop {
    application {
        mainClass = "com.solfi.app.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Solfi"
            packageVersion = "1.0.0"
        }
    }
}

// Copies the production web build to docs/app/ (served by GitHub Pages at /solfi/app/).
tasks.register<Sync>("publishWebApp") {
    group = "distribution"
    description = "Builds the browser app and copies it to docs/app"
    from(tasks.named("wasmJsBrowserDistribution")) { exclude("*.map") }
    into(rootProject.layout.projectDirectory.dir("docs/app"))
    // docs/app/ngl/ opens the same app with the NGL tab (the app looks at the address); <base> points the assets at docs/app/
    doLast {
        val out = rootProject.layout.projectDirectory.dir("docs/app").asFile
        val html = File(out, "index.html").readText().replaceFirst("<head>", "<head>\n<base href=\"../\">")
        File(out, "ngl").mkdirs()
        File(out, "ngl/index.html").writeText(html)
    }
}
