package com.yandex.yatagan.rt.support

import com.yandex.yatagan.validation.RichString
import java.lang.RuntimeException
import kotlin.system.measureTimeMillis

/**
 * A default implementation for a [DynamicValidationDelegate],
 * which runs validation operations eagerly and synchronously.
 *
 * @param reporting a reporting delegate to use.
 * @param logger a logger to use for additional info logging.
 * @param throwOnError `true` if implementation should raise an [InvalidGraphException]
 *   if validation found at least one error
 * @param usePlugins Whether to search classpath for Yatagan validation plugins.
 *   Bear in mind, that loading plugins can be costly for large classpaths, and may impact startup performance if
 *   used synchronously.
 */
class SimpleDynamicValidationDelegate @JvmOverloads constructor(
    private val reporting: DynamicValidationDelegate.ReportingDelegate,
    private val logger: Logger? = null,
    private val throwOnError: Boolean = true,
    override val usePlugins: Boolean = false,
) : DynamicValidationDelegate {

    /**
     * Uses [logger]-backed reporting implementation.
     */
    @JvmOverloads constructor(
        logger: Logger = ConsoleLogger(),
        throwOnError: Boolean = true,
        usePlugins: Boolean = false,
    ) : this(
        reporting = LoggerReporting(logger),
        logger = logger,
        throwOnError = throwOnError,
        usePlugins = usePlugins,
    )

    /**
     * May be raised when graph is invalid.
     */
    class InvalidGraphException(title: String) : RuntimeException() {
        override val message: String = "Validation for $title detected error(s), consult reported results for details"
    }

    override fun dispatchValidation(
        title: String,
        operation: DynamicValidationDelegate.Operation,
    ): DynamicValidationDelegate.Promise? {
        val reporting = ReportingWrapper(reporting)
        val time = measureTimeMillis {
            operation.validate(reporting)
        }
        logger?.log("Validation for $title yielded ${reporting.errorCount} error(s), " +
                "${reporting.warningCount} warning(s) and took $time ms")
        if (throwOnError && reporting.errorCount > 0) {
            throw InvalidGraphException(title)
        }
        return null
    }

    private class ReportingWrapper(
        private val delegate: DynamicValidationDelegate.ReportingDelegate,
    ) : DynamicValidationDelegate.ReportingDelegate {
        var errorCount = 0
        var warningCount = 0

        override fun reportError(message: RichString) {
            errorCount += 1
            delegate.reportError(message)
        }

        override fun reportWarning(message: RichString) {
            warningCount += 1
            delegate.reportWarning(message)
        }
    }
}
