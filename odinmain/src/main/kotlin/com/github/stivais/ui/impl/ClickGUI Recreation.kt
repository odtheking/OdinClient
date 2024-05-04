package com.github.stivais.ui.impl

import com.github.stivais.ui.UI
import com.github.stivais.ui.animation.Animations
import com.github.stivais.ui.color.Color
import com.github.stivais.ui.constraints.at
import com.github.stivais.ui.constraints.measurements.Animatable
import com.github.stivais.ui.constraints.px
import com.github.stivais.ui.constraints.size
import com.github.stivais.ui.constraints.sizes.Bounding
import com.github.stivais.ui.elements.*
import com.github.stivais.ui.events.onClick
import com.github.stivais.ui.renderer.Renderer
import com.github.stivais.ui.utils.*
import me.odinmain.features.Category
import me.odinmain.features.Module
import me.odinmain.features.ModuleManager.modules
import me.odinmain.features.impl.render.ClickGUIModule.color
import me.odinmain.utils.capitalizeFirst

@JvmField
val mainColor = Color { color.rgba }

// todo: scissoring isn't complete
fun clickGUI(renderer: Renderer) = UI(renderer) {
    text(
        text = "odin-client ui-branch", // maybe put odin version in here
        pos = at(x = 1.px, y = -0.px),
        size = 12.px
    )
    for (panel in Category.entries) {
        column(at(x = panel.x.px, y = panel.y.px)) {
            block(
                constraints = size(240.px, 40.px),
                color = Color.RGB(26, 26, 26),
                radius = radii(tl = 5, tr = 5)
            ) {
                text(
                    text = panel.name.capitalizeFirst(),
                    size = 20.px
                )
                onClick(1) {
                    sibling()!!.height().animate(0.5.seconds, Animations.EaseInOutQuint)
                    true
                }
                draggable(target = parent!!)
            }
            column(size(h = Animatable(from = Bounding, to = 0.px, swapIf = !panel.extended))) {
                for (module in modules) {
                    if (module.category != panel) continue
                    module(module)
                }
                background(color = Color.RGB(38, 38, 38, 0.7f))
            }
            block(
                constraints = size(240.px, 10.px),
                color = Color.RGB(26, 26, 26),
                radius = radii(br = 5, bl = 5)
            )
        }
    }
}

private fun Element.module(module: Module) {
    column(size(h = Animatable(from = 32.px, to = Bounding))) {
        scissors()
        button(
            constraints = size(w = 240.px, h = 32.px),
            color = color(from = Color.RGB(26, 26, 26), to = mainColor),
            on = module.enabled
        ) {
            text(
                text = module.name,
            )
            onClick(0) {
                module.toggle()
                true
            }
            onClick(1) {
                parent!!.height().animate(0.25.seconds, Animations.EaseInOutQuint)
                true
            }
        }
        for (setting in module.settings) {
            if (setting.hidden) continue
            setting.getElement(this)
        }
    }
}