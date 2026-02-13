package io.github.mqtt5

import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ReconnectStrategyTest {

    // ─────────────────── ExponentialBackoff ───────────────────

    @Test
    fun exponentialBackoffDoublesDelay() {
        val strategy = ExponentialBackoff(initialDelay = 1.seconds, maxDelay = 60.seconds)
        assertEquals(1.seconds, strategy.nextDelay(1, null))
        assertEquals(2.seconds, strategy.nextDelay(2, null))
        assertEquals(4.seconds, strategy.nextDelay(3, null))
        assertEquals(8.seconds, strategy.nextDelay(4, null))
    }

    @Test
    fun exponentialBackoffCapsAtMaxDelay() {
        val strategy = ExponentialBackoff(initialDelay = 1.seconds, maxDelay = 5.seconds)
        assertEquals(1.seconds, strategy.nextDelay(1, null))
        assertEquals(2.seconds, strategy.nextDelay(2, null))
        assertEquals(4.seconds, strategy.nextDelay(3, null))
        assertEquals(5.seconds, strategy.nextDelay(4, null)) // capped
        assertEquals(5.seconds, strategy.nextDelay(5, null)) // stays capped
    }

    @Test
    fun exponentialBackoffRespectsMaxAttempts() {
        val strategy = ExponentialBackoff(initialDelay = 1.seconds, maxDelay = 60.seconds, maxAttempts = 3)
        assertNotNull(strategy.nextDelay(1, null))
        assertNotNull(strategy.nextDelay(2, null))
        assertNotNull(strategy.nextDelay(3, null))
        assertNull(strategy.nextDelay(4, null)) // exceeded
    }

    @Test
    fun exponentialBackoffUnlimitedWhenMaxAttemptsZero() {
        val strategy = ExponentialBackoff(initialDelay = 100.milliseconds, maxDelay = 10.seconds, maxAttempts = 0)
        // Should never return null
        for (i in 1..100) {
            assertNotNull(strategy.nextDelay(i, null))
        }
    }

    @Test
    fun exponentialBackoffWithJitterStaysWithinBounds() {
        val strategy = ExponentialBackoff(
            initialDelay = 1.seconds,
            maxDelay = 60.seconds,
            jitterFactor = 0.5,
        )
        // With jitter, delay should be in [base, base * 1.5], capped at maxDelay
        repeat(50) {
            val delay = strategy.nextDelay(1, null)!!
            assertTrue(delay >= 1.seconds, "delay=$delay should be >= 1s")
            assertTrue(delay <= 1500.milliseconds, "delay=$delay should be <= 1.5s")
        }
    }

    @Test
    fun exponentialBackoffJitterNeverExceedsMaxDelay() {
        val strategy = ExponentialBackoff(
            initialDelay = 1.seconds,
            maxDelay = 3.seconds,
            jitterFactor = 1.0, // max jitter
        )
        repeat(100) {
            val delay = strategy.nextDelay(5, null)!! // base would be 16s without cap
            assertTrue(delay <= 3.seconds, "delay=$delay should be <= maxDelay 3s")
        }
    }

    @Test
    fun exponentialBackoffHandlesLargeAttemptNumber() {
        val strategy = ExponentialBackoff(initialDelay = 1.seconds, maxDelay = 60.seconds)
        // Should not overflow or throw for very large attempt numbers
        val delay = strategy.nextDelay(1000, null)
        assertNotNull(delay)
        assertEquals(60.seconds, delay) // capped at max
    }

    @Test
    fun exponentialBackoffValidation() {
        assertFailsWith<IllegalArgumentException> { ExponentialBackoff(initialDelay = 0.seconds) }
        assertFailsWith<IllegalArgumentException> { ExponentialBackoff(initialDelay = 10.seconds, maxDelay = 1.seconds) }
        assertFailsWith<IllegalArgumentException> { ExponentialBackoff(maxAttempts = -1) }
        assertFailsWith<IllegalArgumentException> { ExponentialBackoff(jitterFactor = -0.1) }
        assertFailsWith<IllegalArgumentException> { ExponentialBackoff(jitterFactor = 1.1) }
    }

    // ─────────────────── ConstantDelay ───────────────────

    @Test
    fun constantDelayReturnsSameValue() {
        val strategy = ConstantDelay(delay = 5.seconds)
        assertEquals(5.seconds, strategy.nextDelay(1, null))
        assertEquals(5.seconds, strategy.nextDelay(2, null))
        assertEquals(5.seconds, strategy.nextDelay(100, null))
    }

    @Test
    fun constantDelayRespectsMaxAttempts() {
        val strategy = ConstantDelay(delay = 2.seconds, maxAttempts = 2)
        assertNotNull(strategy.nextDelay(1, null))
        assertNotNull(strategy.nextDelay(2, null))
        assertNull(strategy.nextDelay(3, null))
    }

    @Test
    fun constantDelayValidation() {
        assertFailsWith<IllegalArgumentException> { ConstantDelay(delay = 0.seconds) }
        assertFailsWith<IllegalArgumentException> { ConstantDelay(maxAttempts = -1) }
    }

    // ─────────────────── LinearBackoff ───────────────────

    @Test
    fun linearBackoffIncreasesLinearly() {
        val strategy = LinearBackoff(initialDelay = 1.seconds, step = 2.seconds, maxDelay = 60.seconds)
        assertEquals(1.seconds, strategy.nextDelay(1, null))   // 1 + 2*0
        assertEquals(3.seconds, strategy.nextDelay(2, null))   // 1 + 2*1
        assertEquals(5.seconds, strategy.nextDelay(3, null))   // 1 + 2*2
        assertEquals(7.seconds, strategy.nextDelay(4, null))   // 1 + 2*3
    }

    @Test
    fun linearBackoffCapsAtMaxDelay() {
        val strategy = LinearBackoff(initialDelay = 1.seconds, step = 5.seconds, maxDelay = 10.seconds)
        assertEquals(1.seconds, strategy.nextDelay(1, null))
        assertEquals(6.seconds, strategy.nextDelay(2, null))
        assertEquals(10.seconds, strategy.nextDelay(3, null)) // capped (would be 11s)
        assertEquals(10.seconds, strategy.nextDelay(4, null)) // stays capped
    }

    @Test
    fun linearBackoffRespectsMaxAttempts() {
        val strategy = LinearBackoff(initialDelay = 1.seconds, step = 1.seconds, maxDelay = 60.seconds, maxAttempts = 3)
        assertNotNull(strategy.nextDelay(3, null))
        assertNull(strategy.nextDelay(4, null))
    }

    @Test
    fun linearBackoffValidation() {
        assertFailsWith<IllegalArgumentException> { LinearBackoff(initialDelay = 0.seconds) }
        assertFailsWith<IllegalArgumentException> { LinearBackoff(step = 0.seconds) }
        assertFailsWith<IllegalArgumentException> { LinearBackoff(initialDelay = 10.seconds, maxDelay = 1.seconds) }
        assertFailsWith<IllegalArgumentException> { LinearBackoff(maxAttempts = -1) }
    }

    // ─────────────────── SAM / Custom Strategy ───────────────────

    @Test
    fun customStrategyViaLambda() {
        val strategy = ReconnectStrategy { attempt, _ ->
            if (attempt > 5) null else (attempt * 100).milliseconds
        }
        assertEquals(100.milliseconds, strategy.nextDelay(1, null))
        assertEquals(500.milliseconds, strategy.nextDelay(5, null))
        assertNull(strategy.nextDelay(6, null))
    }

    @Test
    fun customStrategyCanInspectCause() {
        val strategy = ReconnectStrategy { _, cause ->
            // Stop if server explicitly refused
            if (cause is MqttConnectException) null else 1.seconds
        }
        assertEquals(1.seconds, strategy.nextDelay(1, MqttConnectionException("network error")))
        assertNull(strategy.nextDelay(1, MqttConnectException(ReasonCode.NOT_AUTHORIZED)))
    }

    // ─────────────────── Config Integration ───────────────────

    @Test
    fun configEffectiveStrategyUsesExplicitStrategy() {
        val custom = ConstantDelay(delay = 3.seconds)
        val config = MqttConfig().apply { reconnectStrategy = custom }
        assertSame(custom, config.effectiveReconnectStrategy())
    }

    @Test
    fun configEffectiveStrategyBuildsFromLegacyProperties() {
        val config = MqttConfig().apply {
            reconnectDelay = 2.seconds
            maxReconnectDelay = 30.seconds
            maxReconnectAttempts = 5
        }
        val strategy = config.effectiveReconnectStrategy()
        assertTrue(strategy is ExponentialBackoff, "Expected ExponentialBackoff, got ${strategy::class.simpleName}")
        // Verify it behaves as configured
        assertEquals(2.seconds, strategy.nextDelay(1, null))
        assertEquals(4.seconds, strategy.nextDelay(2, null))
        assertNull(strategy.nextDelay(6, null)) // attempt 6 > maxAttempts 5
    }

    @Test
    fun configExplicitStrategyTakesPrecedenceOverLegacy() {
        val custom = ConstantDelay(delay = 7.seconds)
        val config = MqttConfig().apply {
            reconnectDelay = 1.seconds       // should be ignored
            maxReconnectDelay = 60.seconds   // should be ignored
            maxReconnectAttempts = 10        // should be ignored
            reconnectStrategy = custom
        }
        assertSame(custom, config.effectiveReconnectStrategy())
        assertEquals(7.seconds, config.effectiveReconnectStrategy().nextDelay(1, null))
    }
}
