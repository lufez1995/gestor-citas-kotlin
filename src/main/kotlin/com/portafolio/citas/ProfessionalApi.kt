package com.portafolio.citas

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

data class ProfessionalRequest(@field:NotBlank @field:Size(max = 150) val name: String)
data class ProfessionalResponse(val id: Long, val name: String)
private fun Professional.response() = ProfessionalResponse(id!!, name)

@Service
class ProfessionalService(private val professionals: ProfessionalRepository) {
    @Transactional fun create(input: ProfessionalRequest): ProfessionalResponse = professionals.save(Professional(name = input.name.trim())).response()
    @Transactional(readOnly = true) fun list(): List<ProfessionalResponse> = professionals.findAll().map { it.response() }
    @Transactional(readOnly = true) fun get(id: Long): ProfessionalResponse = find(id).response()
    @Transactional fun update(id: Long, input: ProfessionalRequest): ProfessionalResponse {
        val professional = find(id)
        professional.name = input.name.trim()
        return professional.response()
    }
    private fun find(id: Long): Professional = professionals.findById(id).orElse(null) ?: notFound("Profesional")
}

@RestController
@RequestMapping("/api/professionals")
@SecurityRequirement(name = "bearerAuth")
class ProfessionalController(private val service: ProfessionalService) {
    @Operation(summary = "Registrar un profesional")
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody input: ProfessionalRequest) = service.create(input)
    @Operation(summary = "Listar profesionales")
    @GetMapping fun list() = service.list()
    @Operation(summary = "Consultar un profesional")
    @GetMapping("/{id}") fun get(@PathVariable id: Long) = service.get(id)
    @Operation(summary = "Actualizar un profesional")
    @PutMapping("/{id}") fun update(@PathVariable id: Long, @Valid @RequestBody input: ProfessionalRequest) = service.update(id, input)
}
