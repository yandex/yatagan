# Module validation-spi

Contains classes for Extending Yatagan validation routines via Service Provider Interface.

Clients should implement [com.yandex.yatagan.validation.spi.ValidationPluginProvider] extension point,
and make implementations available on the annotation processing classpath for them to be picked up.

Plugins API is based on [:validation] model.

## Available API for plugins

SPI currently provides full Yatagan model API: [:lang] -> [:core] -> [:graph].