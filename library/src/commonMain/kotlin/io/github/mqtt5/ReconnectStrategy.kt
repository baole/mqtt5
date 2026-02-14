package io.github.mqtt5

import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Strategy that controls reconnection behavior after connection loss.
 *
 * Implement this interface to define custom reconnection logic such as
 * different backoff algorithms, conditional retry based on the disconnect
 * reason, or integration with external circuit-breaker systems.
 *
 * The strategy is called before each reconnection attempt. Return the
 * [Duration] to wait before the next attempt, or `null` to stop reconnecting.
 *
 * ## Built-in strategies
 *
 * - [ExponentialBackoff] — default; doubles the delay each attempt with optional jitter
 * - [ConstantDelay] — fixed delay between every attempt
 * - [LinearBackoff] — increases delay linearly by a fixed step each attempt
 *
 * ## Example: custom strategy
 *
 * ```kotlin
 * val client = MqttClient {
 *     autoReconnect = true
 *     reconnectStrategy = ReconnectStrategy { attempt, cause ->
 *         if (cause is MqttConnectException) null   // server refused — stop
 *         else (attempt * 2).seconds                 // linear 2s, 4s, 6s …
 *     }
 * }
 * ```
 */
fun interface ReconnectStrategy {
    /**
     * Compute the delay before the next reconnection attempt.
     *
     * @param attempt 1-based attempt number
     * @param cause the error that triggered reconnection (may be `null` for keep-alive timeout)
     * @return the [Duration] to wait before attempting, or `null` to stop reconnecting
     */
    fun nextDelay(attempt: Int, cause: Throwable?): Duration?

    companion object {
        /**
         * A strategy that never reconnects. Equivalent to `ReconnectStrategy { _, _ -> null }`.
         *
         * Useful when you want `autoReconnect = true` for offline-queue buffering
         * but want to control reconnection timing yourself via [MqttClient.connect].
         */
        val None: ReconnectStrategy = ReconnectStrategy { _, _ -> null }
    }
}

/**
 * Exponential backoff strategy (the default).
 *
 * Delay doubles after each attempt: `initialDelay`, `initialDelay * 2`, `initialDelay * 4`, …
 * capped at [maxDelay]. Optionally adds random jitter up to [jitterFactor] of the computed delay
 * to prevent reconnect storms (thundering herd).
 *
 * @param initialDelay delay before the first reconnection attempt (default 1 s)
 * @param maxDelay upper bound for the backoff delay (default 60 s)
 * @param maxAttempts maximum number of attempts; 0 means unlimited (default 0)
 * @param jitterFactor fraction of the delay to randomize (0.0–1.0); 0 disables jitter (default 0.0)
 */
class ExponentialBackoff(
    private val initialDelay: Duration = 1.seconds,
    private val maxDelay: Duration = 60.seconds,
    private val maxAttempts: Int = 0,
    private val jitterFactor: Double = 0.0,
) : ReconnectStrategy {

    init {
        require(initialDelay.isPositive()) { "initialDelay must be positive" }
        require(maxDelay >= initialDelay) { "maxDelay must be >= initialDelay" }
        require(maxAttempts >= 0) { "maxAttempts must be >= 0" }
        require(jitterFactor in 0.0..1.0) { "jitterFactor must be in 0.0..1.0" }
    }

    override fun nextDelay(attempt: Int, cause: Throwable?): Duration? {
        if (maxAttempts > 0 && attempt > maxAttempts) return null

        // 2^(attempt-1) * initialDelay, capped at maxDelay
        val shift = min(attempt - 1, 30) // prevent overflow
        val baseMs = (initialDelay.inWholeMilliseconds shl shift)
            .coerceAtMost(maxDelay.inWholeMilliseconds)

        if (jitterFactor == 0.0) return baseMs.milliseconds

        val jitterMs = (baseMs * jitterFactor * Random.nextDouble()).toLong()
        return (baseMs + jitterMs).coerceAtMost(maxDelay.inWholeMilliseconds).milliseconds
    }
}

/**
 * Constant delay strategy — waits the same duration between every attempt.
 *
 * @param delay fixed delay between attempts
 * @param maxAttempts maximum number of attempts; 0 means unlimited
 */
class ConstantDelay(
    private val delay: Duration = 5.seconds,
    private val maxAttempts: Int = 0,
) : ReconnectStrategy {

    init {
        require(delay.isPositive()) { "delay must be positive" }
        require(maxAttempts >= 0) { "maxAttempts must be >= 0" }
    }

    override fun nextDelay(attempt: Int, cause: Throwable?): Duration? {
        if (maxAttempts > 0 && attempt > maxAttempts) return null
        return delay
    }
}

/**
 * Linear backoff strategy — increases delay by a fixed step each attempt.
 *
 * Delay = `initialDelay + step * (attempt - 1)`, capped at [maxDelay].
 *
 * @param initialDelay delay before the first attempt
 * @param step added to the delay after each attempt
 * @param maxDelay upper bound for the delay
 * @param maxAttempts maximum number of attempts; 0 means unlimited
 */
class LinearBackoff(
    private val initialDelay: Duration = 1.seconds,
    private val step: Duration = 1.seconds,
    private val maxDelay: Duration = 60.seconds,
    private val maxAttempts: Int = 0,
) : ReconnectStrategy {

    init {
        require(initialDelay.isPositive()) { "initialDelay must be positive" }
        require(step.isPositive()) { "step must be positive" }
        require(maxDelay >= initialDelay) { "maxDelay must be >= initialDelay" }
        require(maxAttempts >= 0) { "maxAttempts must be >= 0" }
    }

    override fun nextDelay(attempt: Int, cause: Throwable?): Duration? {
        if (maxAttempts > 0 && attempt > maxAttempts) return null
        val delayMs = initialDelay.inWholeMilliseconds + step.inWholeMilliseconds * (attempt - 1)
        return delayMs.coerceAtMost(maxDelay.inWholeMilliseconds).milliseconds
    }
}
