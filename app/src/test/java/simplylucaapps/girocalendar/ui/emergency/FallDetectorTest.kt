package simplylucaapps.girocalendar.ui.emergency

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Random

class FallDetectorTest {
    private val rnd = Random(7)
    private fun noise(s: Double) = (rnd.nextGaussian() * s).toFloat()

    /** Genera campioni a 50 Hz. [gx,gy,gz] gravità, [bump] ampiezza rumore da pedalata. */
    private class Gen(val d: FallDetector, val rnd: Random) {
        var t = 0L
        fun seg(ms: Int, g: FloatArray, sd: Double, speed: Float? = null) {
            var i = 0
            while (i < ms) {
                d.onSample(t, g[0] + (rnd.nextGaussian() * sd).toFloat(), g[1] + (rnd.nextGaussian() * sd).toFloat(), g[2] + (rnd.nextGaussian() * sd).toFloat())
                if (speed != null && (t / 20) % 50 == 0L) d.onSpeed(t, speed)
                t += 20; i += 20
            }
        }
        fun hit(peak: Float, g: FloatArray) { d.onSample(t, g[0], g[1], peak); t += 20; d.onSample(t, g[0], g[1], g[2]); t += 20 }
    }
    private val upright = floatArrayOf(0f, 0f, 9.81f)
    private val onSide = floatArrayOf(9.81f, 0f, 0f)

    private fun run(sens: Int = 1, block: (Gen) -> Unit): Int {
        var n = 0
        val d = FallDetector(sens) { n++ }
        block(Gen(d, rnd))
        return n
    }

    @Test fun crashTriggers() = assertEquals(1, run { g ->
        g.seg(8000, upright, 2.0, 6f); g.hit(75f, upright); g.seg(300, onSide, 6.0); g.seg(8000, onSide, 0.2, 0f)
    })

    @Test fun jumpWithHardLandingDoesNotTrigger() = assertEquals(0, run { g ->
        g.seg(8000, upright, 2.0, 6f); g.seg(400, floatArrayOf(0f, 0f, 0.5f), 0.3, 6f); g.hit(80f, upright); g.seg(8000, upright, 2.0, 6f)
    })

    @Test fun rockyBumpsOnHandlebarDoNotTrigger() = assertEquals(0, run { g ->
        repeat(10) { g.seg(1500, upright, 2.5, 5f); g.hit(60f, upright) }; g.seg(6000, upright, 2.5, 5f)
    })

    @Test fun phoneBouncingInBackpackDoesNotTrigger() = assertEquals(0, run { g ->
        // orientamento che cambia e urti, ma mai fermo
        g.seg(6000, upright, 3.0, 5f); g.hit(70f, upright); g.seg(3000, onSide, 4.0, 5f); g.hit(60f, onSide); g.seg(8000, upright, 4.0, 5f)
    })

    @Test fun phoneDroppedFromTableAtRestDoesNotTrigger() = assertEquals(0, run { g ->
        g.seg(8000, upright, 0.05); g.hit(70f, upright); g.seg(8000, onSide, 0.05)
    })

    @Test fun crashButStillMovingAfterwardsDoesNotTrigger() = assertEquals(0, run { g ->
        g.seg(8000, upright, 2.0, 6f); g.hit(75f, upright); g.seg(8000, onSide, 2.5, 5f)
    })

    @Test fun lowSensitivityIgnoresMediumImpact() = assertEquals(0, run(0) { g ->
        g.seg(8000, upright, 2.0, 6f); g.hit(50f, upright); g.seg(300, onSide, 6.0); g.seg(8000, onSide, 0.2, 0f)
    })

    @Test fun crashWithoutGpsStillTriggers() = assertEquals(1, run { g ->
        g.seg(8000, upright, 2.0); g.hit(75f, upright); g.seg(300, onSide, 6.0); g.seg(8000, onSide, 0.2)
    })

    @Test fun multiBounceTumbleTriggersOnce() = assertEquals(1, run { g ->
        g.seg(8000, upright, 2.0, 6f); g.hit(75f, upright); g.seg(500, onSide, 6.0); g.hit(60f, onSide); g.seg(8000, floatArrayOf(0f, 9.81f, 0f), 0.2, 0f)
    })
}
