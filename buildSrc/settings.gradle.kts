// This file is also applied in root `settings.gradle.kts` script.
// Only version catalog declaration is allowed here, don't do other things here

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}