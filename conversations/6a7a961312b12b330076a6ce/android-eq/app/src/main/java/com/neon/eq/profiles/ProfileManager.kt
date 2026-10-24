package com.neon.eq.profiles

import org.json.JSONArray
import org.json.JSONObject

data class SonicCoreAudioProfile(
    val id: String,
    val name: String,
    val bands: IntArray, // 10 bands
    val preampDb: Float = 0f,
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    val loudness: Int = 0,
    val peqEnabled: Boolean = false,
    val peqFreq: Float = 1000f,
    val peqGainDb: Float = 0f,
    val peqQ: Float = 1.0f,
    val limiterEnabled: Boolean = true,
    val limiterCeilingDb: Float = -1.0f,
    val compressorEnabled: Boolean = false,
    val compressorThresholdDb: Float = -18f,
    val compressorRatio: Float = 4.0f,
    val stereoWidth: Float = 1.0f,
    val themeId: String = "cyan_dream",
    val glassIntensity: String = "MEDIUM",
    val isFavorite: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SonicCoreAudioProfile) return false
        return id == other.id &&
                name == other.name &&
                bands.contentEquals(other.bands) &&
                preampDb == other.preampDb &&
                bassBoost == other.bassBoost &&
                virtualizer == other.virtualizer &&
                loudness == other.loudness &&
                peqEnabled == other.peqEnabled &&
                peqFreq == other.peqFreq &&
                peqGainDb == other.peqGainDb &&
                peqQ == other.peqQ &&
                limiterEnabled == other.limiterEnabled &&
                limiterCeilingDb == other.limiterCeilingDb &&
                compressorEnabled == other.compressorEnabled &&
                compressorThresholdDb == other.compressorThresholdDb &&
                compressorRatio == other.compressorRatio &&
                stereoWidth == other.stereoWidth &&
                themeId == other.themeId &&
                glassIntensity == other.glassIntensity &&
                isFavorite == other.isFavorite
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + bands.contentHashCode()
        result = 31 * result + preampDb.hashCode()
        return result
    }
}

object ProfileManager {

    private const val MAX_JSON_CHARS = 256 * 1024 // 256 KB
    const val SCHEMA_VERSION = 1

    fun exportToJson(profile: SonicCoreAudioProfile): String {
        val root = JSONObject()
        root.put("schema_version", SCHEMA_VERSION)
        root.put("type", "soniccore_profile")
        root.put("id", profile.id)
        root.put("name", profile.name)

        val bandsArr = JSONArray()
        for (b in profile.bands) {
            bandsArr.put(b)
        }
        root.put("bands", bandsArr)
        root.put("preamp_db", profile.preampDb.toDouble())
        root.put("bass_boost", profile.bassBoost)
        root.put("virtualizer", profile.virtualizer)
        root.put("loudness", profile.loudness)

        root.put("peq_enabled", profile.peqEnabled)
        root.put("peq_freq", profile.peqFreq.toDouble())
        root.put("peq_gain_db", profile.peqGainDb.toDouble())
        root.put("peq_q", profile.peqQ.toDouble())

        root.put("limiter_enabled", profile.limiterEnabled)
        root.put("limiter_ceiling_db", profile.limiterCeilingDb.toDouble())
        root.put("compressor_enabled", profile.compressorEnabled)
        root.put("compressor_threshold_db", profile.compressorThresholdDb.toDouble())
        root.put("compressor_ratio", profile.compressorRatio.toDouble())
        root.put("stereo_width", profile.stereoWidth.toDouble())

        root.put("theme_id", profile.themeId)
        root.put("glass_intensity", profile.glassIntensity)
        root.put("is_favorite", profile.isFavorite)

        return root.toString(2)
    }

    fun exportBundleToJson(profiles: List<SonicCoreAudioProfile>): String {
        val root = JSONObject()
        root.put("schema_version", SCHEMA_VERSION)
        root.put("type", "soniccore_profile_bundle")
        val arr = JSONArray()
        for (p in profiles) {
            arr.put(JSONObject(exportToJson(p)))
        }
        root.put("profiles", arr)
        return root.toString(2)
    }

    /**
     * Validates and imports a JSON profile string.
     * Enforces schema constraints and clamps all parameters to safe boundaries.
     * Returns null if JSON is invalid or corrupted.
     */
    fun importFromJson(jsonStr: String): SonicCoreAudioProfile? {
        if (jsonStr.isBlank() || jsonStr.length > MAX_JSON_CHARS) return null

        return try {
            val root = JSONObject(jsonStr)
            val name = root.optString("name", "Imported Profile").trim().take(64)
            if (name.isEmpty()) return null

            val id = root.optString("id", "profile_${System.currentTimeMillis()}").take(64)

            // Bands validation (10 bands expected; convert if different length)
            val bandsArr = root.optJSONArray("bands") ?: return null
            val rawBands = IntArray(bandsArr.length()) { i ->
                bandsArr.optInt(i, 0).coerceIn(-20, 20)
            }
            val finalBands = if (rawBands.size == 10) {
                rawBands
            } else {
                // Map/resample to 10 bands
                val res = IntArray(10)
                for (i in 0 until 10) {
                    val srcIdx = (i * rawBands.size) / 10
                    res[i] = rawBands.getOrElse(srcIdx) { 0 }
                }
                res
            }

            val preamp = root.optDouble("preamp_db", 0.0).toFloat().coerceIn(-20f, 20f)
            val bass = root.optInt("bass_boost", 0).coerceIn(0, 1000)
            val virt = root.optInt("virtualizer", 0).coerceIn(0, 1000)
            val loud = root.optInt("loudness", 0).coerceIn(0, 1000)

            val peqEn = root.optBoolean("peq_enabled", false)
            val peqF = root.optDouble("peq_freq", 1000.0).toFloat().coerceIn(20f, 20000f)
            val peqG = root.optDouble("peq_gain_db", 0.0).toFloat().coerceIn(-20f, 20f)
            val peqQ = root.optDouble("peq_q", 1.0).toFloat().coerceIn(0.1f, 20f)

            val limEn = root.optBoolean("limiter_enabled", true)
            val limCeil = root.optDouble("limiter_ceiling_db", -1.0).toFloat().coerceIn(-30f, 0f)

            val compEn = root.optBoolean("compressor_enabled", false)
            val compThresh = root.optDouble("compressor_threshold_db", -18.0).toFloat().coerceIn(-60f, 0f)
            val compRatio = root.optDouble("compressor_ratio", 4.0).toFloat().coerceIn(1.0f, 20f)
            val stereo = root.optDouble("stereo_width", 1.0).toFloat().coerceIn(0f, 2.0f)

            val theme = root.optString("theme_id", "cyan_dream").take(32)
            val glass = when (root.optString("glass_intensity", "MEDIUM").uppercase()) {
                "LOW" -> "LOW"
                "HIGH" -> "HIGH"
                else -> "MEDIUM"
            }
            val isFav = root.optBoolean("is_favorite", false)

            SonicCoreAudioProfile(
                id = id,
                name = name,
                bands = finalBands,
                preampDb = preamp,
                bassBoost = bass,
                virtualizer = virt,
                loudness = loud,
                peqEnabled = peqEn,
                peqFreq = peqF,
                peqGainDb = peqG,
                peqQ = peqQ,
                limiterEnabled = limEn,
                limiterCeilingDb = limCeil,
                compressorEnabled = compEn,
                compressorThresholdDb = compThresh,
                compressorRatio = compRatio,
                stereoWidth = stereo,
                themeId = theme,
                glassIntensity = glass,
                isFavorite = isFav
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun importBundleFromJson(jsonStr: String): List<SonicCoreAudioProfile> {
        if (jsonStr.isBlank() || jsonStr.length > MAX_JSON_CHARS) return emptyList()

        return try {
            val root = JSONObject(jsonStr)
            val arr = root.optJSONArray("profiles") ?: return emptyList()
            val result = mutableListOf<SonicCoreAudioProfile>()
            for (i in 0 until arr.length()) {
                val itemObj = arr.optJSONObject(i) ?: continue
                val prof = importFromJson(itemObj.toString())
                if (prof != null) {
                    result.add(prof)
                }
            }
            result
        } catch (_: Throwable) {
            emptyList()
        }
    }
}
