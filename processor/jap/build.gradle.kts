plugins {
    id("daggerlite.artifact")
}

val autoCommonVersion: String by extra
val junitVersion: String by extra

dependencies {
    implementation(project(":api:public"))
    implementation(project(":processor:common"))
    implementation(project(":lang:jap"))
    implementation(kotlin("stdlib"))

    api("com.google.auto:auto-common:$autoCommonVersion")
}