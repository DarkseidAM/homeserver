package `in`.darkseid.homeserver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GreetingTest {
    private class TestPlatform(
        override val name: String,
    ) : Platform

    @Test
    fun testGreeting() {
        val platformName = "Test Platform"
        val greeting = Greeting(TestPlatform(platformName))
        assertEquals("Hello, $platformName!", greeting.greet())
    }

    @Test
    fun testDefaultGreeting() {
        // This exercises the default constructor and getPlatform()
        val greeting = Greeting()
        val message = greeting.greet()
        // We can't easily assert the exact content since it depends on the platform version,
        // but we can assert it starts with "Hello, "
        assertTrue(message.startsWith("Hello, "), "Greeting should start with 'Hello, '")
    }
}
