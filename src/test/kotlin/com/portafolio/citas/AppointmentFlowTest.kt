package com.portafolio.citas

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath

@SpringBootTest
@AutoConfigureMockMvc
class AppointmentFlowTest(@Autowired val mvc: MockMvc, @Autowired val json: ObjectMapper) {
    private fun postJson(path: String, body: String, token: String? = null, expected: Int = 201): String {
        val request = post(path).contentType(MediaType.APPLICATION_JSON).content(body)
        if (token != null) request.header("Authorization", "Bearer $token")
        return mvc.perform(request).andExpect(status().`is`(expected)).andReturn().response.contentAsString
    }

    @Test
    fun `authenticated user can manage appointments without overlapping`() {
        mvc.perform(get("/api/clients")).andExpect(status().isUnauthorized)
        postJson("/api/auth/login", """{"email":"admin@example.com","password":"incorrecta"}""", expected = 401)
        val login = postJson("/api/auth/login", """{"email":"admin@example.com","password":"password-de-prueba"}""", expected = 200)
        val token = json.readTree(login)["accessToken"].asText()
        val client = json.readTree(postJson("/api/clients", """{"name":"Ana"}""", token))["id"].asLong()
        val professional = json.readTree(postJson("/api/professionals", """{"name":"Laura"}""", token))["id"].asLong()
        val body = """{"clientId":$client,"professionalId":$professional,"startsAt":"2099-05-01T10:00:00","endsAt":"2099-05-01T11:00:00"}"""
        val appointment = json.readTree(postJson("/api/appointments", body, token))["id"].asLong()
        postJson("/api/appointments", body, token, 409)
        mvc.perform(get("/api/appointments?date=2099-05-01").header("Authorization", "Bearer $token")).andExpect(status().isOk)
        mvc.perform(patch("/api/appointments/$appointment/cancel").header("Authorization", "Bearer $token")).andExpect(status().isOk)
        postJson("/api/appointments", body, token)
    }

    @Test
    fun `rescheduling checks conflicts and cancelled state while preserving identity`() {
        val token = token()
        val client = json.readTree(postJson("/api/clients", """{"name":"Cliente"}""", token))["id"].asLong()
        val professional = json.readTree(postJson("/api/professionals", """{"name":"Profesional"}""", token))["id"].asLong()
        fun appointment(start: String, end: String) = """{"clientId":$client,"professionalId":$professional,"startsAt":"$start","endsAt":"$end"}"""
        val first = json.readTree(postJson("/api/appointments", appointment("2099-06-01T10:00:00", "2099-06-01T11:00:00"), token))["id"].asLong()
        // Adjacent appointments are allowed.
        postJson("/api/appointments", appointment("2099-06-01T11:00:00", "2099-06-01T12:00:00"), token)
        fun move(body: String, expected: Int) = mvc.perform(patch("/api/appointments/$first/reschedule").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().`is`(expected))
        move("""{"startsAt":"2099-06-01T11:30:00","endsAt":"2099-06-01T12:30:00"}""", 409)
        move("""{"startsAt":"2099-06-01T10:30:00","endsAt":"2099-06-01T11:00:00"}""", 200).andExpect(jsonPath("$.id").value(first))
        mvc.perform(patch("/api/appointments/$first/cancel").header("Authorization", "Bearer $token")).andExpect(status().isOk)
        move("""{"startsAt":"2099-06-01T12:00:00","endsAt":"2099-06-01T13:00:00"}""", 409)
    }

    @Test
    fun `validates dates missing resources and request bodies`() {
        val token = token()
        val client = json.readTree(postJson("/api/clients", """{"name":"Cliente"}""", token))["id"].asLong()
        val professional = json.readTree(postJson("/api/professionals", """{"name":"Profesional"}""", token))["id"].asLong()
        fun appointment(c: Long, p: Long, start: String, end: String) = """{"clientId":$c,"professionalId":$p,"startsAt":"$start","endsAt":"$end"}"""
        postJson("/api/appointments", appointment(client, professional, "2000-01-01T10:00:00", "2000-01-01T11:00:00"), token, 400)
        postJson("/api/appointments", appointment(client, professional, "2099-01-01T11:00:00", "2099-01-01T10:00:00"), token, 400)
        postJson("/api/appointments", appointment(999999, professional, "2099-01-01T10:00:00", "2099-01-01T11:00:00"), token, 404)
        postJson("/api/appointments", appointment(client, 999999, "2099-01-01T10:00:00", "2099-01-01T11:00:00"), token, 404)
        mvc.perform(post("/api/clients").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content("""{"name":""}"""))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.errors[0].field").value("name"))
        postJson("/api/appointments", """{"startsAt":"no-es-fecha"}""", token, 400)
    }

    @Test
    fun `updates client and professional details`() {
        val token = token()
        val client = json.readTree(postJson("/api/clients", """{"name":"Antes","email":"ana@example.com"}""", token))["id"].asLong()
        val professional = json.readTree(postJson("/api/professionals", """{"name":"Antes"}""", token))["id"].asLong()
        mvc.perform(put("/api/clients/$client").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content("""{"name":"Después"}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.name").value("Después")).andExpect(jsonPath("$.email").doesNotExist())
        mvc.perform(put("/api/professionals/$professional").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content("""{"name":"Actualizado"}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.name").value("Actualizado"))
        mvc.perform(put("/api/professionals/999999").header("Authorization", "Bearer $token").contentType(MediaType.APPLICATION_JSON).content("""{"name":"Nadie"}"""))
            .andExpect(status().isNotFound)
    }

    private fun token(): String = json.readTree(postJson("/api/auth/login", """{"email":"admin@example.com","password":"password-de-prueba"}""", expected = 200))["accessToken"].asText()
}
