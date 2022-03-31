package com.arejaybee.pokemon_watch.pokemon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.media.MediaPlayer
import android.util.Log
import com.arejaybee.pokemon_watch.PreferenceUtil
import java.util.*
import kotlin.random.Random

open class Pokemon(
    private val context: Context,
    private var id: String,
    private var isEgg: Boolean,
    private var isShiny: Boolean
) : SensorEventListener {
    private val TAG = "Pokemon"
    private val levelUpPlayer: MediaPlayer
    private var cryPlayer: MediaPlayer? = null

    enum class STATUS { NONE, HAPPY }

    private val SPRITE_PREFIX = "sprite_"
    private val SHINY_SUFFIX = "s"
    private val EGG_SUFFIX = "_egg"
    val level: Int
        get() {
            return experience / 100
        }

    var experience: Int = 0
    var name = ""
    lateinit var sprite: Bitmap
    var currentDay: Calendar
    var lastStep: Calendar

    init {
        translatePokemon()
        val packageName: String = context.packageName
        levelUpPlayer = MediaPlayer.create(
            context,
            context.resources.getIdentifier("level_up", "raw", packageName)
        )
        currentDay = Calendar.getInstance()
        lastStep = Calendar.getInstance()
    }

    /**
     * Called each redraw on the watch. Updates the status and evolves the pokemon if its time
     */
    fun onTick() {
        updateStatus()
        evolveIfAble()
    }

    /**
     * Used to get the pokemon's battle cry
     */
    fun cry() {
        cryPlayer?.start()
    }

    /**
     * Translates an ID to an actual pokmeon with a sprite and such for the user to see
     */
    private fun translatePokemon() {
        val packageName: String = context.packageName
        val spriteID =
            SPRITE_PREFIX + id + (if (isEgg) EGG_SUFFIX else if (isShiny) SHINY_SUFFIX else "")
        val resID = context.resources.getIdentifier(spriteID, "drawable", packageName)
        try {
            sprite = BitmapFactory.decodeResource(context.resources, resID)
            if (isEgg) {
                name = "Egg"
            } else {
                name = PokemonMap.map[id]?.name ?: "Unown"
                val cryFileName = "cry_" + name.lowercase().replace("-", "_")
                cryPlayer = MediaPlayer.create(
                    context,
                    context.resources.getIdentifier(cryFileName, "raw", packageName)
                )
            }

        } catch (err: NullPointerException) {
            isEgg = false
            translatePokemon()
        }
    }

    /**
     * Will create a new pokemon if we are at midnight. In the future, I want to build this out to allow status effects as well
     */
    private fun updateStatus() {
        if (Calendar.getInstance()
                .get(Calendar.DAY_OF_YEAR) != currentDay.get(Calendar.DAY_OF_YEAR)
        ) {
            currentDay = Calendar.getInstance()
            experience = 0
            id = PreferenceUtil.getPokemonToday(context)
            isEgg = canBeEgg()
            isShiny = Random.nextInt(100) == 12
            translatePokemon()
        }
    }

    /**
     * Checks if the pokemon has an egg, mostly necessary for legendaries
     */
    private fun canBeEgg(): Boolean {
        val packageName: String = context.packageName
        val spriteID = SPRITE_PREFIX + id + EGG_SUFFIX
        return context.resources.getIdentifier(spriteID, "drawable", packageName) != 0
    }

    /**
     * Evolves the pokemon if they are at the appropriate level
     */
    private fun evolveIfAble() {
        if (canEvolve()) {
            Log.i(TAG, "Pokemon can evolve!")
            levelUpPlayer.start()
            id = if (isEgg) id else PokemonMap.getEvolution(id)
            isEgg = false
            translatePokemon()
        }
    }

    /**
     * Checks if the pokemon can evolve
     * @return true if they have an evolution, or if they are an egg at the appropriate step count
     */
    private fun canEvolve(): Boolean {
        return if (isEgg) {
            val evolutionLevel = PokemonMap.map[id]?.evolutionLevel!!
            //Pokemon that do not evolve will take 2500 steps to hatch
            if (evolutionLevel == -1) {
                level >= 25
            }
            //Pokemon that can evolve will do so at 500 steps
            level >= 5
        } else {
            try {
                val evolutionLevel = PokemonMap.map[id]?.evolutionLevel!!
                if (evolutionLevel == -1) {
                    false //Pokemon has no evolution
                } else {
                    level >= evolutionLevel
                }
            } catch (err: Exception) {
                false //Not sure why this would would ever be null or return an error, but safety first
            }
        }
    }

    /**
     * Called when the sensor reports an update
     */
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val steps = event.values[0].toInt()
            val milestone = PreferenceUtil.updateSteps(context, steps)
            this.experience += milestone
            Log.i(TAG, "Updated experience - $experience")
        }
    }

    /**
     * Required but not used
     */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        print("ROBERT - Accuracy")
    }
}