package `in`.darkseid.homeserver

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
