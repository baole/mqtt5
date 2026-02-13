plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    mainClass.set("MainKt")
}

dependencies {
    implementation(project(":library"))
    implementation(libs.coroutines.core)
    implementation(libs.ktor.network)
    implementation(libs.ktor.io)
}
