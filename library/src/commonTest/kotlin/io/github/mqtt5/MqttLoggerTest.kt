package io.github.mqtt5

import kotlin.test.*

class MqttLoggerTest {

    @Test
    fun testLoggerOutputsAtMinLevel() {
        val logs = mutableListOf<Triple<MqttLogLevel, String, String>>()
        val logger = MqttLogger(minLevel = MqttLogLevel.INFO) { level, tag, message ->
            logs.add(Triple(level, tag, message))
        }

        logger.debug("test") { "debug message" }
        logger.info("test") { "info message" }
        logger.warn("test") { "warn message" }
        logger.error("test") { "error message" }

        // DEBUG should be filtered out (minLevel = INFO)
        assertEquals(3, logs.size)
        assertEquals(MqttLogLevel.INFO, logs[0].first)
        assertEquals("info message", logs[0].third)
        assertEquals(MqttLogLevel.WARN, logs[1].first)
        assertEquals("warn message", logs[1].third)
        assertEquals(MqttLogLevel.ERROR, logs[2].first)
        assertEquals("error message", logs[2].third)
    }

    @Test
    fun testLoggerDebugLevel() {
        val logs = mutableListOf<MqttLogLevel>()
        val logger = MqttLogger(minLevel = MqttLogLevel.DEBUG) { level, _, _ ->
            logs.add(level)
        }

        logger.debug("t") { "d" }
        logger.info("t") { "i" }
        logger.warn("t") { "w" }
        logger.error("t") { "e" }

        assertEquals(4, logs.size)
        assertEquals(MqttLogLevel.DEBUG, logs[0])
        assertEquals(MqttLogLevel.INFO, logs[1])
        assertEquals(MqttLogLevel.WARN, logs[2])
        assertEquals(MqttLogLevel.ERROR, logs[3])
    }

    @Test
    fun testLoggerErrorOnlyLevel() {
        val logs = mutableListOf<MqttLogLevel>()
        val logger = MqttLogger(minLevel = MqttLogLevel.ERROR) { level, _, _ ->
            logs.add(level)
        }

        logger.debug("t") { "d" }
        logger.info("t") { "i" }
        logger.warn("t") { "w" }
        logger.error("t") { "e" }

        assertEquals(1, logs.size)
        assertEquals(MqttLogLevel.ERROR, logs[0])
    }

    @Test
    fun testLoggerWarnLevel() {
        val logs = mutableListOf<MqttLogLevel>()
        val logger = MqttLogger(minLevel = MqttLogLevel.WARN) { level, _, _ ->
            logs.add(level)
        }

        logger.debug("t") { "d" }
        logger.info("t") { "i" }
        logger.warn("t") { "w" }
        logger.error("t") { "e" }

        assertEquals(2, logs.size)
        assertEquals(MqttLogLevel.WARN, logs[0])
        assertEquals(MqttLogLevel.ERROR, logs[1])
    }

    @Test
    fun testLoggerTagPropagation() {
        var receivedTag = ""
        val logger = MqttLogger(minLevel = MqttLogLevel.DEBUG) { _, tag, _ ->
            receivedTag = tag
        }

        logger.info("MqttClient") { "test" }
        assertEquals("MqttClient", receivedTag)
    }

    @Test
    fun testLoggerLazyEvaluation() {
        var evaluated = false
        val logger = MqttLogger(minLevel = MqttLogLevel.ERROR) { _, _, _ -> }

        // Debug should not evaluate the message lambda since minLevel is ERROR
        logger.debug("test") {
            evaluated = true
            "should not be evaluated"
        }

        assertFalse(evaluated, "Debug message lambda should not be evaluated when minLevel is ERROR")
    }

    @Test
    fun testLoggerLazyEvaluationCalledWhenNeeded() {
        var evaluated = false
        val logger = MqttLogger(minLevel = MqttLogLevel.DEBUG) { _, _, _ -> }

        logger.debug("test") {
            evaluated = true
            "should be evaluated"
        }

        assertTrue(evaluated, "Debug message lambda should be evaluated when minLevel is DEBUG")
    }

    @Test
    fun testLogLevelOrdering() {
        // Verify enum ordering which is used in comparisons
        assertTrue(MqttLogLevel.DEBUG < MqttLogLevel.INFO)
        assertTrue(MqttLogLevel.INFO < MqttLogLevel.WARN)
        assertTrue(MqttLogLevel.WARN < MqttLogLevel.ERROR)
    }

    @Test
    fun testLoggerConfigInClient() {
        val logs = mutableListOf<String>()
        val client = MqttClient {
            logger = MqttLogger(minLevel = MqttLogLevel.DEBUG) { _, _, message ->
                logs.add(message)
            }
        }

        assertNotNull(client.config.logger)
    }

    @Test
    fun testMinLevelCanBeChanged() {
        val logs = mutableListOf<MqttLogLevel>()
        val logger = MqttLogger(minLevel = MqttLogLevel.ERROR) { level, _, _ ->
            logs.add(level)
        }

        logger.info("t") { "should be filtered" }
        assertEquals(0, logs.size)

        logger.minLevel = MqttLogLevel.INFO
        logger.info("t") { "should pass" }
        assertEquals(1, logs.size)
        assertEquals(MqttLogLevel.INFO, logs[0])
    }
}
