// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import org.jetbrains.annotations.ApiStatus
import java.net.URI

@ApiStatus.Internal
object OtlpConfiguration {

  @JvmStatic
  fun isTraceEnabled(): Boolean {
    val endpoint = System.getenv("OTLP_ENDPOINT") ?: System.getProperty("idea.diagnostic.opentelemetry.otlp")
    return normalizeTraceEndpoint(endpoint) != null
  }

  @JvmStatic
  fun getTraceEndpoint(): String? {
    if (System.getProperty(OpenTelemetryUtils.RDCT_TRACING_DIAGNOSTIC_FLAG) != null) {
      return null // this dependency is strange, but it is api
    }

    val endpoint = System.getenv("OTLP_ENDPOINT") ?: System.getProperty("idea.diagnostic.opentelemetry.otlp")
    return normalizeTraceEndpoint(endpoint)
  }

  @JvmStatic
  fun getTraceEndpointURI(): URI? {
    return getTraceEndpoint()?.let {
      try {
        URI.create(it)
      }
      catch (_: Exception) {
        null
      }
    }
  }

  private fun normalizeTraceEndpoint(value: String?): String? {
    var endpoint = value?.takeIf(String::isNotEmpty) ?: return null
    endpoint = if (endpoint == "true") "http://127.0.0.1:4318" else endpoint.removeSuffix("/")
    return "$endpoint/v1/traces"
  }
}