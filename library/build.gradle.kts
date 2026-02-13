plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm()

    macosArm64()
    macosX64()
    linuxX64()

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
