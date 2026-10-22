package com.droptuner.tuner

import android.content.Context
import androidx.core.content.edit
import com.droptuner.music.Note
import com.droptuner.music.Tuning
import com.droptuner.music.Tunings

class TunerPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("tuner", Context.MODE_PRIVATE)

    var a4: Int
        get() = prefs.getInt(KEY_A4, Note.A4_HZ.toInt())
        set(value) = prefs.edit { putInt(KEY_A4, value) }

    var strobe: Boolean
        get() = prefs.getBoolean(KEY_STROBE, false)
        set(value) = prefs.edit { putBoolean(KEY_STROBE, value) }

    var tuningId: String?
        get() = prefs.getString(KEY_TUNING, null)
        set(value) = prefs.edit { putString(KEY_TUNING, value) }

    fun tuning(): Tuning =
        (if (tuningId == Tunings.CUSTOM_ID) custom() else Tunings.byId(tuningId)) ?: Tunings.default

    fun custom(): Tuning? =
        prefs.getString(KEY_CUSTOM, null)
            ?.split(",")
            ?.mapNotNull { midi -> midi.toIntOrNull()?.let { Note(it) } }
            ?.takeIf { it.isNotEmpty() }
            ?.let(Tunings::custom)

    fun saveCustom(tuning: Tuning) = prefs.edit {
        putString(KEY_CUSTOM, tuning.strings.joinToString(",") { it.midi.toString() })
    }

    private companion object {
        const val KEY_A4 = "a4"
        const val KEY_TUNING = "tuning"
        const val KEY_CUSTOM = "custom"
        const val KEY_STROBE = "strobe"
    }
}
