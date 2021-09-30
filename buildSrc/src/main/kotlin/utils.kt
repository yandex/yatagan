import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainSpec

fun JavaToolchainSpec.set11Jdk() {
    languageVersion.set(JavaLanguageVersion.of(11))
}

