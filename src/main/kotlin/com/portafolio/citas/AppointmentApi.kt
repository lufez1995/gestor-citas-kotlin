package com.portafolio.citas

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

data class AppointmentRequest(
    @field:Positive val clientId: Long,
    @field:Positive val professionalId: Long,
    @field:Schema(description = "Fecha y hora UTC sin sufijo de zona", example = "2099-05-01T15:00:00") val startsAt: LocalDateTime,
    @field:Schema(description = "Fecha y hora UTC sin sufijo de zona", example = "2099-05-01T16:00:00") val endsAt: LocalDateTime
)
data class RescheduleRequest(
    @field:Schema(description = "Nueva fecha y hora UTC", example = "2099-05-01T16:00:00") val startsAt: LocalDateTime,
    @field:Schema(description = "Nueva fecha y hora UTC de finalización", example = "2099-05-01T17:00:00") val endsAt: LocalDateTime
)
data class AppointmentResponse(val id: Long, val clientId: Long, val professionalId: Long,
                               val startsAt: LocalDateTime, val endsAt: LocalDateTime, val status: AppointmentStatus)
private fun Appointment.response() = AppointmentResponse(id!!, client.id!!, professional.id!!, startsAt, endsAt, status)

@Service
class AppointmentService(private val appointments: AppointmentRepository, private val clients: ClientRepository,
                         private val professionals: ProfessionalRepository) {
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun create(input: AppointmentRequest): AppointmentResponse {
        validateTimes(input.startsAt, input.endsAt)
        val client = clients.findById(input.clientId).orElse(null) ?: notFound("Cliente")
        val professional = professionals.findByIdForUpdate(input.professionalId) ?: notFound("Profesional")
        ensureAvailable(input.professionalId, input.startsAt, input.endsAt)
        return appointments.save(Appointment(client = client, professional = professional, startsAt = input.startsAt, endsAt = input.endsAt)).response()
    }

    @Transactional(readOnly = true)
    fun list(date: LocalDate?): List<AppointmentResponse> = (if (date == null) appointments.findAllByOrderByStartsAtAsc()
        else appointments.findAllByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(date.atStartOfDay(), date.plusDays(1).atStartOfDay())).map { it.response() }

    @Transactional(readOnly = true)
    fun get(id: Long): AppointmentResponse = (appointments.findById(id).orElse(null) ?: notFound("Cita")).response()

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun reschedule(id: Long, input: RescheduleRequest): AppointmentResponse {
        validateTimes(input.startsAt, input.endsAt)
        val appointment = lockAppointment(id)
        if (appointment.status != AppointmentStatus.SCHEDULED)
            throw ResponseStatusException(HttpStatus.CONFLICT, "No se puede reprogramar una cita cancelada")
        ensureAvailable(appointment.professional.id!!, input.startsAt, input.endsAt, id)
        appointment.startsAt = input.startsAt
        appointment.endsAt = input.endsAt
        return appointment.response()
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun cancel(id: Long): AppointmentResponse {
        val appointment = lockAppointment(id)
        appointment.status = AppointmentStatus.CANCELLED
        return appointment.response()
    }

    // All writes lock the professional first, so bookings, cancellations and moves serialize.
    private fun lockAppointment(id: Long): Appointment {
        val professionalId = appointments.findProfessionalId(id) ?: notFound("Cita")
        professionals.findByIdForUpdate(professionalId) ?: notFound("Profesional")
        return appointments.findByIdForUpdate(id) ?: notFound("Cita")
    }

    private fun validateTimes(startsAt: LocalDateTime, endsAt: LocalDateTime) {
        if (!startsAt.isAfter(LocalDateTime.now(ZoneOffset.UTC)) || !endsAt.isAfter(startsAt))
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "La cita debe empezar en el futuro y terminar después de su inicio (UTC)")
    }

    private fun ensureAvailable(professionalId: Long, startsAt: LocalDateTime, endsAt: LocalDateTime, excludedId: Long = 0) {
        if (appointments.hasOverlap(professionalId, startsAt, endsAt, excludedId))
            throw ResponseStatusException(HttpStatus.CONFLICT, "El profesional ya tiene una cita en ese horario")
    }
}

@RestController
@RequestMapping("/api/appointments")
@SecurityRequirement(name = "bearerAuth")
class AppointmentController(private val service: AppointmentService) {
    @Operation(summary = "Agendar una cita", description = "Las fechas se interpretan en UTC. Devuelve 409 si el profesional ya está ocupado.")
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody input: AppointmentRequest) = service.create(input)
    @Operation(summary = "Consultar citas", description = "El filtro opcional date selecciona las citas que comienzan ese día UTC.")
    @GetMapping fun list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?) = service.list(date)
    @Operation(summary = "Consultar una cita")
    @GetMapping("/{id}") fun get(@PathVariable id: Long) = service.get(id)
    @Operation(summary = "Reprogramar una cita", description = "Conserva cliente y profesional; requiere horario futuro disponible y una cita activa.")
    @PatchMapping("/{id}/reschedule") fun reschedule(@PathVariable id: Long, @Valid @RequestBody input: RescheduleRequest) = service.reschedule(id, input)
    @Operation(summary = "Cancelar una cita", description = "Libera el horario. Cancelar de nuevo devuelve la cita cancelada.")
    @PatchMapping("/{id}/cancel") fun cancel(@PathVariable id: Long) = service.cancel(id)
}
