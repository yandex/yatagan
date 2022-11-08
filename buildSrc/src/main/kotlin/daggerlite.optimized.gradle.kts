import com.yandex.yatagan.gradle.ComponentFactoryProvider
import com.yandex.yatagan.gradle.copyFrom
import org.gradle.jvm.tasks.Jar
import proguard.gradle.ProGuardTask

plugins {
    id("daggerlite.base-module")
}

if (pluginManager.hasPlugin("daggerlite.artifact")) {
    throw GradleException("`daggerlite.optimized` must be applied before `daggerlite.artifact`.")
}

val flatApi: Configuration by configurations.creating {
    isTransitive = false
}

val flatImplementation: Configuration by configurations.creating {
    isTransitive = false
}

val libApi: Configuration by configurations.creating {
    isTransitive = true
}

configurations {
    compileClasspath {
        extendsFrom(flatApi, flatImplementation, libApi)
    }

    runtimeElements {
        extendsFrom(flatApi, flatImplementation, libApi)
    }

    apiElements {
        extendsFrom(flatApi, libApi)
    }
}

// Outgoing configuration
val optimizedRuntimeElements: Configuration by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false

    extendsFrom(flatApi, libApi)
    attributes {
        copyFrom(configurations.runtimeElements.get().attributes)
    }
}

val unoptimizedJar = tasks.named<Jar>("jar") {
    // Explicitly override default classifier to eliminate possible confusion
    archiveClassifier.set("unoptimized")
}

val optimizedJar = layout.buildDirectory.file("libs/lib-optimized.jar")

val proguard by tasks.registering(ProGuardTask::class) {
    injars(unoptimizedJar.map { it.archiveFile })
    injars(flatImplementation)
    outjars(optimizedJar)
    libraryjars(flatApi)
    libraryjars(libApi)
    // Assume JDK9+
    libraryjars("${System.getProperty("java.home")}/jmods/java.base.jmod")
    configuration("proguard.txt")

    dependsOn(unoptimizedJar)
}

artifacts {
    add("optimizedRuntimeElements", optimizedJar) {
        builtBy(proguard)
    }
}

components += objects.newInstance(ComponentFactoryProvider::class.java)
    .softwareComponentFactory
    .adhoc("optimizedJava").apply {
        addVariantsFromConfiguration(optimizedRuntimeElements) {
            mapToMavenScope("runtime")
        }
    }

tasks.check {
    // Check proguard is configured properly
    dependsOn(proguard)
}