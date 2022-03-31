package com.arejaybee.pokemon_watch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.view.SurfaceHolder
import androidx.palette.graphics.Palette
import com.arejaybee.pokemon_watch.pokemon.Pokemon
import com.arejaybee.pokemon_watch.pokemon.PokemonGraphics
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import kotlin.random.Random


/**
 * Updates rate in milliseconds for interactive mode. We update once a second to advance the
 * second hand.
 */
private const val INTERACTIVE_UPDATE_RATE_MS = 1000

/**
 * Handler message id for updating the time periodically in interactive mode.
 */
private const val MSG_UPDATE_TIME = 0

private const val HOUR_STROKE_WIDTH = 5f
private const val STATUS_DELETE_DELAY = 5

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn"t
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 *
 *
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
class MyWatchFace : CanvasWatchFaceService() {

    override fun onCreateEngine(): Engine {
        return Engine()
    }

    private class EngineHandler(reference: MyWatchFace.Engine) : Handler() {
        private val mWeakReference: WeakReference<MyWatchFace.Engine> = WeakReference(reference)

        override fun handleMessage(msg: Message) {
            val engine = mWeakReference.get()
            if (engine != null) {
                when (msg.what) {
                    MSG_UPDATE_TIME -> engine.handleUpdateTimeMessage()
                }
            }
        }
    }

    inner class Engine : CanvasWatchFaceService.Engine() {
        private lateinit var mCalendar: Calendar

        private var mRegisteredTimeZoneReceiver = false
        private var mMuteMode: Boolean = false
        private var mCenterX: Float = 0F
        private var mCenterY: Float = 0F

        private var mSecondHandLength: Float = 0F
        private var sMinuteHandLength: Float = 0F
        private var sHourHandLength: Float = 0F

        /* Colors for all hands (hour, minute, seconds, ticks) based on photo loaded. */
        private var mWatchHandColor: Int = 0
        private var mWatchHandHighlightColor: Int = 0
        private var mWatchHandShadowColor: Int = 0

        private lateinit var mTickAndCirclePaint: Paint

        private lateinit var mBackgroundPaint: Paint
        private lateinit var pokemonBattleBackground: Bitmap
        private lateinit var mBackgroundBitmap: Bitmap
        private lateinit var mGrayBackgroundBitmap: Bitmap

        private var mAmbient: Boolean = false
        private var mLowBitAmbient: Boolean = false
        private var mBurnInProtection: Boolean = false

        private lateinit var pokemon: Pokemon

        private lateinit var sensorManager: SensorManager
        var statusTime = 0

        /* Handler to update the time once a second in interactive mode. */
        private val mUpdateTimeHandler = EngineHandler(this)

        private val mTimeZoneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            //PokemonMap.fillFirstEvolutions()
            sensorManager = getSystemService(SensorManager::class.java)

            val id = PreferenceUtil.getPokemonToday(this@MyWatchFace)
            val isShiny = Random.nextInt(0, 1000) == 1
            pokemon = Pokemon(this@MyWatchFace, id, true, isShiny)
            pokemon.experience = PreferenceUtil.getInitialExp(this@MyWatchFace)
            PokemonGraphics.initialize(this@MyWatchFace)

            setWatchFaceStyle(
                WatchFaceStyle.Builder(this@MyWatchFace)
                    .setAcceptsTapEvents(true)
                    .build()
            )

            mCalendar = Calendar.getInstance()

            initializeBackground()
            initializeImages()
            IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                displayContext?.registerReceiver(null, ifilter)
            }
            registerSensor()
        }

        override fun onTimeTick() {
            super.onTimeTick()
            invalidate()
        }

        /**
         * Sets up the watch's background
         */
        private fun initializeBackground() {
            mBackgroundPaint = Paint().apply {
                color = Color.BLACK
            }
            mBackgroundBitmap =
                BitmapFactory.decodeResource(resources, R.drawable.pokeball_background)

            /* Extracts colors from background image to improve watchface style. */
            Palette.from(mBackgroundBitmap).generate {
                it?.let {
                    mWatchHandHighlightColor = it.getVibrantColor(Color.RED)
                    mWatchHandColor = it.getLightVibrantColor(Color.WHITE)
                    mWatchHandShadowColor = it.getDarkMutedColor(Color.BLACK)
                    updateWatchHandStyle()
                }
            }
        }

        private fun initializeImages() {
            pokemonBattleBackground =
                BitmapFactory.decodeResource(resources, R.drawable.pokemon_battle)
        }

        override fun onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)
            mLowBitAmbient = properties.getBoolean(
                WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false
            )
            mBurnInProtection = properties.getBoolean(
                WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false
            )
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            mAmbient = inAmbientMode

            updateWatchHandStyle()

            // Check and trigger whether or not timer should be running (only
            // in active mode).
            updateTimer()
        }

        private fun updateWatchHandStyle() {
        }

        override fun onInterruptionFilterChanged(interruptionFilter: Int) {
            super.onInterruptionFilterChanged(interruptionFilter)
            val inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f
            mCenterY = height / 2f

            /*
             * Calculate lengths of different hands based on watch screen size.
             */
            mSecondHandLength = (mCenterX * 0.875).toFloat()
            sMinuteHandLength = (mCenterX * 0.75).toFloat()
            sHourHandLength = (mCenterX * 0.5).toFloat()

            /* Scale loaded background image (more efficient) if surface dimensions change. */
            val scale = width.toFloat() / mBackgroundBitmap.width.toFloat()

            mBackgroundBitmap = Bitmap.createScaledBitmap(
                mBackgroundBitmap,
                (mBackgroundBitmap.width * scale).toInt(),
                (mBackgroundBitmap.height * scale).toInt(), true
            )

            /*
             * Create a gray version of the image only if it will look nice on the device in
             * ambient mode. That means we don"t want devices that support burn-in
             * protection (slight movements in pixels, not great for images going all the way to
             * edges) and low ambient mode (degrades image quality).
             *
             * Also, if your watch face will know about all images ahead of time (users aren"t
             * selecting their own photos for the watch face), it will be more
             * efficient to create a black/white version (png, etc.) and load that when you need it.
             */
            if (!mBurnInProtection && !mLowBitAmbient) {
                initGrayBackgroundBitmap()
            }
        }

        private fun initGrayBackgroundBitmap() {
            mGrayBackgroundBitmap = Bitmap.createBitmap(
                mBackgroundBitmap.width,
                mBackgroundBitmap.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(mGrayBackgroundBitmap)
            val grayPaint = Paint()
            val colorMatrix = ColorMatrix()
            colorMatrix.setSaturation(0f)
            val filter = ColorMatrixColorFilter(colorMatrix)
            grayPaint.colorFilter = filter
            canvas.drawBitmap(mBackgroundBitmap, 0f, 0f, grayPaint)
        }

        /**
         * Captures tap event (and tap type). The [WatchFaceService.TAP_TYPE_TAP] case can be
         * used for implementing specific logic to handle the gesture.
         */
        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            when (tapType) {
                WatchFaceService.TAP_TYPE_TOUCH -> {
                    // The user has started touching the screen.
                }
                WatchFaceService.TAP_TYPE_TOUCH_CANCEL -> {
                    // The user has started a different gesture or otherwise cancelled the tap.
                }
                WatchFaceService.TAP_TYPE_TAP -> {
                    // The user has completed the tap gesture.
                    pokemon.cry()
                    statusTime = STATUS_DELETE_DELAY
                }
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas, bounds: Rect) {
            val now = System.currentTimeMillis()
            if(statusTime > 0) { statusTime-- }
            mCalendar.timeInMillis = now
            pokemon.onTick()
            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                canvas.drawColor(Color.BLACK)
            } else if (mAmbient) {
                drawBattleBackground(canvas)
                drawTime(canvas)
            } else {
                drawBackground(canvas)
                drawPokemon(canvas)
                drawBattleBackground(canvas)
                if(statusTime > 0) {
                    drawPokemonStatus(canvas)
                }
                drawTime(canvas)
            }
        }

        /**
         * Draws the custom pokeball background
         */
        private fun drawBackground(canvas: Canvas) {
            canvas.drawBitmap(mBackgroundBitmap, 0f, 0f, mBackgroundPaint)
        }

        /**
         * Draws the date and time
         */
        private fun drawTime(canvas: Canvas) {
            val textPaint = Paint().apply {
                color = mWatchHandColor
                strokeWidth = HOUR_STROKE_WIDTH
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
            }
            textPaint.textSize = 50.0f
            val f = "hh:mm aa"
            val formattedTime = SimpleDateFormat(f).format(mCalendar.time)
            val day = getDate(mCalendar.get(Calendar.DAY_OF_WEEK))
            val date = "" + day + " " + mCalendar.get(Calendar.DAY_OF_MONTH)

            val yDateOffset = -160
            val xDateOffset = -1 * textPaint.measureText(date) / 2

            val yTimeOffset = yDateOffset + textPaint.textSize
            val xTimeOffset = -1 * textPaint.measureText(formattedTime) / 2
            canvas.drawText(date, mCenterX + xDateOffset, mCenterY + yDateOffset, textPaint)
            canvas.drawText(
                formattedTime,
                mCenterX + xTimeOffset,
                mCenterY + yTimeOffset,
                textPaint
            )
        }

        /**
         * Turns the Day of the week into a string
         */
        private fun getDate(date: Int): String {
            return when (date) {
                1 -> "Sun"
                2 -> "Mon"
                3 -> "Tues"
                4 -> "Wed"
                5 -> "Thurs"
                6 -> "Fri"
                else -> "Sat"
            }
        }

        /**
         * Draws the pokemon sprite
         */
        private fun drawPokemon(canvas: Canvas) {
            val sprite = pokemon.sprite
            val xOffset = -1 * sprite.width / 2
            val yOffset = -1 * (sprite.height / 2 + 25)

            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                canvas.drawColor(Color.BLACK)
            } else if (mAmbient) {
                canvas.drawBitmap(sprite, mCenterX + xOffset, mCenterY + yOffset, mBackgroundPaint)
            } else {
                canvas.drawBitmap(sprite, mCenterX + xOffset, mCenterY + yOffset, mBackgroundPaint)
            }
        }

        /**
         * draws the pokemon's Box with hp and exp
         */
        private fun drawBattleBackground(canvas: Canvas) {
            val xBattleOffset = -1 * pokemonBattleBackground.width / 2
            val yBattleOffset = 1 * pokemonBattleBackground.height / 2 - 10
            val xHpBarOffset = -20
            val xNameOffset = xBattleOffset + 35
            val yHpBarOffset = yBattleOffset + 48
            val xHpOffset = -1 * (xBattleOffset + 110)
            val yNameOffset = yBattleOffset + 35
            val xBpmOffset = -1 * (xBattleOffset + 80)
            val yHpOffset = yBattleOffset + 61
            val xExpBarOffset = -65
            val yExpBarOffset = pokemonBattleBackground.height + 32
            val batteryPct = getBatteryInfoPhone()
            val bar = PokemonGraphics.getHpBar(batteryPct)
            val textPaint = Paint().apply {
                color = Color.BLACK
                strokeWidth = HOUR_STROKE_WIDTH
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
            }
            val batteryDisplay = batteryPct.roundToInt().toString() + "%"

            canvas.drawBitmap(
                PokemonGraphics.getModifiedExpBar(pokemon.experience, pokemon.level),
                mCenterX + xExpBarOffset,
                mCenterY + yExpBarOffset,
                mBackgroundPaint
            )
            canvas.drawBitmap(
                bar,
                mCenterX + xHpBarOffset,
                mCenterY + yHpBarOffset,
                mBackgroundPaint
            )
            canvas.drawBitmap(
                pokemonBattleBackground,
                mCenterX + xBattleOffset,
                mCenterY + yBattleOffset,
                mBackgroundPaint
            )
            textPaint.textSize = 30.0f
            canvas.drawText(pokemon.name, mCenterX + xNameOffset, mCenterY + yNameOffset, textPaint)

            textPaint.textSize = 25.0f
            canvas.drawText(
                pokemon.level.toString(),
                mCenterX + xBpmOffset,
                mCenterY + yNameOffset,
                textPaint
            )

            textPaint.textSize = 15.0f
            canvas.drawText(batteryDisplay, mCenterX + xHpOffset, mCenterY + yHpOffset, textPaint)
        }

        /**
         * Gets the watch's battery life
         */
        private fun getBatteryInfoPhone(): Float {
            val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = registerReceiver(null, iFilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            return level?.toFloat() ?: let { 0f }
        }

        /**
         * Not currently used. Will draw the pokemon's status (should be referenced in onTouch)
         */
        private fun drawPokemonStatus(canvas: Canvas) {
            val sprite = PokemonGraphics.getStatus(this@MyWatchFace,0) ?: return
            val xOffset = -1 * sprite.width / 2 - 80
            val yOffset = -1 * (sprite.height / 2 + 80)
            canvas.drawBitmap(sprite, mCenterX + xOffset, mCenterY + yOffset, mBackgroundPaint)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()
                /* Update time zone in case it changed while we weren"t visible. */
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            } else {
                unregisterReceiver()
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer()
        }

        private fun registerSensor() {
            sensorManager.registerListener(
                pokemon,
                sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER),
                SensorManager.SENSOR_DELAY_UI
            )
        }

        private fun registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@MyWatchFace.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@MyWatchFace.unregisterReceiver(mTimeZoneReceiver)
        }

        /**
         * Starts/stops the [.mUpdateTimeHandler] timer based on the state of the watch face.
         */
        private fun updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }

        /**
         * Returns whether the [.mUpdateTimeHandler] timer should be running. The timer
         * should only run in active mode.
         */
        private fun shouldTimerBeRunning(): Boolean {
            return isVisible && !mAmbient
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        fun handleUpdateTimeMessage() {
            invalidate()
            if (shouldTimerBeRunning()) {
                val timeMs = System.currentTimeMillis()
                val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
            }
        }
    }
}