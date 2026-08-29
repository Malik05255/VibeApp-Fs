package com.almi.ai.data.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Visual presentation chosen for the avatar. This does not alter body measurements. */
enum class AvatarPresentation { MASCULINE, FEMININE }

data class AvatarAppearance(
    val presentation: AvatarPresentation = AvatarPresentation.FEMININE,
    val hairVariant: String = "bob",
    val hairColor: String = "2C1B18",
    val skinColor: String = "F8D5C2",
    val accessoriesVariant: String = "none",
    val facialHairVariant: String = "none",
    val eyesVariant: String = "default",
    val eyebrowsVariant: String = "default",
    val mouthVariant: String = "smile",
    val seed: String = "almi-avatar-v9",
)

@Singleton
class AvatarAppearanceStore @Inject constructor(
    @ApplicationContext context: Context,
    private val bodyProfileStore: BodyProfileStore,
) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _appearance = MutableStateFlow(read())
    val appearance: StateFlow<AvatarAppearance> = _appearance.asStateFlow()

    private val _savedLooks = MutableStateFlow(readSavedLooks())
    val savedLooks: StateFlow<Map<Int, AvatarAppearance>> = _savedLooks.asStateFlow()

    fun setPresentation(value: AvatarPresentation) = update {
        val presetHair = when (value) {
            AvatarPresentation.FEMININE -> if (it.hairVariant in masculineHair) "bob" else it.hairVariant
            AvatarPresentation.MASCULINE -> if (it.hairVariant in feminineHair) "shortFlat" else it.hairVariant
        }
        it.copy(
            presentation = value,
            hairVariant = presetHair,
            facialHairVariant = if (value == AvatarPresentation.FEMININE) "none" else it.facialHairVariant,
        )
    }

    fun setHairVariant(value: String) = update { it.copy(hairVariant = value) }
    fun setHairColor(value: String) = update { it.copy(hairColor = value) }
    fun setSkinColor(value: String) = update { it.copy(skinColor = value) }
    fun setAccessoriesVariant(value: String) = update { it.copy(accessoriesVariant = value) }
    fun setFacialHairVariant(value: String) = update { it.copy(facialHairVariant = value) }
    fun setEyesVariant(value: String) = update { it.copy(eyesVariant = value) }
    fun setEyebrowsVariant(value: String) = update { it.copy(eyebrowsVariant = value) }
    fun setMouthVariant(value: String) = update { it.copy(mouthVariant = value) }

    fun applyPreset(name: String) = update { current ->
        when (name) {
            "clean" -> current.copy(
                hairVariant = if (current.presentation == AvatarPresentation.FEMININE) "bob" else "shortFlat",
                hairColor = "241A19",
                accessoriesVariant = "none",
                facialHairVariant = "none",
                eyesVariant = "default",
                eyebrowsVariant = "default",
                mouthVariant = "neutral",
            )
            "street" -> current.copy(
                hairVariant = "shortCurly",
                hairColor = "171312",
                accessoriesVariant = "cap",
                facialHairVariant = if (current.presentation == AvatarPresentation.MASCULINE) "beardLight" else "none",
                eyesVariant = "sharp",
                eyebrowsVariant = "defined",
                mouthVariant = "neutral",
            )
            "editorial" -> current.copy(
                hairVariant = if (current.presentation == AvatarPresentation.FEMININE) "longButNotTooLong" else "shortFlat",
                hairColor = "5D382C",
                accessoriesVariant = "round",
                facialHairVariant = "none",
                eyesVariant = "wide",
                eyebrowsVariant = "defined",
                mouthVariant = "full",
            )
            else -> current
        }
    }

    fun randomizeIdentity() = update { current ->
        current.copy(seed = "almi-avatar-${System.currentTimeMillis()}")
    }

    /** Three tiny persistent look slots avoid a database and keep avatar switching instant. */
    fun saveLook(slot: Int) {
        if (slot !in 1..3) return
        val value = _appearance.value
        preferences.edit().putString(lookKey(slot), encode(value)).apply()
        _savedLooks.value = _savedLooks.value.toMutableMap().apply { put(slot, value) }
    }

    fun applyLook(slot: Int) {
        val value = _savedLooks.value[slot] ?: return
        persist(value)
    }

    fun currentPromptContext(): String? {
        if (bodyProfileStore.journeyMode.value != JourneyMode.AVATAR) return null
        val current = _appearance.value
        return buildString {
            append("Avatar appearance chosen by the user: ")
            append("presentation=${current.presentation.name.lowercase()}, ")
            append("hair=${current.hairVariant}, hairColor=#${current.hairColor}, ")
            append("skinTone=#${current.skinColor}, accessory=${current.accessoriesVariant}, ")
            append("facialHair=${current.facialHairVariant}, eyes=${current.eyesVariant}, ")
            append("eyebrows=${current.eyebrowsVariant}, mouth=${current.mouthVariant}. ")
            append("Preserve these appearance choices while keeping body proportions from the digital twin. ")
            append("Do not change body measurements to match the face, hairstyle or accessories.")
        }
    }

    private fun update(transform: (AvatarAppearance) -> AvatarAppearance) {
        persist(transform(_appearance.value))
    }

    private fun persist(next: AvatarAppearance) {
        preferences.edit()
            .putString(KEY_PRESENTATION, next.presentation.name)
            .putString(KEY_HAIR, next.hairVariant)
            .putString(KEY_HAIR_COLOR, next.hairColor)
            .putString(KEY_SKIN, next.skinColor)
            .putString(KEY_ACCESSORIES, next.accessoriesVariant)
            .putString(KEY_FACIAL_HAIR, next.facialHairVariant)
            .putString(KEY_EYES, next.eyesVariant)
            .putString(KEY_EYEBROWS, next.eyebrowsVariant)
            .putString(KEY_MOUTH, next.mouthVariant)
            .putString(KEY_SEED, next.seed)
            .apply()
        _appearance.value = next
    }

    private fun read(): AvatarAppearance = AvatarAppearance(
        presentation = preferences.getString(KEY_PRESENTATION, null)
            ?.let { runCatching { AvatarPresentation.valueOf(it) }.getOrNull() }
            ?: AvatarPresentation.FEMININE,
        hairVariant = preferences.getString(KEY_HAIR, "bob") ?: "bob",
        hairColor = preferences.getString(KEY_HAIR_COLOR, "2C1B18") ?: "2C1B18",
        skinColor = preferences.getString(KEY_SKIN, "F8D5C2") ?: "F8D5C2",
        accessoriesVariant = preferences.getString(KEY_ACCESSORIES, "none") ?: "none",
        facialHairVariant = preferences.getString(KEY_FACIAL_HAIR, "none") ?: "none",
        eyesVariant = preferences.getString(KEY_EYES, "default") ?: "default",
        eyebrowsVariant = preferences.getString(KEY_EYEBROWS, "default") ?: "default",
        mouthVariant = preferences.getString(KEY_MOUTH, "smile") ?: "smile",
        seed = preferences.getString(KEY_SEED, "almi-avatar-v9") ?: "almi-avatar-v9",
    )

    private fun readSavedLooks(): Map<Int, AvatarAppearance> = buildMap {
        for (slot in 1..3) {
            preferences.getString(lookKey(slot), null)?.let(::decode)?.let { put(slot, it) }
        }
    }

    private fun encode(value: AvatarAppearance): String = listOf(
        value.presentation.name,
        value.hairVariant,
        value.hairColor,
        value.skinColor,
        value.accessoriesVariant,
        value.facialHairVariant,
        value.eyesVariant,
        value.eyebrowsVariant,
        value.mouthVariant,
        value.seed,
    ).joinToString("|")

    private fun decode(raw: String): AvatarAppearance? {
        val parts = raw.split('|')
        if (parts.size != 10) return null
        val presentation = runCatching { AvatarPresentation.valueOf(parts[0]) }.getOrNull() ?: return null
        return AvatarAppearance(
            presentation = presentation,
            hairVariant = parts[1],
            hairColor = parts[2],
            skinColor = parts[3],
            accessoriesVariant = parts[4],
            facialHairVariant = parts[5],
            eyesVariant = parts[6],
            eyebrowsVariant = parts[7],
            mouthVariant = parts[8],
            seed = parts[9],
        )
    }

    private fun lookKey(slot: Int): String = "look_$slot"

    companion object {
        private const val PREFS = "almi_avatar_appearance_v7"
        private const val KEY_PRESENTATION = "presentation"
        private const val KEY_HAIR = "hair"
        private const val KEY_HAIR_COLOR = "hair_color"
        private const val KEY_SKIN = "skin"
        private const val KEY_ACCESSORIES = "accessories"
        private const val KEY_FACIAL_HAIR = "facial_hair"
        private const val KEY_EYES = "eyes"
        private const val KEY_EYEBROWS = "eyebrows"
        private const val KEY_MOUTH = "mouth"
        private const val KEY_SEED = "seed"

        private val masculineHair = setOf("shortFlat", "shortRound", "shortCurly", "shortWaved", "theCaesar", "sides")
        private val feminineHair = setOf("longButNotTooLong", "bob", "curvy", "straight01", "straight02", "bigHair", "bun")
    }
}
