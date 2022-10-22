# Module validation-spi

Contains classes for Extending DL validation routines via Service Provider Interface.

Clients should implement [com.yandex.daggerlite.spi.ValidationPluginProvider] extension point,
and make implementations available on the annotation processing classpath for them to be picked up.

Plugins API is based on [:validation] model.

## Available API for plugins

SPI currently provides full DL model API: [:lang] -> [:core] -> [:graph].