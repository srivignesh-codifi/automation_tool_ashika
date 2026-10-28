package com.mobileautomation.tool

import org.json.JSONObject

/**
 * The market-data feed (websocket) over a whole run in live market hours. Tradion publishes what its feed socket did
 * as the `ws_feed_status` node (see tradion_mobile AutomationFeedLog): the socket's health counters and its recent
 * events. The node is read after every step, so each connect, disconnect, stale feed or subscription error is put on
 * the step that was running when it happened, and the counters are kept even if the app ends before the run does.
 *
 * Read from what the app reports about its own socket, not from network traffic.
 */
class FeedMonitor(private val engine: AutomationEngine) {

    private class Event(val atMs: Long, val phase: String, val error: Boolean, var step: String? = null)

    private val events = LinkedHashMap<String, Event>()
    private var health: JSONObject? = null
    private var counts: JSONObject? = null
    private var maxTickGapMs = 0L
    private var maxRttMs = 0L
    private var samples = 0
    private var unread = 0
    private var firstSampleAtMs: Long? = null
    private var lastSampleAtMs: Long? = null

    /** Reads the node once; [steps] attributes new events to the step whose time span holds them. */
    fun sample(steps: List<AutomationStepResult>) {
        val label = engine.labelOf(AutomationConfig.TradionIds.WS_FEED_STATUS)
        if (label.isNullOrBlank()) {
            // Before sign-in the app publishes nothing yet; only a gap after the first reading counts.
            if (samples > 0) unread++
            return
        }
        val json = try {
            JSONObject(label)
        } catch (_: Throwable) {
            unread++
            return
        }
        samples++
        val now = System.currentTimeMillis()
        if (firstSampleAtMs == null) firstSampleAtMs = now
        lastSampleAtMs = now
        json.optJSONObject("health")?.let { health = it }
        json.optJSONObject("counts")?.let { counts = it }
        maxTickGapMs = maxOf(maxTickGapMs, json.optLong("runMaxTickGapMs", 0L))
        maxRttMs = maxOf(maxRttMs, json.optLong("runMaxRttMs", 0L))
        val list = json.optJSONArray("events") ?: return
        for (i in 0 until list.length()) {
            val raw = list.optString(i)
            if (raw.isNullOrBlank() || events.containsKey(raw)) continue
            val parts = raw.split('|')
            val at = parts.getOrNull(0)?.toLongOrNull() ?: continue
            val phase = parts.getOrNull(1).orEmpty()
            val event = Event(at, phase, parts.getOrNull(2) == "E")
            // The step running at that moment, or else the last one begun before it (an event between two steps).
            event.step = (steps.firstOrNull { s ->
                val start = s.startedAtMs ?: return@firstOrNull false
                at >= start && at <= (s.finishedAtMs ?: Long.MAX_VALUE)
            } ?: steps.filter { (it.startedAtMs ?: Long.MAX_VALUE) <= at }.maxByOrNull { it.startedAtMs ?: 0L })?.name
            events[raw] = event
        }
    }

    private fun h(key: String): Long? = health?.let { if (it.has(key) && !it.isNull(key)) it.optLong(key) else null }

    /** What the report shows; [live] false gives the BOD note only. */
    fun report(live: Boolean): Map<String, Any?> {
        if (!live) {
            return mapOf(
                "mode" to "bod",
                "note" to "BOD run (before the market opens): the live feed was not measured. Choose Live trade time to measure it.",
            )
        }
        val significant = DISCONNECTS + setOf(
            "connect.start", "connect.ok", "connect.setup_failed", "probe.timeout", "sink.failed", "close.failed",
            "close.requested", "low_network", "message.handle_failed", "feed.resumed", "pause",
        )
        val shown = events.values.filter { it.error || it.phase in significant || it.phase.contains("disconnect") || it.phase.contains("unack") }
        val dropped = events.values.filter { it.phase in DISCONNECTS }.sortedBy { it.atMs }
        val clock = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
        val problems = mutableListOf<String>()
        fun count(key: String) = h(key) ?: 0L
        if (samples == 0) problems += "the app published no feed status (install a Tradion build with the ws_feed_status marker)"
        if (count("disconnects") > 0) problems += "${count("disconnects")} unplanned disconnection(s)"
        for (d in dropped) {
            problems += "feed dropped at ${clock.format(java.util.Date(d.atMs))} (${CAUSES[d.phase] ?: d.phase}) during '${d.step ?: "before the first step"}'"
        }
        if (count("connect_timeouts") > 0) problems += "${count("connect_timeouts")} connect timeout(s)"
        if (count("login_rejects") > 0) problems += "${count("login_rejects")} connection(s) rejected by the server"
        if (count("unacked_keys") > 0 || count("ack_timeouts") > 0) {
            problems += "subscription errors: ${count("unacked_keys")} instrument(s) never acknowledged, ${count("ack_timeouts")} ack timeout(s)"
        }
        if (count("stale_detections") > 0) problems += "${count("stale_detections")} stale feed(s) (no data within the stale threshold)"
        if (count("heartbeats_missing") > 0) problems += "${count("heartbeats_missing")} heartbeat(s) without a reply"
        if (count("malformed_frames") > 0) problems += "${count("malformed_frames")} malformed frame(s)"
        if (samples > 0 && count("ticks") == 0L) problems += "no price tick was received"
        if (count("silent_keys") > 0) problems += "${count("silent_keys")} subscribed instrument(s) silent at the end"
        if (maxTickGapMs >= LONG_TICK_GAP_MS) problems += "longest gap between ticks ${"%.1f".format(maxTickGapMs / 1000.0)} s"
        val errors = shown.count { it.error }
        return mapOf(
            "mode" to "live",
            "samples" to samples,
            "unreadSamples" to unread,
            "firstSampleAtMs" to firstSampleAtMs,
            "lastSampleAtMs" to lastSampleAtMs,
            "verdict" to if (problems.isEmpty()) "OK" else "PROBLEMS",
            "problems" to problems,
            "connectedAtEnd" to health?.optBoolean("connected"),
            "connects" to count("login_acks"),
            "connectAttempts" to count("reconnect_attempts"),
            "disconnects" to count("disconnects"),
            "connectTimeouts" to count("connect_timeouts"),
            "loginRejects" to count("login_rejects"),
            "unackedKeys" to count("unacked_keys"),
            "ackTimeouts" to count("ack_timeouts"),
            "staleDetections" to count("stale_detections"),
            "heartbeatsSent" to count("heartbeats_sent"),
            "heartbeatsMissing" to count("heartbeats_missing"),
            "ticks" to count("ticks"),
            "malformedFrames" to count("malformed_frames"),
            "subscribedKeys" to count("subscribed_keys"),
            "silentKeys" to count("silent_keys"),
            "lowNetworkEvents" to count("low_network_events"),
            "connectToAckMs" to h("connect_to_ack_ms"),
            "ackToFirstTickMs" to h("ack_to_first_tick_ms"),
            "maxTickGapMs" to maxTickGapMs,
            "maxRttMs" to maxRttMs,
            "errorEvents" to errors,
            "disconnections" to dropped.map {
                mapOf("atMs" to it.atMs, "cause" to (CAUSES[it.phase] ?: it.phase), "step" to it.step)
            },
            "events" to shown.sortedBy { it.atMs }.takeLast(60).map {
                mapOf("atMs" to it.atMs, "phase" to it.phase, "error" to (it.error || it.phase in DISCONNECTS), "step" to it.step)
            },
        )
    }

    companion object {
        /** A gap between two ticks this long, in market hours, is reported. */
        const val LONG_TICK_GAP_MS = 10_000L

        /** The phases Tradion logs when the feed socket is lost or could not be (re)established, with what they mean. */
        val CAUSES = mapOf(
            "onDone" to "the server closed the socket",
            "onError" to "socket error",
            "liveness.failed" to "no reply to the liveness check",
            "heartbeat.timeout" to "heartbeat not answered",
            "connection.stale" to "no data within the stale threshold",
            "send.failed" to "a frame could not be sent",
            "connect.failed" to "connection failed",
            "connect.timeout" to "connection timed out",
            "connect.rejected" to "login to the feed rejected",
        )
        val DISCONNECTS = CAUSES.keys
    }
}
