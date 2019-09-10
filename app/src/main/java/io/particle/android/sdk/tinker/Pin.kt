package io.particle.android.sdk.tinker

import android.animation.*
import android.animation.Animator.AnimatorListener
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import androidx.annotation.AnimatorRes
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import io.particle.android.sdk.utils.Py.list
import io.particle.android.sdk.utils.ui.Ui
import io.particle.sdk.app.R


interface OnAnalogWriteListener {

    fun onAnalogWrite(value: Int)
}


internal class Pin(
    val pinLabelView: TextView,
    private val pinType: PinType,
    val name: String,
    internal val functions: Set<PinAction>,
    internal val label: String = name
) {

    var configuredAction: PinAction = PinAction.NONE

    private var pinBackgroundAnim: ObjectAnimator? = null
    private var endAnimation: Animator? = null

    private var analogReadView: View? = null
    private var analogWriteView: View? = null
    private var digitalWriteView: View? = null
    private var digitalReadView: View? = null

    internal var analogValue = 0
        private set
    internal var digitalValue: DigitalValue? = null
        private set

    internal val isAnalogWriteMode: Boolean
        get() = analogWriteView != null && analogWriteView!!.visibility == View.VISIBLE

    private val cancelAnimator: Animator
        get() {
            val ctx = pinLabelView.context
            val backToTransparent1 = ctx.loadObjectAnimator(R.animator.pin_background_end)
            val goDark = ctx.loadObjectAnimator(R.animator.pin_background_go_dark)
            val backToTransparent2 = ctx.loadObjectAnimator(R.animator.pin_background_end)
            val parent = pinLabelView.parent as ViewGroup
            val evaluator = CastCheckArgbEvaluator()
            for (animator in list(backToTransparent1, goDark, backToTransparent2)) {
                animator.target = parent
                animator.setEvaluator(evaluator)
            }

            val animatorSet = AnimatorSet()
            animatorSet.setTarget(parent)
            animatorSet.playSequentially(backToTransparent1, goDark, backToTransparent2)
            return animatorSet
        }

    private val analogMax: Int
        get() {
            var max = 1
            if (configuredAction === PinAction.ANALOG_READ) {
                max = ANALOG_READ_MAX
            } else if (configuredAction === PinAction.ANALOG_WRITE) {
                max = if (PinAction.ANALOG_WRITE_DAC in functions) {
                    ANALOG_WRITE_MAX_DAC
                } else {
                    ANALOG_WRITE_MAX_PWM
                }
            }
            return max
        }

    init {
        this.pinLabelView.text = label
        reset()
    }

    internal fun reset() {
        if (analogReadView != null) {
            analogReadView!!.visibility = View.GONE
            // Reset the values
            val barGraph = Ui.findView<ProgressBar>(
                analogReadView!!,
                R.id.tinker_analog_read_progress
            )
            val readValue = Ui.findView<TextView>(
                analogReadView!!,
                R.id.tinker_analog_read_value
            )
            barGraph.max = 100
            barGraph.progress = 0
            readValue.text = "0"
            analogReadView = null
        }

        if (analogWriteView != null) {
            // Reset the values
            analogWriteView!!.visibility = View.GONE
            val seekBar = Ui.findView<SeekBar>(
                analogWriteView!!,
                R.id.tinker_analog_write_seekbar
            )
            val value = Ui.findView<TextView>(
                analogWriteView!!,
                R.id.tinker_analog_write_value
            )
            seekBar.progress = 0
            value.text = "0"
            analogWriteView = null
        }

        if (digitalWriteView != null) {
            digitalWriteView!!.visibility = View.GONE
            digitalWriteView = null
        }

        if (digitalReadView != null) {
            digitalReadView!!.visibility = View.GONE
            digitalReadView = null
        }

        if (!stopAnimating()) {
            (pinLabelView.parent as View).setBackgroundColor(0)
        }

        analogValue = 0
        digitalValue = DigitalValue.NONE
    }

    fun updatePinColor() {
        pinLabelView.setTextColor(pinLabelView.context.resources.getColor(android.R.color.black))

        when (configuredAction) {
            PinAction.ANALOG_READ -> pinLabelView.setBackgroundResource(R.drawable.tinker_pin_emerald)
            PinAction.ANALOG_WRITE -> pinLabelView.setBackgroundResource(R.drawable.tinker_pin_sunflower)
            PinAction.DIGITAL_READ -> if (digitalValue === DigitalValue.HIGH) {
                pinLabelView.setBackgroundResource(R.drawable.tinker_pin_read_high)
                pinLabelView.setTextColor(pinLabelView.context.resources.getColor(R.color.tinker_pin_text_dark))
            } else {
                pinLabelView.setBackgroundResource(R.drawable.tinker_pin_cyan)
            }
            PinAction.DIGITAL_WRITE -> if (digitalValue === DigitalValue.HIGH) {
                pinLabelView.setBackgroundResource(R.drawable.tinker_pin_write_high)
                pinLabelView.setTextColor(pinLabelView.context.resources.getColor(R.color.tinker_pin_text_dark))
            } else {
                pinLabelView.setBackgroundResource(R.drawable.tinker_pin_alizarin)
            }
            PinAction.NONE -> pinLabelView.setBackgroundResource(R.drawable.tinker_pin)
        }
    }

    internal fun mute() {
        pinLabelView.setBackgroundResource(R.drawable.tinker_pin_muted)
        pinLabelView.setTextColor(
            pinLabelView.context.resources.getColor(
                R.color.tinker_pin_text_muted
            )
        )
        hideExtraViews()
    }

    internal fun unmute() {
        updatePinColor()
        showExtraViews()
    }

    private fun hideExtraViews() {
        if (analogReadView != null) {
            analogReadView!!.visibility = View.GONE
        }
        if (analogWriteView != null) {
            analogWriteView!!.visibility = View.GONE
        }
        if (digitalWriteView != null) {
            digitalWriteView!!.visibility = View.GONE
        }
        if (digitalReadView != null) {
            digitalReadView!!.visibility = View.GONE
        }

        if (pinBackgroundAnim != null) {
            pinBackgroundAnim!!.end()
        }
        val parent = pinLabelView.parent as View
        parent.setBackgroundColor(0)
    }

    private fun showExtraViews() {
        if (analogReadView != null) {
            analogReadView!!.visibility = View.VISIBLE
        } else if (analogWriteView != null) {
            analogWriteView!!.visibility = View.VISIBLE
        } else if (digitalWriteView != null) {
            digitalWriteView!!.visibility = View.VISIBLE
        } else if (digitalReadView != null) {
            digitalReadView!!.visibility = View.VISIBLE
        }
    }

    internal fun showAnalogValue(value: Int) {
        analogValue = value
        doShowAnalogValue(value)
    }

    private fun doShowAnalogValue(newValue: Int) {
        if (analogWriteView != null) {
            analogWriteView!!.visibility = View.GONE
            analogWriteView = null
        }

        val parent = pinLabelView.parent as ViewGroup

        if (pinBackgroundAnim != null) {
            pinBackgroundAnim!!.cancel()
        }

        if (analogReadView == null) {
            analogReadView = Ui.findView(parent, R.id.tinker_analog_read_main)
        }

        // If the pinLabelView does not exist, inflate it
        if (analogReadView == null) {
            val inflater = pinLabelView.layoutInflater
            if (pinType === PinType.A) {
                analogReadView = inflater.inflate(R.layout.tinker_analog_read_left, parent, false)
                parent.addView(analogReadView)
            } else if (pinType === PinType.D) {
                analogReadView = inflater.inflate(R.layout.tinker_analog_read_right, parent, false)
                parent.addView(analogReadView, 0)
            }
        }

        analogReadView!!.visibility = View.VISIBLE
        // Find the existing views and set the values
        val barGraph = Ui.findView<ProgressBar>(
            analogReadView!!,
            R.id.tinker_analog_read_progress
        )
        val readValue = Ui.findView<TextView>(
            analogReadView!!,
            R.id.tinker_analog_read_value
        )

        if (PinAction.ANALOG_READ == configuredAction) {
            barGraph.progressDrawable = ContextCompat.getDrawable(
                pinLabelView.context, R.drawable.progress_emerald
            )
        } else {
            barGraph.progressDrawable = ContextCompat.getDrawable(
                pinLabelView.context, R.drawable.progress_sunflower
            )
        }

        val max = analogMax
        barGraph.max = max
        barGraph.progress = newValue
        readValue.text = newValue.toString()
    }

    internal fun showAnalogWrite(listener: OnAnalogWriteListener) {
        if (analogReadView != null) {
            analogReadView!!.visibility = View.GONE
            analogReadView = null
        }

        val parent = pinLabelView.parent as ViewGroup
        if (analogWriteView == null) {
            analogWriteView = Ui.findView(parent, R.id.tinker_analog_write_main)
        }

        // If the pinLabelView does not exist, inflate it
        if (analogWriteView == null) {
            val inflater = pinLabelView.layoutInflater

            if (pinType === PinType.A) {
                analogWriteView = inflater.inflate(
                    R.layout.tinker_analog_write_left,
                    parent, false
                )
                parent.addView(analogWriteView)
            } else if (pinType === PinType.D) {
                analogWriteView = inflater.inflate(
                    R.layout.tinker_analog_write_right, parent,
                    false
                )
                parent.addView(analogWriteView, 0)
            }
        }

        analogWriteView!!.visibility = View.VISIBLE
        val seekBar = Ui.findView<SeekBar>(
            analogWriteView!!,
            R.id.tinker_analog_write_seekbar
        )
        val valueText = Ui.findView<TextView>(
            analogWriteView!!,
            R.id.tinker_analog_write_value
        )
        if (pinBackgroundAnim != null) {
            pinBackgroundAnim!!.cancel()
            pinBackgroundAnim = null
        }

        seekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                val value = seekBar.progress
                parent.setBackgroundColor(0)
                showAnalogWriteValue()
                listener.onAnalogWrite(value)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {

            }

            override fun onProgressChanged(
                seekBar: SeekBar, progress: Int,
                fromUser: Boolean
            ) {
                valueText.text = progress.toString()
            }
        })
        seekBar.max = analogMax
    }

    internal fun showAnalogWriteValue() {
        doShowAnalogValue(analogValue)
    }

    fun showDigitalWrite(newValue: DigitalValue) {
        this.digitalValue = newValue
        val parent = pinLabelView.parent as ViewGroup
        if (digitalWriteView == null) {
            digitalWriteView = Ui.findView(parent, R.id.tinker_digital_write_main)
        }

        // If the pinLabelView does not exist, inflate it
        if (digitalWriteView == null) {
            val inflater = pinLabelView.layoutInflater
            digitalWriteView = inflater.inflate(R.layout.tinker_digital_write, parent, false)
            if (pinType === PinType.A) {
                parent.addView(digitalWriteView)
            } else if (pinType === PinType.D) {
                parent.addView(digitalWriteView, 0)
            }
        }

        digitalWriteView!!.visibility = View.VISIBLE
        val value = Ui.findView<TextView>(
            digitalWriteView!!,
            R.id.tinker_digital_write_value
        )
        value.text = newValue.name
        updatePinColor()
    }

    internal fun showDigitalRead(newValue: DigitalValue) {
        this.digitalValue = newValue
        val parent = pinLabelView.parent as ViewGroup
        if (digitalReadView == null) {
            digitalReadView = Ui.findView(
                parent,
                R.id.tinker_digital_write_main
            )
        }

        // If the pinLabelView does not exist, inflate it
        if (digitalReadView == null) {
            val inflater = pinLabelView.layoutInflater
            digitalReadView = inflater.inflate(R.layout.tinker_digital_read, parent, false)
            if (pinType === PinType.A) {
                parent.addView(digitalReadView)
            } else if (pinType === PinType.D) {
                parent.addView(digitalReadView, 0)
            }
        }

        digitalReadView!!.visibility = View.VISIBLE
        val value = Ui.findView<TextView>(
            digitalReadView!!,
            R.id.tinker_digital_read_value
        )
        value.text = newValue.name
        // fade(value, newValue);
        updatePinColor()
        if (!stopAnimating()) {
            cancelAnimator.start()
        }
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + if (configuredAction == null) 0 else configuredAction.hashCode()
        result = prime * result + (pinLabelView.hashCode() ?: 0)
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj)
            return true
        if (obj == null)
            return false
        if (javaClass != obj.javaClass)
            return false
        val other = obj as Pin?
        if (configuredAction !== other!!.configuredAction)
            return false
        if (pinLabelView == null) {
            if (other!!.pinLabelView != null)
                return false
        } else if (pinLabelView != other!!.pinLabelView)
            return false
        return true
    }

    internal fun animateYourself() {
        val parent = pinLabelView.parent as ViewGroup

        if (pinBackgroundAnim != null) {
            pinBackgroundAnim!!.end()
            pinBackgroundAnim = null
        }

        pinBackgroundAnim = AnimatorInflater.loadAnimator(
            pinLabelView.context, R.animator.pin_background_start
        ) as ObjectAnimator

        pinBackgroundAnim!!.target = parent
        pinBackgroundAnim!!.setEvaluator(CastCheckArgbEvaluator())
        pinBackgroundAnim!!.addListener(object : AnimatorListener {

            override fun onAnimationStart(animation: Animator) {
                // NO OP
            }

            override fun onAnimationRepeat(animation: Animator) {}

            override fun onAnimationEnd(animation: Animator) {}

            override fun onAnimationCancel(animation: Animator) {
                endAnimation = cancelAnimator
                endAnimation!!.start()
            }
        })

        pinBackgroundAnim!!.start()
    }

    internal fun stopAnimating(): Boolean {
        if (pinBackgroundAnim != null) {
            pinBackgroundAnim!!.cancel()
            pinBackgroundAnim = null
            return true
        } else {
            return false
        }
    }

}


private class CastCheckArgbEvaluator : TypeEvaluator<Any> {

    internal val argbEvaluator = ArgbEvaluator()
    internal val floatEvaluator = FloatEvaluator()

    override fun evaluate(fraction: Float, startValue: Any, endValue: Any): Any? {
        return try {
            argbEvaluator.evaluate(fraction, startValue, endValue)
        } catch (e: ClassCastException) {
            floatEvaluator.evaluate(fraction, startValue as Number, endValue as Number)
        }

    }
}


private fun Context.loadObjectAnimator(@AnimatorRes animatorId: Int): ObjectAnimator {
    return AnimatorInflater.loadAnimator(this, animatorId) as ObjectAnimator
}


private val View.layoutInflater: LayoutInflater
    get() { return context.getSystemService()!! }