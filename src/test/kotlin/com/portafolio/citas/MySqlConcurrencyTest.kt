package com.portafolio.citas

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// Run only against the dedicated disposable database, never the application's database.
@EnabledIfEnvironmentVariable(named = "MYSQL_TEST_URL", matches = ".+")
@SpringBootTest
class MySqlConcurrencyTest @Autowired constructor(
    private val service: AppointmentService,
    private val clients: ClientRepository,
    private val professionals: ProfessionalRepository
) {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun mysql(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { System.getenv("MYSQL_TEST_URL") }
            registry.add("spring.datasource.username") { System.getenv("MYSQL_TEST_USER") ?: "test" }
            registry.add("spring.datasource.password") { System.getenv("MYSQL_TEST_PASSWORD") ?: "test-password" }
        }
    }

    @Test
    fun `simultaneous bookings and reschedules cannot reserve the same slot`() {
        val client = clients.save(Client(name = "Prueba"))
        val professional = professionals.save(Professional(name = "Prueba concurrencia"))
        fun time(hour: Int) = LocalDateTime.of(2099, 1, 1, hour, 0)
        fun create(start: Int, end: Int) = service.create(AppointmentRequest(client.id!!, professional.id!!, time(start), time(end)))

        val createResults = race({ create(10, 11) }, { create(10, 11) })
        assertEquals(listOf(201, 409), createResults.sorted())

        val first = create(12, 13)
        val second = create(14, 15)
        val target = RescheduleRequest(time(16), time(17))
        val moveResults = race({ service.reschedule(first.id, target) }, { service.reschedule(second.id, target) })
        assertEquals(listOf(201, 409), moveResults.sorted())
        assertEquals(1, service.list(null).count { it.professionalId == professional.id && it.startsAt == time(16) })
    }

    private fun race(first: () -> AppointmentResponse, second: () -> AppointmentResponse): List<Int> {
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val futures = listOf(first, second).map { operation ->
                pool.submit(Callable {
                    ready.countDown()
                    check(start.await(10, TimeUnit.SECONDS))
                    try { operation(); 201 } catch (ex: ResponseStatusException) { ex.statusCode.value() }
                })
            }
            check(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            return futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            start.countDown()
            pool.shutdownNow()
        }
    }
}
