package io.github.mqtt5.internal

/**
 * Manages Topic Alias mappings for MQTT v5 per Section 3.3.2.3.4.
 *
 * Topic Alias mappings exist only within a single Network Connection.
 */
internal class TopicAliasManager(
    /** Maximum topic alias value (from CONNACK or CONNECT). 0 means disabled. */
    private val maximum: Int = 0,
) {
    /** Maps alias (1..maximum) -> topic name. */
    private val aliasToTopic = mutableMapOf<Int, String>()

    /** Maps topic name -> alias for outbound optimization. */
    private val topicToAlias = mutableMapOf<String, Int>()

    private var nextAlias = 1

    val isEnabled: Boolean get() = maximum > 0

    /**
     * Resolve a received topic alias. Used for inbound messages.
     *
     * If topicName is non-empty, updates the mapping.
     * If topicName is empty, looks up the existing mapping.
     *
     * @return The resolved topic name.
     */
    fun resolve(topicName: String, alias: Int?): String {
        if (alias == null) return topicName
        require(alias in 1..maximum) { "Topic alias $alias out of range (1..$maximum)" }

        return if (topicName.isNotEmpty()) {
            // Update mapping
            aliasToTopic[alias] = topicName
            topicName
        } else {
            // Look up existing mapping
            aliasToTopic[alias]
                ?: throw IllegalStateException("No mapping for topic alias $alias")
        }
    }

    /**
     * Get or create an alias for an outbound topic.
     *
     * @return Pair of (topicName to send, alias to send). If the alias is new,
     *         topicName is non-empty. If reusing, topicName is empty.
     */
    fun getOutboundAlias(topicName: String): Pair<String, Int>? {
        if (!isEnabled) return null

        val existing = topicToAlias[topicName]
        if (existing != null) {
            // Reuse existing alias with empty topic
            return "" to existing
        }

        // Allocate new alias if possible
        if (nextAlias <= maximum) {
            val alias = nextAlias++
            topicToAlias[topicName] = alias
            aliasToTopic[alias] = topicName
            return topicName to alias
        }

        // No more aliases available; send without alias
        return null
    }

    /**
     * Clear all mappings (e.g., on reconnect).
     */
    fun clear() {
        aliasToTopic.clear()
        topicToAlias.clear()
        nextAlias = 1
    }
}
