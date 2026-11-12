package com.vasilisneo.trackstar.data.workout

// The bundled exercise library (free-exercise-db, public domain — github.com/yuhonas/free-exercise-db):
// ~870 exercises with muscles, equipment, instructions and two photos each (start / end position).
// Same data file as iOS (assets/exercise_catalog.json); photos are served from the support site and
// cached by Coil. Plans still store only the exercise name, so lookups go by name — the picker's
// familiar names ("Bench Press") resolve through `aliases`. Mirrors ExerciseCatalog.swift.

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class CatalogExercise(
    val id: String,
    val name: String,
    val category: String,
    val equipment: String?,
    val level: String?,
    val mechanic: String?,
    val force: String?,
    val primary: List<String> = emptyList(),
    val secondary: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val images: Int = 0,
) {
    val muscleGroup: MuscleGroup get() = MuscleGroup.of(primary.firstOrNull())

    fun imageUrl(index: Int): String = "${ExerciseCatalog.imageBase}$id/$index.jpg"
    val thumbnailUrl: String get() = "${ExerciseCatalog.imageBase}$id/thumb.jpg"
}

/** Coarse muscle groups for the picker's filter chips. */
enum class MuscleGroup(val label: String) {
    Chest("Chest"), Back("Back"), Shoulders("Shoulders"), Arms("Arms"),
    Legs("Legs"), Glutes("Glutes"), Core("Core"), Other("Other");

    companion object {
        fun of(muscle: String?): MuscleGroup = when (muscle) {
            "chest" -> Chest
            "lats", "middle back", "lower back", "traps" -> Back
            "shoulders", "neck" -> Shoulders
            "biceps", "triceps", "forearms" -> Arms
            "quadriceps", "hamstrings", "calves", "adductors", "abductors" -> Legs
            "glutes" -> Glutes
            "abdominals" -> Core
            else -> Other
        }
    }
}

object ExerciseCatalog {
    var imageBase: String = "https://trackstar.fitness/exercises/"
        private set
    var exercises: List<CatalogExercise> = emptyList()
        private set
    /** Library ids already shown under a familiar picker name (hidden from the library list). */
    var aliasedIds: Set<String> = emptySet()
        private set

    private var byId: Map<String, CatalogExercise> = emptyMap()
    private var byName: Map<String, CatalogExercise> = emptyMap()
    private var aliases: Map<String, String> = emptyMap()
    @Volatile private var loaded = false

    private data class File(
        @SerializedName("imageBase") val imageBase: String,
        @SerializedName("exercises") val exercises: List<CatalogExercise>,
        @SerializedName("aliases") val aliases: Map<String, String>,
    )

    /** Parses the bundled file once (call off the main thread; ~0.8 MB). */
    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        runCatching {
            context.assets.open("exercise_catalog.json").bufferedReader().use { reader ->
                val file = Gson().fromJson(reader, File::class.java)
                imageBase = file.imageBase
                exercises = file.exercises
                byId = file.exercises.associateBy { it.id }
                byName = file.exercises.associateBy { it.name.lowercase() }
                aliases = file.aliases.mapKeys { it.key.lowercase() }
                aliasedIds = file.aliases.values.toSet()
            }
        }
        loaded = true
    }

    /** The library entry for an exercise name — a library name or a familiar picker name. */
    fun exercise(named: String): CatalogExercise? {
        val key = named.trim().lowercase()
        return aliases[key]?.let { byId[it] } ?: byName[key]
    }

    fun equipmentLabel(raw: String): String = when (raw) {
        "body only" -> "Bodyweight"
        "e-z curl bar" -> "EZ bar"
        "kettlebells" -> "Kettlebell"
        "bands" -> "Bands"
        else -> raw.replaceFirstChar { it.uppercase() }
    }
}
