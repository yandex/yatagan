plugins {
    id("yatagan.artifact")
}

dependencies {
    api(project(":codegen:poetry:api"))
    implementation(project(":lang:compiled"))

    implementation(libs.poets.java)
}