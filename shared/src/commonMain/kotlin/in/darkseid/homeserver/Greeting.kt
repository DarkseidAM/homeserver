package `in`.darkseid.homeserver

class Greeting(
    private val platform: Platform = getPlatform(),
) {
    fun greet(): String = "Hello, ${platform.name}!"
}
