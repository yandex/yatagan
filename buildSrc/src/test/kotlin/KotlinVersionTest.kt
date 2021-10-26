import java.io.File
import java.util.Properties
import kotlin.test.Test
import kotlin.test.expect

class KotlinVersionTest {
    @Test
    fun `kotlin version is consistent`() {
        val buildSrcProps = Properties().apply {
            load(File("gradle.properties").inputStream())
        }
        val projectProps = Properties().apply {
            load(File("../gradle.properties").inputStream())
        }
        expect(buildSrcProps["daggerlite.kotlin.version"]) {
            projectProps["daggerlite.kotlin.version"]
        }
    }
}