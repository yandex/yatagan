plugins {
    id("yatagan.artifact")
}

dependencies {
    implementation(project(":api:public"))
    implementation(project(":processor:common"))
    implementation(project(":lang:jap"))
}