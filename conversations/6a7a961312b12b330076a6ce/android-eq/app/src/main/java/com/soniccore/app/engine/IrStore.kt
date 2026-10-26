package com.soniccore.app.engine

import android.content.Context
import com.soniccore.app.dsp.NeonDsp
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The impulse response currently loaded into the Convolver stage, and where it
 * is kept between launches.
 *
 * The engine holds the taps in native memory, which a process restart clears, so
 * the decoded taps are stored in the app's own files directory and handed back
 * to [NeonDsp.loadIr] on the next launch. The *file the user picked* is not
 * needed again — storing the decoded taps means the response keeps working if
 * the original file is moved, renamed or on a card that is no longer inserted.
 *
 * [encode]/[decode] are pure and JVM-tested (`IrStoreTest`); the file I/O around
 * them is the thin Android part. A damaged or foreign file decodes to `null`, so
 * a corrupt store can never feed the convolver.
 */
data class StoredImpulse(
    /** Display name of the file it came from, already sanitised. */
    val name: String,
    val left: FloatArray,
    val right: FloatArray,
    /** The file's own rate, before any resampling. */
    val sourceSampleRate: Int,
    /** How many frames the file held, before truncation to [ImpulseResponse.MAX_TAPS]. */
    val sourceFrames: Int,
    val peak: Float
) {
    val taps: Int get() = left.size
}

object IrStore {

    const val FILE_NAME = "impulse-response.bin"

    private const val MAGIC = "SCIR"
    private const val VERSION: Byte = 1
    private const val MAX_NAME_CHARS = 60
    private const val HEADER_BYTES = 4 + 1 + 2 + 4 + 4 + 4 + 4

    /** What the Convolver card says about the loaded response — or why it is empty. */
    fun describe(stored: StoredImpulse?): String {
        if (stored == null) {
            return "No impulse response loaded — the stage passes audio through unchanged."
        }
        return "${stored.name} · ${stored.taps} taps · ${ImpulseResponse.kilo(stored.sourceSampleRate)} source"
    }

    /** The decodable form of a successful [ImpulseResponse.read]. */
    fun from(read: ImpulseResponse.Read.Ok, name: String): StoredImpulse = StoredImpulse(
        name = sanitizeName(name),
        left = read.left,
        right = read.right,
        sourceSampleRate = read.sourceSampleRate,
        sourceFrames = read.sourceFrames,
        peak = read.peak
    )

    /** A file name is untrusted input: no path separators, quotes or control characters. */
    fun sanitizeName(raw: String): String {
        val cleaned = raw
            .filter { !it.isISOControl() && it != '/' && it != '\\' && it != '"' && it != '\'' }
            .trim()
            .trimStart('.')
            .ifBlank { "impulse-response.wav" }
        return if (cleaned.length > MAX_NAME_CHARS) cleaned.take(MAX_NAME_CHARS) else cleaned
    }

    fun encode(value: StoredImpulse): ByteArray {
        val taps = minOf(value.left.size, value.right.size)
        val nameBytes = value.name.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocate(HEADER_BYTES + nameBytes.size + taps * 2 * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(MAGIC.toByteArray(Charsets.US_ASCII))
        buffer.put(VERSION)
        buffer.putShort(nameBytes.size.toShort())
        buffer.putInt(value.sourceSampleRate)
        buffer.putInt(value.sourceFrames)
        buffer.putInt(value.peak.toRawBits())
        buffer.putInt(taps)
        buffer.put(nameBytes)
        for (i in 0 until taps) buffer.putFloat(value.left[i])
        for (i in 0 until taps) buffer.putFloat(value.right[i])
        return buffer.array()
    }

    fun decode(bytes: ByteArray): StoredImpulse? {
        if (bytes.size < HEADER_BYTES) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(4)
        buffer.get(magic)
        if (String(magic, Charsets.US_ASCII) != MAGIC) return null
        if (buffer.get() != VERSION) return null
        val nameLength = buffer.short.toInt() and 0xFFFF
        if (nameLength <= 0 || nameLength > bytes.size) return null
        val sourceSampleRate = buffer.int
        val sourceFrames = buffer.int
        val peak = Float.fromBits(buffer.int)
        val taps = buffer.int
        if (taps <= 0 || taps > ImpulseResponse.MAX_TAPS) return null
        if (buffer.remaining() < nameLength + taps * 2 * 4) return null
        val nameBytes = ByteArray(nameLength)
        buffer.get(nameBytes)
        val left = FloatArray(taps)
        val right = FloatArray(taps)
        for (i in 0 until taps) left[i] = buffer.float
        for (i in 0 until taps) right[i] = buffer.float
        if (left.any { !it.isFinite() } || right.any { !it.isFinite() }) return null
        return StoredImpulse(
            name = String(nameBytes, Charsets.UTF_8),
            left = left,
            right = right,
            sourceSampleRate = sourceSampleRate,
            sourceFrames = sourceFrames,
            peak = peak
        )
    }

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    /** Writes atomically: a crash mid-write can never leave a half response behind. */
    fun save(context: Context, value: StoredImpulse): Boolean = runCatching {
        val target = file(context)
        val temp = File(target.parentFile, "$FILE_NAME.tmp")
        temp.writeBytes(encode(value))
        if (!temp.renameTo(target)) {
            target.writeBytes(temp.readBytes())
            temp.delete()
        }
        true
    }.getOrDefault(false)

    fun load(context: Context): StoredImpulse? = runCatching {
        val target = file(context)
        if (!target.isFile) null else decode(target.readBytes())
    }.getOrNull()

    fun clear(context: Context): Boolean = runCatching { file(context).delete() }.getOrDefault(false)

    /**
     * Hands the stored response back to the native convolver after a restart.
     *
     * Returns false when there is nothing stored, the engine is unavailable or
     * the write could not happen — the caller never assumes it landed.
     */
    fun restoreIntoEngine(context: Context): Boolean {
        if (!NeonDsp.available) return false
        val stored = load(context) ?: return false
        return runCatching {
            NeonDsp.loadIr(stored.left, stored.right)
            true
        }.getOrDefault(false)
    }
}
