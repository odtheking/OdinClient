package com.github.stivais.ui

import com.github.stivais.ui.constraints.Constraints
import com.github.stivais.ui.constraints.px
import com.github.stivais.ui.elements.Element
import com.github.stivais.ui.elements.impl.Group
import com.github.stivais.ui.events.EventManager
import me.odinmain.utils.render.TextAlign
import me.odinmain.utils.render.TextPos
import me.odinmain.utils.render.Color as OdinColor
import me.odinmain.utils.render.text
import me.odinmain.utils.render.translate
import net.minecraft.client.renderer.GlStateManager
import java.util.logging.Logger

// TODO: When finished with dsl and inputs, bring to its own window instead of inside of minecraft for benchmarking and reduce all memory usage
class UI(
    //val renderer: Renderer2D,
    settings: UISettings? = null
) {

    val settings: UISettings = settings ?: UISettings()

    val main: Group = Group(Constraints(0.px, 0.px, 1920.px, 1080.px)).also {
        it.initialize(this)
        it.position()
    }

    constructor(block: Group.() -> Unit) : this() {
        main.block()
    }

    var eventManager: EventManager? = EventManager(this)

    val mx get() = eventManager!!.mouseX

    val my get() = eventManager!!.mouseY

    fun initialize() {
        main.position()
    }

    // frametime metrics
    private var frames: Int = 0
    private var frameTime: Long = 0
    private var performance: String = ""

    fun render() {
        val start = System.nanoTime()

        GlStateManager.pushMatrix()
        GlStateManager.scale(0.5f, 0.5f, 0.5f)
        translate(0f, 0f, 0f)


//        renderer.beginFrame()
        main.position()
        main.render()
        if (settings.frameMetrics) {
            text(performance, main.width, main.height, OdinColor.WHITE, 12f, align = TextAlign.Right, verticalAlign = TextPos.Bottom)
        }
//        renderer.endFrame()
        if (settings.frameMetrics) {
            frames++
            frameTime += System.nanoTime() - start
            if (frames > 100) {
                performance =
                    "elements: ${getElementAmount(main, false)}, " +
                    "elements rendering: ${getElementAmount(main, true)}," +
                    "frametime avg: ${(frameTime / frames) / 1_000_000.0}ms"
                frames = 0
                frameTime = 0
            }
        }
        frames++
        frameTime += System.nanoTime() - start
        GlStateManager.popMatrix()
    }

    fun getElementAmount(element: Element, onlyRender: Boolean): Int {
        var amount = 0
        if (!(onlyRender && !element.renders)) {
            amount++
            element.elements?.let {
                for (i in it) {
                    amount += getElementAmount(i, onlyRender)
                }
            }
        }
        return amount
    }

    fun resize(width: Int, height: Int) {
        main.constraints.width = width.px
        main.constraints.height = height.px
    }

    fun focus(element: Element) {
        if (eventManager == null) return logger.warning("Event Manager isn't setup, but called focus")
        eventManager!!.focus(element)
    }

    fun unfocus() {
        if (eventManager == null) return logger.warning("Event Manager isn't setup, but called unfocus")
        eventManager!!.unfocus()
    }

    inline fun settings(block: UISettings.() -> Unit): UI {
        settings.apply(block)
        return this
    }

    companion object {
        val logger: Logger = Logger.getLogger("UI")
    }
}