package me.odinmain.features.impl.dungeon

import me.odinmain.config.DungeonWaypointConfig
import me.odinmain.features.Category
import me.odinmain.features.Module
import me.odinmain.features.impl.render.DevPlayers
import me.odinmain.features.settings.Setting.Companion.withDependency
import me.odinmain.features.settings.impl.BooleanSetting
import me.odinmain.utils.equal
import me.odinmain.utils.render.Color
import me.odinmain.utils.render.Renderer
import me.odinmain.utils.rotateToNorth
import me.odinmain.utils.skyblock.devMessage
import me.odinmain.utils.skyblock.dungeon.DungeonUtils
import me.odinmain.utils.skyblock.dungeon.tiles.RoomType
import me.odinmain.utils.skyblock.modMessage
import me.odinmain.utils.subtractVec
import net.minecraft.util.AxisAlignedBB
import net.minecraft.util.BlockPos
import net.minecraft.util.Vec3
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

/**
 * Custom Waypoints for Dungeons
 * @author Bonsai
 */
object DungeonWaypoints : Module(
    name = "Dungeon Waypoints",
    description = "Shows waypoints for dungeons.",
    category = Category.DUNGEON,
    tag = TagType.NEW
) {
    private val debugWaypoint: Boolean by BooleanSetting("Debug Waypoint", false).withDependency { DevPlayers.isDev }
    private var allowEdits: Boolean by BooleanSetting("Allow Edits", false)

    data class DungeonWaypoint(val x: Double, val y: Double, val z: Double, val color: Color)

    override fun onKeybind() {
        allowEdits = !allowEdits
    }

    @SubscribeEvent
    fun onRender(event: RenderWorldLastEvent) {
        DungeonUtils.currentRoom?.waypoints?.forEach {
            Renderer.drawBox(it.toAABB(), it.color, fillAlpha = 0f)
        }

        if (debugWaypoint) {
            val room = DungeonUtils.currentRoom?.room ?: return
            Renderer.drawBox(AxisAlignedBB(room.x - 0.5, 70.0, room.z - 0.5, room.x + 0.5, 70.0, room.z + 0.5), Color.GREEN, fillAlpha = 0f)
        }
    }

    @SubscribeEvent
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || event.world != mc.theWorld || !allowEdits) return
        val room = DungeonUtils.currentRoom?.room ?: return
        val rotationCore = room.rotationCore ?: return modMessage("This room doesn't have a rotationcore ??? Please report this! §7(${room.data.name})")
        val vec = Vec3(event.pos)
            .subtractVec(x = room.x, z = room.z)
            .rotateToNorth(room.rotation)

        val waypoints =
            if (room.data.type != RoomType.NORMAL)
                DungeonWaypointConfig.waypoints.getOrPut(room.data.name) { mutableListOf() }
            else
                DungeonWaypointConfig.waypoints.getOrPut(rotationCore.toString()) { mutableListOf() }

        if (!waypoints.any { it.toVec3().equal(vec) }) {
            waypoints.add(DungeonWaypoint(vec.xCoord, vec.yCoord, vec.zCoord, Color.GREEN))
            devMessage("Added waypoint at $vec")
        } else {
            waypoints.removeIf { it.toVec3().equal(vec) }
            devMessage("Removed waypoint at $vec")
        }
        DungeonWaypointConfig.saveConfig()
        DungeonUtils.setWaypoints()
    }

    fun DungeonWaypoint.toVec3() = Vec3(x, y, z)
    fun DungeonWaypoint.toBlockPos() = BlockPos(x, y, z)

    fun DungeonWaypoint.toAABB() = AxisAlignedBB(x - 0.5, y, z - 0.5, x + 0.5, y + 1.0, z + 0.5)
}