package com.portafolio.citas

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "app_users")
class AppUser(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(nullable = false, unique = true) var email: String = "",
    @Column(name = "password_hash", nullable = false) var passwordHash: String = ""
)

@Entity
@Table(name = "clients")
class Client(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(nullable = false, length = 150) var name: String = "",
    var email: String? = null,
    @Column(length = 40) var phone: String? = null
)

@Entity
@Table(name = "professionals")
class Professional(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(nullable = false, length = 150) var name: String = ""
)

enum class AppointmentStatus { SCHEDULED, CANCELLED }

@Entity
@Table(name = "appointments")
class Appointment(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "client_id") var client: Client = Client(),
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "professional_id") var professional: Professional = Professional(),
    @Column(name = "starts_at", nullable = false) var startsAt: LocalDateTime = LocalDateTime.MIN,
    @Column(name = "ends_at", nullable = false) var endsAt: LocalDateTime = LocalDateTime.MIN,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) var status: AppointmentStatus = AppointmentStatus.SCHEDULED
)
