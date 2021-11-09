import java.util.Properties

plugins {
    id("daggerlite.base-module")
    `maven-publish`
}

version = "0.0.1-SNAPSHOT"
group = "com.yandex.daggerlite"

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf(File::isFile)?.reader()?.use(this::load)
}

publishing {
    publications {
        create<MavenPublication>(name) {
            from(components["java"])
        }

        repositories {
            localProperties["publishing.custom-url"]?.let { customUrl ->
                maven {
                    name = "Custom"
                    url = uri(customUrl)
                }
            }
        }
    }
}
