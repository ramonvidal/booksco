package com.punyplatipus.podouvir.data.network

import kotlinx.serialization.json.Json

/** Json used for all network parsing. */
val NetworkJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
