package com.mobileautomation.tool

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Surface
import android.view.WindowManager
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Records the whole screen to an MP4 for the length of one run.
 *
 * Android only lets an app capture the screen through MediaProjection, and only
 * after the operator accepts the system "start recording" dialog. The tool asks
 * for that once per run ([MainActivity] "requestScreenRecording"); the grant is
 * single use and is spent by [start]. The capture itself lives in
 * [RecordingService], a foreground service, which Android requires to keep a
 * projection alive while another app is in front.
 *
 * The screen feeds a MediaCodec H.264 encoder whose output a MediaMuxer writes
 * to the MP4. MediaRecorder was not used because it forces a key frame about
 * every second, which on a mostly still screen was nearly half the file.
 *
 * Nothing here can fail a run: a recording that cannot start, or cannot be
 * finalised, is reported as a note on the result and the run carries on.
 *
 * The video is a picture of the screen, so it shows whatever the target app
 * shows, including typed values it does not mask. Only the tester's own
 * account should be used for a recorded run.
 */
object ScreenRecorder {

    /** The operator's answer to the system dialog. Valid for one [start]. */
    class Consent(val resultCode: Int, val data: Intent)

    data class Started(val ok: Boolean, val detail: String)

    /**
     * [file] is null when no usable video exists; [note] then says why, or, with
     * a file, what the video does not cover (for example it ended early).
     */
    class Finished(val file: File?, val startedAtMs: Long?, val note: String?)

    // About 0.75 MB per minute (an 8-minute run measured 5.9 MB, against 107 MB
    // at the earlier 720p / 15 fps / 1.5 Mbit/s with 1 s key frames) with the
    // prices still readable. The screen is mostly still, so a variable bit rate
    // and a key frame every 20 s carry most of the saving; the report's seek
    // links decode from the key frame before the step, which a browser does at
    // once.
    private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC
    private const val MAX_SHORT_SIDE = 480
    private const val ALIGN = 16
    private const val FRAME_RATE = 8
    private const val BIT_RATE = 100_000
    private const val KEY_FRAME_INTERVAL_S = 20
    private const val DRAIN_TIMEOUT_US = 100_000L
    private const val DRAIN_FINISH_WAIT_MS = 5_000L
    private const val MIN_FREE_BYTES = 500L * 1024 * 1024
    private const val SERVICE_START_WAIT_MS = 10_000L

    private class Pending(val consent: Consent, val file: File, val latch: CountDownLatch)

    private class Session(
        val file: File,
        val service: Service,
        val projection: MediaProjection,
        val callback: MediaProjection.Callback,
        val codec: MediaCodec,
        val surface: Surface,
        val muxer: MediaMuxer,
        val drain: Drain,
        val display: VirtualDisplay,
        val startedAtMs: Long,
    )

    /**
     * Moves encoded frames from [codec] into [muxer] until the end of stream.
     * The muxer starts when the encoder reports its format, so it only ever
     * holds a playable track. A failure is kept in [error] and handed to
     * [onError]; [finish] reads both after joining.
     */
    private class Drain(
        val codec: MediaCodec,
        val muxer: MediaMuxer,
        val onError: (String) -> Unit,
    ) : Thread("run-recording-encoder") {
        @Volatile var error: String? = null
        @Volatile var samples = 0

        override fun run() {
            val info = MediaCodec.BufferInfo()
            var track = -1
            try {
                while (true) {
                    val index = codec.dequeueOutputBuffer(info, DRAIN_TIMEOUT_US)
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                    } else if (index >= 0) {
                        val buffer = codec.getOutputBuffer(index)
                        val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (!config && info.size > 0 && track >= 0 && buffer != null) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buffer, info)
                            samples++
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            } catch (t: Throwable) {
                val reason = "the encoder failed (${t.javaClass.simpleName}: ${t.message ?: "no detail"})"
                error = reason
                onError(reason)
            }
        }
    }

    @Volatile
    private var consent: Consent? = null
    private var pending: Pending? = null
    private var session: Session? = null
    private var outcome: Started? = null
    private var finished: Finished? = null

    fun setConsent(value: Consent?) {
        consent = value
    }

    /**
     * Starts recording into [file] and waits until it is running. Never throws;
     * [Started.detail] says why when it could not start.
     */
    fun start(context: Context, file: File): Started {
        val granted = synchronized(this) { consent.also { consent = null } }
            ?: return Started(false, "screen recording was not allowed (the system dialog was declined or not shown)")

        file.parentFile?.mkdirs()
        val free = file.parentFile?.usableSpace ?: 0L
        if (free < MIN_FREE_BYTES) {
            return Started(false, "less than ${MIN_FREE_BYTES / (1024 * 1024)} MB of storage is free")
        }

        val latch = CountDownLatch(1)
        synchronized(this) {
            if (session != null) finish("replaced by a newer run")
            finished = null
            outcome = null
            pending = Pending(granted, file, latch)
        }
        try {
            context.startForegroundService(Intent(context, RecordingService::class.java))
        } catch (t: Throwable) {
            synchronized(this) { pending = null }
            return Started(false, "the recording service could not start (${t.javaClass.simpleName})")
        }
        if (!latch.await(SERVICE_START_WAIT_MS, TimeUnit.MILLISECONDS)) {
            // A late service finds no pending request and stops itself.
            synchronized(this) { pending = null }
            return Started(false, "the recording service did not start within ${SERVICE_START_WAIT_MS / 1000} seconds")
        }
        return synchronized(this) { outcome } ?: Started(false, "the recording service reported nothing")
    }

    /** Stops and finalises the recording. Safe to call more than once. */
    fun stop(): Finished = finish(null)

    // ── Called by RecordingService, on the main thread ──────────────────────

    internal fun onServiceReady(service: Service, foregroundError: String?) {
        val request = synchronized(this) { pending.also { pending = null } }
        if (request == null) {
            service.stopSelf()
            return
        }
        val result = if (foregroundError != null) {
            Started(false, foregroundError)
        } else {
            try {
                val started = begin(service, request)
                synchronized(this) { session = started }
                Started(true, "recording")
            } catch (t: Throwable) {
                Started(false, "the screen capture could not start (${t.javaClass.simpleName}: ${t.message ?: "no detail"})")
            }
        }
        synchronized(this) { outcome = result }
        request.latch.countDown()
        if (!result.ok) service.stopSelf()
    }

    internal fun onServiceDestroyed() {
        finish("the recording service was stopped before the run ended")
    }

    private fun begin(service: Service, request: Pending): Session {
        val (width, height, dpi) = captureSize(service)
        request.file.delete()

        val manager = service.getSystemService(MediaProjectionManager::class.java)
        val projection = manager.getMediaProjection(request.consent.resultCode, request.consent.data)
            ?: throw IllegalStateException("the system returned no projection")

        // Android 14+ refuses a capture whose projection has no callback.
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                finish("the system ended the screen capture before the run finished")
            }
        }
        val main = Handler(Looper.getMainLooper())
        var codec: MediaCodec? = null
        var surface: Surface? = null
        var muxer: MediaMuxer? = null
        var display: VirtualDisplay? = null
        try {
            projection.registerCallback(callback, main)
            val encoder = MediaCodec.createEncoderByType(MIME)
            codec = encoder
            encoder.configure(encoderFormat(encoder, width, height), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = encoder.createInputSurface()
            val output = MediaMuxer(request.file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = output
            encoder.start()
            // finish() joins the drain thread, so it must not run on it.
            val drain = Drain(encoder, output) { reason -> main.post { finish(reason) } }
            display = projection.createVirtualDisplay(
                "automation-run-recording",
                width,
                height,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                null,
            )
            drain.start()
            return Session(
                file = request.file,
                service = service,
                projection = projection,
                callback = callback,
                codec = encoder,
                surface = surface,
                muxer = output,
                drain = drain,
                display = display,
                startedAtMs = System.currentTimeMillis(),
            )
        } catch (t: Throwable) {
            runCatching { display?.release() }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { surface?.release() }
            runCatching { muxer?.release() }
            runCatching { projection.unregisterCallback(callback) }
            runCatching { projection.stop() }
            request.file.delete()
            throw t
        }
    }

    @Synchronized
    private fun finish(reason: String?): Finished {
        val s = session ?: return finished ?: Finished(null, null, reason)
        session = null

        var note = reason
        var usable = true
        // No more frames, then let the encoder flush what it holds.
        runCatching { s.display.release() }
        runCatching { s.codec.signalEndOfInputStream() }
        s.drain.join(DRAIN_FINISH_WAIT_MS)
        if (s.drain.isAlive) {
            note = note ?: "the encoder did not finish within ${DRAIN_FINISH_WAIT_MS / 1000} seconds"
        }
        // Stopping the codec also ends a drain that is still waiting on it.
        runCatching { s.codec.stop() }
        s.drain.join(DRAIN_FINISH_WAIT_MS)
        s.drain.error?.let { note = note ?: it }
        try {
            // A muxer that never received a frame was never started and cannot stop.
            if (s.drain.isAlive || s.drain.samples == 0) {
                usable = false
                note = note ?: "the encoder produced no frames"
            } else {
                s.muxer.stop()
            }
        } catch (t: RuntimeException) {
            usable = false
            note = note ?: "the video could not be finalised (${t.message ?: t.javaClass.simpleName})"
        }
        runCatching { s.muxer.release() }
        runCatching { s.codec.release() }
        runCatching { s.surface.release() }
        runCatching { s.projection.unregisterCallback(s.callback) }
        runCatching { s.projection.stop() }
        runCatching { s.service.stopSelf() }

        if (usable && s.file.length() <= 0L) {
            usable = false
            note = note ?: "the video file was empty"
        }
        if (!usable) s.file.delete()
        return Finished(
            file = if (usable) s.file else null,
            startedAtMs = if (usable) s.startedAtMs else null,
            note = note,
        ).also { finished = it }
    }

    /**
     * H.264 at [BIT_RATE], variable where the encoder offers it (a still screen
     * then costs next to nothing), at most [FRAME_RATE] frames a second taken
     * from the display, a key frame every [KEY_FRAME_INTERVAL_S] seconds.
     */
    private fun encoderFormat(codec: MediaCodec, width: Int, height: Int): MediaFormat {
        val capabilities = codec.codecInfo.getCapabilitiesForType(MIME)
        val bitRate = capabilities.videoCapabilities?.bitrateRange?.clamp(BIT_RATE) ?: BIT_RATE
        return MediaFormat.createVideoFormat(MIME, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            if (capabilities.encoderCapabilities?.isBitrateModeSupported(
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR,
                ) == true
            ) {
                setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            }
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            // The display draws up to 60 frames a second while it animates.
            setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, FRAME_RATE.toFloat())
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, KEY_FRAME_INTERVAL_S)
        }
    }

    /** Real display size scaled down to [MAX_SHORT_SIDE], aligned for the encoder. */
    private fun captureSize(context: Context): Triple<Int, Int, Int> {
        val windowManager = context.getSystemService(WindowManager::class.java)
        val (width, height) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }
        val scale = min(1f, MAX_SHORT_SIDE.toFloat() / min(width, height))
        fun align(value: Int) = max(ALIGN, (value * scale / ALIGN).roundToInt() * ALIGN)
        return Triple(align(width), align(height), context.resources.displayMetrics.densityDpi)
    }
}

/**
 * Foreground service that owns the capture. Android 14+ only allows a
 * MediaProjection to run inside a service started with the `mediaProjection`
 * foreground type, after the operator has accepted the system dialog.
 */
class RecordingService : Service() {

    private companion object {
        const val CHANNEL_ID = "run_recording"
        const val NOTIFICATION_ID = 4712
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val error = try {
            startForeground(
                NOTIFICATION_ID,
                notification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
            null
        } catch (t: Throwable) {
            "the recording service could not go to the foreground (${t.javaClass.simpleName})"
        }
        ScreenRecorder.onServiceReady(this, error)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ScreenRecorder.onServiceDestroyed()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Run recording", NotificationManager.IMPORTANCE_LOW),
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("Automation Tool")
            .setContentText("Recording the screen for this test run")
            .setOngoing(true)
            .build()
    }
}
