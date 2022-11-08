plugins {
    id("yatagan.artifact")
}

dependencies {
    api(project(":lang:common"))

    implementation(project(":api:public"))
    implementation(project(":base"))

    implementation(kotlin("stdlib"))
}