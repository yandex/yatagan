plugins {
    id("yatagan.base-module")
    application
}

dependencies {
    implementation(libs.kotlinx.cli)
    implementation(libs.poets.kotlin)
}

application {
    mainClass.set("com.yandex.yatagan.testing.procedural.Standalone")
}