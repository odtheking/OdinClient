package me.odinmain.features.impl.floor7.p3

import me.odinmain.features.Category
import me.odinmain.features.Module
import me.odinmain.features.settings.Setting.Companion.withDependency
import me.odinmain.features.settings.impl.BooleanSetting
import me.odinmain.features.settings.impl.ColorSetting
import me.odinmain.features.settings.impl.SelectorSetting
import me.odinmain.ui.clickgui.util.ColorUtil.withAlpha
import me.odinmain.utils.addVec
import me.odinmain.utils.noControlCodes
import me.odinmain.utils.render.Color
import me.odinmain.utils.render.RenderUtils
import me.odinmain.utils.render.Renderer
import me.odinmain.utils.skyblock.Island
import me.odinmain.utils.skyblock.dungeon.DungeonUtils
import me.odinmain.utils.toAABB
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
    private val showTerminals: Boolean by BooleanSetting(name = "Show Terminals", default = true, description = "Shows inactive terminals")
    private val showDevices: Boolean by BooleanSetting(name = "Show Devices", default = true, description = "Shows inactive devices")
    private val showLevers: Boolean by BooleanSetting(name = "Show Levers", default = true, description = "Shows inactive levers")
    private val renderText: Boolean by BooleanSetting(name = "Render Text", default = true, description = "Renders the name of the inactive waypoint")
    private val renderBeacon: Boolean by BooleanSetting(name = "Render Beacon", default = true, description = "Renders a beacon beam on the inactive waypoint")
    private val renderBox: Boolean by BooleanSetting(name = "Render Box", default = true, description = "Renders a box around the inactive waypoint")
    private val style: Int by SelectorSetting("Style", "Filled", arrayListOf("Filled", "Outline", "Filled Outline"), description = "Whether or not the box should be filled.").withDependency { renderBox }
    private val color: Color by ColorSetting("Color", Color.RED.withAlpha(.5f), true, description = "The color of the inactive waypoints")

    private var inactiveList = listOf<Entity>()

    init {
        execute(500) {
            if (DungeonUtils.getPhase() != Island.M7P3) return@execute
            inactiveList = mc.theWorld?.loadedEntityList?.filter { it is EntityArmorStand &&
                    (it.name.noControlCodes.contains("Inactive", true) ||
                    it.name.noControlCodes.contains("Not Activated", true)) } ?: emptyList()

        }
    }

    @SubscribeEvent
    fun onRenderWorld(event: RenderWorldLastEvent) {
        if (inactiveList.isEmpty()) return
        inactiveList.forEach {
            var name = it.name.noControlCodes
            if ((name == "Inactive Terminal" && showTerminals) || (name == "Inactive" && showDevices) || (name == "Not Activated" && showLevers)) {
                name = if (name == "Inactive Terminal") "Terminal" else if (name == "Inactive") "Device" else "Lever"
                if (renderBox)
                    Renderer.drawBox(it.position.toAABB(), color, 2f, depth = false, outlineAlpha = if (style == 1) color.alpha else 0, fillAlpha = if (style == 1) 0 else color.alpha)
                if (renderText)
                    Renderer.drawStringInWorld(name, it.positionVector.add(Vec3(0.0, 2.0, 0.0)), depth = false, color = color, scale = 0.03f)
                if (renderBeacon)
                    RenderUtils.drawBeaconBeam(it.positionVector.addVec(-0.5, z = -0.5), color, false)
            }
        }
    }
}