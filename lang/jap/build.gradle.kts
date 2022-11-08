plugins {
    id("yatagan.artifact")
}

val autoCommonVersion: String by extra

dependencies {
    implementation(project(":base"))

    api(project(":lang:compiled"))

    implementation("com.google.auto:auto-common:$autoCommonVersion")

    implementation(kotlin("stdlib"))
}