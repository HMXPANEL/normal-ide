package com.hmx.ide.ai.errors

sealed class AiException(message: String, cause: Throwable? = null) :
  Exception(message, cause)

class NetworkException(message: String, cause: Throwable? = null) :
  AiException(message, cause)

class AuthenticationException(message: String, cause: Throwable? = null) :
  AiException(message, cause)

class RateLimitException(message: String, cause: Throwable? = null) :
  AiException(message, cause)

class ModelNotFoundException(message: String, cause: Throwable? = null) :
  AiException(message, cause)

class ProviderException(message: String, cause: Throwable? = null) :
  AiException(message, cause)

class StreamInterruptedException(message: String, cause: Throwable? = null) :
  AiException(message, cause)

class ProviderConfigurationException(message: String, cause: Throwable? = null) :
  AiException(message, cause)
