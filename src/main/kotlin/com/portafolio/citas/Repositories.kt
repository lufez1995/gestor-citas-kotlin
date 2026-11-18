package com.portafolio.citas

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface UserRepository : JpaRepository<AppUser, Long> {
    fun findByEmail(email: String): AppUser?
}
interface ClientRepository : JpaRepository<Client, Long>
interface ProfessionalRepository : JpaRepository<Professional, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Professional p where p.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Professional?
}
interface AppointmentRepository : JpaRepository<Appointment, Long> {
    @Query("""select count(a) > 0 from Appointment a where a.professional.id = :professionalId
        and a.status = com.portafolio.citas.AppointmentStatus.SCHEDULED
        and a.id <> :excludedId
        and a.startsAt < :endsAt and a.endsAt > :startsAt""")
    fun hasOverlap(@Param("professionalId") professionalId: Long, @Param("startsAt") startsAt: LocalDateTime,
                   @Param("endsAt") endsAt: LocalDateTime, @Param("excludedId") excludedId: Long): Boolean

    @Query("select a.professional.id from Appointment a where a.id = :id")
    fun findProfessionalId(@Param("id") id: Long): Long?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Appointment?

    fun findAllByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(from: LocalDateTime, to: LocalDateTime): List<Appointment>
    fun findAllByOrderByStartsAtAsc(): List<Appointment>
}
