package com.mapsupervision.core.error

open class AppException(message: String, cause: Throwable? = null) : Exception(message, cause)

class ImportException(message: String, cause: Throwable? = null) : AppException(message, cause)
class StorageException(message: String, cause: Throwable? = null) : AppException(message, cause)
class AiException(message: String, cause: Throwable? = null) : AppException(message, cause)
class DatabaseException(message: String, cause: Throwable? = null) : AppException(message, cause)
class ValidationException(message: String, cause: Throwable? = null) : AppException(message, cause)
