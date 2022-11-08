# Module validation-api

Contains general model validation API.

Central API element is [com.yandex.yatagan.validation.MayBeInvalid], which should be extended by models that
which to report their validity state.

Besides being used in internal DL validation pipeline,
the module is publicly available as a part of [:spi] interface.  