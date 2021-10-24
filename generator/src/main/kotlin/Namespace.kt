package com.yandex.daggerlite.generator

import com.yandex.daggerlite.generator.lang.ClassNameModel
import kotlin.random.Random

internal class Namespace(
    private val prefix: String = "",
) {
    private val names = hashSetOf<String>()

    fun name(vararg parts: Any): String {
        // MAYBE: This looks scary, probably simplify it, provide single argument overload or smth
        require(parts.isNotEmpty())
        val variantGenerators = Array(parts.size + 1) { index ->
            if (index < parts.size) {
                when (val name = parts[index]) {
                    is ClassNameModel -> iterator {
                        // Use only simple name
                        yield(name.simpleNames.last())

                        // Use all simple names (works only if nested class)
                        var extended = name.simpleNames.joinToString(separator = "_")
                        if (name.simpleNames.size > 1) {
                            yield(extended)
                        }

                        // Gradually use package name parts.
                        name.packageName.split('.').asReversed().forEach { part ->
                            extended = part + "_" + extended
                            yield(extended)
                        }
                    }
                    is String -> iterator { yield(name) }
                    else -> throw IllegalArgumentException("$name has unsupported type: ${name.javaClass.name}")
                }
            } else {
                // unique guarantee part
                iterator {
                    yield("")  // default - no hash suffix
                    val rnd = Random(parts.contentHashCode())
                    while (true) {
                        yield((rnd.nextInt() % 100).toString())
                    }
                }
            }
        }

        val nameGenerator = iterator {
            // Initial combination
            val partVariants = Array(variantGenerators.size) { index ->
                variantGenerators[index].next()
            }
            yield(partVariants.joinToString(separator = ""))

            variantGenerators.forEachIndexed { index, variant ->
                while (variant.hasNext()) {
                    partVariants[index] = variant.next()
                }
                yield(partVariants.joinToString(separator = ""))
            }
        }

        var name: String
        do {
            name = nameGenerator.next()
        } while (name in names)
        return prefix + name
    }
}
