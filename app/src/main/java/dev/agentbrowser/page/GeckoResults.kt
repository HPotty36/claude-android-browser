package dev.agentbrowser.page

import kotlinx.coroutines.suspendCancellableCoroutine
import org.mozilla.geckoview.GeckoResult
import kotlin.coroutines.resume

/** Waits for the result on the calling (main) thread; a failed result yields null. */
suspend fun <T> GeckoResult<T>.await(): T? = suspendCancellableCoroutine { cont ->
    accept(
        { value -> if (cont.isActive) cont.resume(value) },
        { _ -> if (cont.isActive) cont.resume(null) },
    )
}
