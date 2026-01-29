package `in`.darkseid.homeserver.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class SystemStats(
    val osFamily: String,
    val osManufacturer: String,
    val osVersion: String,
    val systemManufacturer: String,
    val systemModel: String,
    val processorName: String, // e.g. "Intel Core i7..."
    val uptime: Long, // seconds
    val processCount: Int,
    val threadCount: Int,
    val cpuVoltage: Double, // Volts
    val fanSpeeds: List<Int>, // RPM
)
