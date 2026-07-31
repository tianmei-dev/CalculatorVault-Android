package com.aurora.calculatorvault.feature.disguise.shortcut

import android.content.Context
import androidx.core.graphics.drawable.IconCompat
import com.aurora.calculatorvault.R
import com.aurora.calculatorvault.feature.disguise.domain.DisguiseIconId

interface DisguiseShortcutIconFactory {
    fun create(iconId: DisguiseIconId): IconCompat?
}

class ResourceDisguiseShortcutIconFactory(
    private val context: Context,
) : DisguiseShortcutIconFactory {
    override fun create(iconId: DisguiseIconId): IconCompat? = runCatching {
        IconCompat.createWithResource(context, iconId.drawableRes())
    }.getOrNull()
}

internal fun DisguiseIconId.drawableRes(): Int = when (this) {
    DisguiseIconId.Files -> R.drawable.ic_disguise_files
    DisguiseIconId.Photos -> R.drawable.ic_disguise_photos
    DisguiseIconId.Browser -> R.drawable.ic_disguise_browser
    DisguiseIconId.Settings -> R.drawable.ic_disguise_settings
    DisguiseIconId.Video -> R.drawable.ic_disguise_video
    DisguiseIconId.Music -> R.drawable.ic_disguise_music
    DisguiseIconId.Tools -> R.drawable.ic_disguise_tools
    DisguiseIconId.Weather -> R.drawable.ic_disguise_weather
    DisguiseIconId.Calendar -> R.drawable.ic_disguise_calendar
    DisguiseIconId.Calculator -> R.drawable.ic_disguise_calculator
}

