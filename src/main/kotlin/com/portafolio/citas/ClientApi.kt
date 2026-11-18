package com.portafolio.citas

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*

data class ClientRequest(
    @field:NotBlank @field:Size(max = 150) val name: String,
    @field:Email @field:Size(max = 255) val email: String? = null,
    @field:Size(max = 40) val phone: String? = null
)
data class ClientResponse(val id: Long, val name: String, val email: String?, val phone: String?)
private fun Client.response() = ClientResponse(id!!, name, email, phone)

@Service
class ClientService(private val clients: ClientRepository) {
    @Transactional
    fun create(input: ClientRequest): ClientResponse = clients.save(Client(name = input.name.trim(), email = input.email?.trim(), phone = input.phone?.trim())).response()
    @Transactional(readOnly = true)
    fun list(): List<ClientResponse> = clients.findAll().map { it.response() }
    @Transactional(readOnly = true)
    fun get(id: Long): ClientResponse = find(id).response()
    @Transactional
    fun update(id: Long, input: ClientRequest): ClientResponse {
        val client = find(id)
        client.name = input.name.trim()
        client.email = input.email?.trim()
        client.phone = input.phone?.trim()
        return client.response()
    }
    private fun find(id: Long): Client = clients.findById(id).orElse(null) ?: notFound("Cliente")
}

@RestController
@RequestMapping("/api/clients")
@SecurityRequirement(name = "bearerAuth")
class ClientController(private val service: ClientService) {
    @Operation(summary = "Registrar un cliente")
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody input: ClientRequest) = service.create(input)
    @Operation(summary = "Listar clientes")
    @GetMapping fun list() = service.list()
    @Operation(summary = "Consultar un cliente")
    @GetMapping("/{id}") fun get(@PathVariable id: Long) = service.get(id)
    @Operation(summary = "Actualizar los datos de un cliente", description = "Reemplaza nombre, correo y teléfono; los campos opcionales omitidos se eliminan.")
    @PutMapping("/{id}") fun update(@PathVariable id: Long, @Valid @RequestBody input: ClientRequest) = service.update(id, input)
}
