package com.portafolio.citas

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityScheme
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.server.ResponseStatusException

@Configuration
@OpenAPIDefinition(info = Info(title = "Gestor de citas", version = "1.0", description = "API para administrar clientes, profesionales y citas de un negocio. Las fechas se expresan en UTC."))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
class OpenApiConfig

internal fun notFound(what: String): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, "$what no encontrado")

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(ResponseStatusException::class)
    fun status(ex: ResponseStatusException): ProblemDetail = ProblemDetail.forStatusAndDetail(ex.statusCode, ex.reason ?: "Error en la solicitud")

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(ex: MethodArgumentNotValidException): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Hay campos inválidos")
        problem.setProperty("errors", ex.bindingResult.fieldErrors.map { mapOf("field" to it.field, "message" to (it.defaultMessage ?: "valor inválido")) })
        return problem
    }

    @ExceptionHandler(HttpMessageNotReadableException::class, MethodArgumentTypeMismatchException::class)
    fun invalidInput(): ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "El formato de la solicitud es inválido")
}
