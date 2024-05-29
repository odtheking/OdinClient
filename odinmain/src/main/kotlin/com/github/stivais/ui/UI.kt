package com.github.stivais.ui

import com.github.stivais.ui.constraints.Constraints
import com.github.stivais.ui.constraints.px
import com.github.stivais.ui.elements.Element
import com.github.stivais.ui.elements.impl.Group
import com.github.stivais.ui.elements.scope.ElementScope
import com.github.stivais.ui.events.EventManager
import com.github.stivais.ui.renderer.Font
import com.github.stivais.ui.renderer.Framebuffer
import com.github.stivais.ui.renderer.NVGRenderer
import com.github.stivais.ui.renderer.Renderer
import com.github.stivais.ui.utils.forLoop
import me.odinmain.utils.round
import java.util.logging.Logger

class UI(
    val renderer: Renderer = NVGRenderer,
    settings: UISettings? = null
) {
    val main: Group = Group(Constraints(0.px, 0.px, 1920.px, 1080.px))

    val settings: UISettings = settings ?: UISettings()

    constructor(renderer: Renderer = NVGRenderer, dsl: ElementScope<Group>.() -> Unit) : this(renderer) {
        ElementScope(main).dsl()
    }

    var eventManager: EventManager? = EventManager(this)

    val mx get() = eventManager!!.mouseX

    val my get() = eventManager!!.mouseY

    var afterInit: ArrayList<() -> Unit>? = null

    fun initialize(width: Int, height: Int) {
        main.constraints.width = width.px
        main.constraints.height = height.px
        main.initialize(this)
        main.position()
        afterInit?.forLoop { it() }
        afterInit = null
        if (settings.cacheFrames && renderer.supportsFramebuffers()) {
            framebuffer = renderer.createFramebuffer(main.width, main.height)
        }
    }

    // frame metrics
    var performance: String? = null
    var lastUpdate = System.nanoTime()
    var frames: Int = 0
    var frameTime: Long = 0

    var needsRedraw = true

    private var framebuffer: Framebuffer? = null

    // rework fbo
    fun render() {
        val fbo = framebuffer
        if (fbo == null) {
            renderer.beginFrame(main.width, main.height)
            if (needsRedraw) {
                needsRedraw = false
                main.position()
                main.clip()
            }
            main.render()
            performance?.let {
                renderer.text(it, main.width - renderer.textWidth(it, 12f), main.height - 12f, 12f)
            }
            renderer.endFrame()
        } else {
            if (needsRedraw) {
                needsRedraw = false
                renderer.bindFramebuffer(fbo) // thanks ilmars for helping me fix
                renderer.beginFrame(fbo.width, fbo.height)
                main.position()
                main.clip()
                main.render()
                renderer.endFrame()
                renderer.unbindFramebuffer()
            }
            renderer.beginFrame(fbo.width, fbo.height)
            renderer.drawFramebuffer(fbo, 0f, 0f)
            performance?.let {
                renderer.text(it, main.width - renderer.textWidth(it, 12f), main.height - 12f, 12f)
            }
            renderer.endFrame()
        }
    }

    // idk about name
    // kinda verbose
    internal inline fun measureMetrics(block: () -> Unit) {
        val start = System.nanoTime()
        block()
        frameTime += System.nanoTime() - start
        frames++
        if (System.nanoTime() - lastUpdate >= 1_000_000_000) {
            lastUpdate = System.nanoTime()
            val sb = StringBuilder()
            if (settings.elementMetrics) {
                sb.append("elements: ${getStats(main, false)}, elements rendering: ${getStats(main, true)},")
            }
            if (settings.frameMetrics) {
                sb.append("frame-time avg: ${((frameTime / frames) / 1_000_000.0).round(4)}s")
            }
            performance = sb.toString()
            frames = 0
            frameTime = 0
        }
    }

    private fun getStats(element: Element, onlyRender: Boolean): Int {
        var amount = 0
        if (!(onlyRender && !element.renders)) {
            amount++
            element.elements?.forLoop { amount += getStats(it, onlyRender) }
        }
        return amount
    }

    fun resize(width: Int, height: Int) {
        main.constraints.width = width.px
        main.constraints.height = height.px
        needsRedraw = true
        if (framebuffer != null) {
            renderer.destroyFramebuffer(framebuffer!!)
            framebuffer = renderer.createFramebuffer(main.width, main.height)
        }
    }

    fun cleanup() {
        val fbo = framebuffer ?: return
        renderer.destroyFramebuffer(fbo)
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
        // temp name
        // future: maybe make a logging class, so you can get an element's "errors" and details
        val logger: Logger = Logger.getLogger("Odin/UI")

        val defaultFont = Font("Regular", "/assets/odinmain/fonts/Regular.otf")
    }
}