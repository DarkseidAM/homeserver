package `in`.darkseid.homeserver.utils

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.slf4j.MDCContext
import org.slf4j.MDC
import kotlin.coroutines.CoroutineContext

/**
 * Creates a [CoroutineName] and an [MDCContext] for identifying coroutines in logs.
 *
 * This function captures the current MDC context, adds a "coroutine" key with the given [name],
 * and returns a combined context of [CoroutineName] and [MDCContext].
 *
 * @param name The name to assign to the coroutine.
 * @return A [CoroutineContext] containing [CoroutineName] and [MDCContext].
 */
fun named(name: String): CoroutineContext {
    val contextMap = MDC.getCopyOfContextMap() ?: emptyMap()
    val newMap = contextMap + ("coroutine" to name)
    return CoroutineName(name) + MDCContext(newMap)
}

/**
 * Extension function to append a name to an existing [CoroutineContext].
 *
 * @param name The name to assign.
 * @return The new [CoroutineContext] with the name added.
 */
fun CoroutineContext.withName(name: String): CoroutineContext = this + named(name)
