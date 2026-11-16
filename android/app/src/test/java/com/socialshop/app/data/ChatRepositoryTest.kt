package com.socialshop.app.data

import com.socialshop.app.data.remote.ApiException
import com.socialshop.app.testutil.FakeTokenStorage
import com.socialshop.app.testutil.TestBackend
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ChatRepositoryTest {

    private val backend = TestBackend(tokens = FakeTokenStorage("tok-user"))

    @After
    fun tearDown() = backend.shutdown()

    @Test
    fun `streams text chunks until done event`() = runBlocking {
        backend.enqueueSse("""{"text":"Shop có "}""", """{"text":"3 mẫu hoodie."}""", """{"done":true}""", """{"text":"bỏ qua sau done"}""")

        val chunks = backend.chat.ask("Có hoodie không?", emptyList()).toList()

        assertEquals(listOf("Shop có ", "3 mẫu hoodie."), chunks)
    }

    @Test
    fun `single event answer with done flag is emitted once`() = runBlocking {
        backend.enqueueSse("""{"text":"Đổi trả trong 7 ngày.","done":true}""")

        assertEquals(listOf("Đổi trả trong 7 ngày."), backend.chat.ask("Đổi trả?", emptyList()).toList())
    }

    @Test
    fun `sends question, last 20 history turns and bearer token`() = runBlocking {
        backend.enqueueSse("""{"done":true}""")
        val history = (1..25).map { ChatTurn(if (it % 2 == 0) "bot" else "user", "lượt $it") }

        backend.chat.ask("Câu mới", history).toList()

        val request = backend.server.takeRequest()
        val body = request.body.readUtf8()
        assertEquals("/api/v1/chat", request.path)
        assertEquals("Bearer tok-user", request.getHeader("Authorization"))
        assertTrue(body.contains("\"message\":\"Câu mới\""))
        assertTrue("Giữ 20 lượt gần nhất", body.contains("lượt 25") && body.contains("lượt 6"))
        assertFalse("Bỏ các lượt cũ hơn", body.contains("\"lượt 5\""))
    }

    @Test
    fun `validation error is turned into readable message`() = runBlocking {
        backend.enqueueJson("""{"message": "Tin nhắn tối đa 1000 ký tự.", "errors": {"message": ["Tin nhắn tối đa 1000 ký tự."]}}""", code = 422)

        try {
            backend.chat.ask("x".repeat(1001), emptyList()).toList()
            fail("Phải ném ApiException")
        } catch (e: ApiException) {
            assertEquals(422, e.code)
            assertEquals("Tin nhắn tối đa 1000 ký tự.", e.message)
        }
    }
}
