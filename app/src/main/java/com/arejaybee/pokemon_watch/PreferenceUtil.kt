package com.arejaybee.pokemon_watch

import android.content.Context
import android.util.Log
import com.arejaybee.pokemon_watch.pokemon.Pokemon
import com.arejaybee.pokemon_watch.pokemon.PokemonMap
import java.util.*
import kotlin.random.Random

object PreferenceUtil {

    private const val WRITABLE_FILE = "step_file"
    private const val STEP_PREFIX = "step"
    private const val DAILY_STEP_PREFIX = "daily_step"
    private const val POKEMON_PREFIX = "pokemon"

    /**
     * Updates the saved number of steps. This will be called every time we get a new measurement from the watch.
     * Watch stores steps from the last time it was booted, so we store that and check daily.
     * TODO - NOTE this assumes users are not rebooting their watch each day. Not sure how to handle that.
     */
    fun updateSteps(context: Context, totalStepSinceReboot: Int): Int {
        var lastMeasuredTotal = getPreference(context, today(STEP_PREFIX))

        return if (lastMeasuredTotal == 0) {
            lastMeasuredTotal = totalStepSinceReboot
            savePreferences(context, today(STEP_PREFIX), lastMeasuredTotal)
            Log.i("TAG", "Your today step now is $lastMeasuredTotal")
            0
        } else {
            val additionStep = totalStepSinceReboot - lastMeasuredTotal
            val dailyStep = getPreference(context, today(DAILY_STEP_PREFIX))
            Log.i("TAG", "Your today step now is $additionStep")

            //update the measured total as well as our daily number of steps
            savePreferences(context, today(STEP_PREFIX), lastMeasuredTotal + additionStep)
            savePreferences(context, today(DAILY_STEP_PREFIX), dailyStep + additionStep)
            additionStep
        }
    }

    /**
     * Gets the day's pokemon. If one is not already saved, we get a random one then save it.
     */
    fun getPokemonToday(context: Context): String {
        val num: Int = if (hasPokemonToday(context)) {
            getPreference(context, today("pokemon"))
        } else {
            PokemonMap.getRandomPokemonId()
        }
        var id = num.toString()
        while (id.length < 3) {
            id = "0$id"
        }
        savePreferences(context, today(POKEMON_PREFIX), num)
        return id
    }

    /**
     * Gets today's exp
     */
    fun getInitialExp(context: Context): Int {
        return getPreference(context, today(DAILY_STEP_PREFIX))
    }

    /**
     * checks if we have a pokemon saved
     */
    private fun hasPokemonToday(context: Context): Boolean {
        return getPreference(context, today("pokemon")) > 0
    }

    /**
     * saves things to the preferences
     */
    private fun savePreferences(context: Context, key: String, value: Int) {
        val sharedPref = context.getSharedPreferences(WRITABLE_FILE, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putInt(key, value)
        editor.apply()
    }

    /**
     * gets things from the preferences
     */
    private fun getPreference(context: Context, key: String): Int {
        val sharedPref = context.getSharedPreferences(WRITABLE_FILE, Context.MODE_PRIVATE)
        return sharedPref.getInt(key, 0)
    }

    /**
     * get the preference key for today
     */
    private fun today(prefix: String): String {
        val cal = Calendar.getInstance()
        return prefix + "_" + cal.get(Calendar.YEAR) + "" + cal.get(Calendar.MONTH) + "" + cal.get(
            Calendar.DAY_OF_MONTH
        )
    }
}