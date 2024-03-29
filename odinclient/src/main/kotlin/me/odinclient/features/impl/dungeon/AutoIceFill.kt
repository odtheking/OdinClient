package me.odinclient.features.impl.dungeon

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.odinclient.utils.skyblock.PlayerUtils.clipTo
import me.odinclient.utils.waitUntilPacked
import me.odinmain.OdinMain.scope
import me.odinmain.features.Category
import me.odinmain.features.Module
import me.odinmain.features.impl.dungeon.puzzlesolvers.IceFillSolver
import me.odinmain.features.impl.dungeon.puzzlesolvers.IceFillSolver.checkRotation
import me.odinmain.features.impl.dungeon.puzzlesolvers.IceFillSolver.currentPatterns
import me.odinmain.features.impl.dungeon.puzzlesolvers.IceFillSolver.transform
import me.odinmain.features.impl.dungeon.puzzlesolvers.IceFillSolver.transformTo
import me.odinmain.features.impl.dungeon.puzzlesolvers.Rotation
import me.odinmain.utils.plus
import me.odinmain.utils.skyblock.PlayerUtils.posFloored
import me.odinmain.utils.skyblock.dungeon.DungeonUtils
import me.odinmain.utils.skyblock.getBlockIdAt
import net.minecraft.util.BlockPos
import net.minecraft.util.Vec3
import net.minecraft.util.Vec3i
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

object AutoIceFill: Module(
    name = "Auto Ice Fill",
    description = "Automatically completes the ice fill puzzle.",
    category = Category.DUNGEON,
    tag = TagType.RISKY
) {
    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END || mc.thePlayer == null || !DungeonUtils.inDungeons || DungeonUtils.currentRoomName != "Ice Fill") return
        val pos = posFloored
        if (getBlockIdAt(BlockPos(pos.x, pos.y - 1, pos.z )) != 79) return
        val floorIndex = pos.y % 70
        if (floorIndex !in IceFillSolver.scanned.indices) return
        if (!IceFillSolver.scanned[floorIndex]) return
        val rotation = checkRotation(pos, floorIndex) ?: return
        if (floorIndex !in currentPatterns.indices) return
        scope.launch {
            move(Vec3(pos.x.toDouble(), pos.y - 1.0, pos.z.toDouble()), currentPatterns[floorIndex], rotation, floorIndex)
        }
    }

    private suspend fun move(pos: Vec3, pattern: List<Vec3i>, rotation: Rotation, floorIndex: Int) {
        val x = mc.thePlayer.posX
        val y = mc.thePlayer.posY - 1
        val z = mc.thePlayer.posZ

        val deferred1 = waitUntilPacked(x, y, z)
        try {
            deferred1.await()
        } catch (e: Exception) {
            return
        }
        val (bx, bz) = transform(pattern[0].x, pattern[0].z, rotation)
        clipTo(x + bx, y + 1, z + bz)
        for (i in 0..pattern.size - 2) {
            val deferred = waitUntilPacked(
                pos + transformTo(pattern[i], rotation)
            )
            try {
                deferred.await()
            } catch (e: Exception) {
                return
            }
            clipTo(
                pos + transformTo(pattern[i + 1], rotation).addVector(0.0, 1.0, 0.0)
            )
        }
        if (floorIndex == 2) return
        val (bx2, bz2) = transform(pattern[pattern.size - 1].x, pattern[pattern.size - 1].z, rotation)
        val deferred = waitUntilPacked(x + bx2, y, z + bz2)
        try {
            deferred.await()
        } catch (e: Exception) {
            return
        }
        clipToNext(pos, rotation, bx2, bz2, floorIndex + 1)
    }

    private fun clipToNext(pos: Vec3, rotation: Rotation, bx: Int, bz: Int, floorIndex: Int) {
        val x = pos.xCoord
        val y = pos.yCoord
        val z = pos.zCoord
        val (nx, ny) = when (rotation) {
            Rotation.EAST -> Pair(0.5f, 0f)
            Rotation.WEST -> Pair(-0.5f, 0f)
            Rotation.SOUTH -> Pair(0f, 0.5f)
            else -> Pair(0f, -0.5f)
        }
        clipTo(x + bx + nx, y + 1.5, z + bz + ny)
        scope.launch {
            delay(100)
            clipTo(x + bx + nx * 2, y + 2, z + bz + ny * 2)
            delay(100)
            clipTo(x + bx + nx * 4, y + 2, z + bz + ny * 4)
            delay(150)
            move(pos + transformTo(Vec3i(bx, 0, bz), rotation), IceFillSolver.currentPatterns[floorIndex], rotation, floorIndex)
        }
    }
}