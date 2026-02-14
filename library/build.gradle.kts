plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.maven.publish)
}

// Maven coordinates
val mavenGroup = "io.github.baole"
val mavenArtifactId = "kmqtt5"
val mavenVersion = "1.2.0"

kotlin {
    jvm()

    // Apple
    macosX64(); macosArm64()
    iosArm64(); iosX64(); iosSimulatorArm64()
    tvosArm64(); tvosX64(); tvosSimulatorArm64()
    watchosArm32(); watchosArm64(); watchosX64(); watchosSimulatorArm64(); watchosDeviceArm64()

    // Linux & Windows
    linuxX64(); linuxArm64()
    mingwX64()

    // Android Native (NDK)
    androidNativeArm32(); androidNativeArm64(); androidNativeX64(); androidNativeX86()

    // JS & Wasm (Node.js only – raw TCP sockets are not available in browsers)
    js { nodejs() }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { nodejs() }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.network)
            implementation(libs.ktor.network.tls)
            implementation(libs.ktor.io)
            implementation(libs.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()

    // Configure signing to work with both local keyring and GitHub Actions in-memory keys
    signAllPublications()

    coordinates(mavenGroup, mavenArtifactId, mavenVersion)

    pom {
        name = "KMQTT5"
        description = "Kotlin Multiplatform MQTT v5.0 client library using Ktor for networking"
        inceptionYear = "2025"
        url = "https://github.com/baole/kmqtt5"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "baole"
                name = "Bao Le"
                email = "leducbao@gmail.com"
            }
        }
        scm {
            url = "https://github.com/baole/kmqtt5"
            connection = "scm:git:git://github.com/baole/kmqtt5.git"
            developerConnection = "scm:git:ssh://git@github.com/baole/kmqtt5.git"
        }
    }
}
