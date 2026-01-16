package `in`.darkseid.homeserver

import kotlin.test.Test
import kotlin.test.assertTrue

class JVMPlatformTest {
    @Test
    fun testPlatformName() {
        val platform = JVMPlatform()
        assertTrue(platform.name.startsWith("Java"), "Platform name should start with 'Java'")
    }
}
