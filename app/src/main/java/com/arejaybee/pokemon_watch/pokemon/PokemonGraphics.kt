package com.arejaybee.pokemon_watch.pokemon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.arejaybee.pokemon_watch.R

object PokemonGraphics {

    lateinit var hpBarGreen:Bitmap
    lateinit var hpBarYellow:Bitmap
    lateinit var hpBarRed:Bitmap
    lateinit var expBar:Bitmap

    fun initialize(context: Context) {
        expBar = BitmapFactory.decodeResource(context.resources, R.drawable.exp_bar)
        hpBarGreen = BitmapFactory.decodeResource(context.resources, R.drawable.hp_bar_green)
        hpBarYellow = BitmapFactory.decodeResource(context.resources, R.drawable.hp_bar_yellow)
        hpBarRed = BitmapFactory.decodeResource(context.resources, R.drawable.hp_bar_red)
    }
    /**
     * Gets the graphic for the hp bar
     */
    fun getHpBar(health: Float): Bitmap {

        val hp = health / 100f
        val bar: Bitmap = when {
            hp > 0.5 -> hpBarGreen
            hp > 0.25 -> hpBarYellow
            else -> hpBarRed
        }
        val healthOffset = 5f
        val myBar: Bitmap = bar.copy(bar.config, true)
        myBar.width = bar.width.coerceAtMost(((bar.width * hp) + healthOffset).toInt())
        return myBar
    }

    /**
     * gets the graphic for the exp bar
     */
    fun getModifiedExpBar(experience: Int, level: Int): Bitmap {
        val expBarMod = (experience.toFloat() / 100) - level
        val myBar: Bitmap = expBar.copy(expBar.config, true)
        val offset = 5.0f
        val newWidth = expBar.width.coerceAtMost(
                (myBar.width * expBarMod + offset).toInt()
            )
        myBar.width = 1.coerceAtLeast(newWidth)
        return myBar
    }

    /**
     * Will show the pokemon's status. Not liking how it looks so far...
     */
    fun getStatus(context: Context, status: Int): Bitmap? {
        val id = when(status) {
            Pokemon.STATUS.HAPPY.ordinal -> {
                R.drawable.status_happy
            }
            else -> {
                return null
            }
        }
        return BitmapFactory.decodeResource(context.resources, id)
    }
}