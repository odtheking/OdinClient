package me.odinmain.features.impl.floor7.p3

import me.odinmain.features.Category
import me.odinmain.features.Module
import me.odinmain.features.settings.impl.BooleanSetting
import me.odinmain.features.settings.impl.ColorSetting
import me.odinmain.features.settings.impl.SelectorSetting
import me.odinmain.ui.clickgui.util.ColorUtil.withAlpha
import me.odinmain.utils.noControlCodes
import me.odinmain.utils.render.Color
import me.odinmain.utils.render.world.RenderUtils
import me.odinmain.utils.render.world.RenderUtils.renderBoundingBox
import net.minecraft.entity.Entity
import net.minecraft.entity.item.EntityArmorStand
import net.minecraft.util.Vec3
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent


object InactiveWaypoints : Module(
    name = "Inactive Waypoints",
    category = Category.FLOOR7,
    description = "Shows inactive terminals, devices and levers"
) {

    private val showTerminals: Boolean by BooleanSetting(name = "Show Terminals")
    private val showDevices: Boolean by BooleanSetting(name = "Show Devices")
    private val showLevers: Boolean by BooleanSetting(name = "Show Levers")
    private val renderText: Boolean by BooleanSetting(name = "Render Text")
    private val renderMode: Int by SelectorSetting("Render", "Both", arrayListOf("Both", "Outline", "Filled"))
    private val color: Color by ColorSetting("Color", Color.RED.withAlpha(.5f), true)

    private var inactiveList = listOf<Entity>()

    init {
        execute(1000) {
            if (!enabled) return@execute
            inactiveList = mc.theWorld?.loadedEntityList?.filter { it is EntityArmorStand && it.name.noControlCodes.contains("Inactive", true) || it.name.noControlCodes.contains("Not Activated", true) } ?: emptyList()
        }
    }

    @SubscribeEvent
    fun onRenderWorld(event: RenderWorldLastEvent) {
        inactiveList.forEach {
            var name = it.name.noControlCodes
            if ((name == "Inactive Terminal" && showTerminals) || (name == "Inactive" && showDevices) || (name == "Not Activated" && showLevers)) {
                name = if (name == "Inactive Terminal") "Terminal" else if (name == "Inactive") "Device" else "Lever"
                when (renderMode) {
                    0 -> {
                        RenderUtils.drawFilledBox(it.renderBoundingBox, Color(color.r, color.g, color.b, color.alpha), phase = true)
                        RenderUtils.drawCustomBox(it.renderBoundingBox, Color(color.r, color.g, color.b), 2f, phase = true)
                    }
                    1 -> RenderUtils.drawCustomBox(it.renderBoundingBox, Color(color.r, color.g, color.b), 2f, phase = true)
                    2 -> RenderUtils.drawFilledBox(it.renderBoundingBox, Color(color.r, color.g, color.b, color.alpha), phase = true)
                }
                if (renderText) RenderUtils.drawStringInWorld(name, it.positionVector.add(Vec3(0.5, it.height + 0.5, 0.5)), depthTest = true, increase = true, scale = 0.25f)
            }
        }
    }

}