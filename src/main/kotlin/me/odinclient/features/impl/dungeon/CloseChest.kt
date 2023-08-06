package me.odinclient.features.impl.dungeon

import me.odinclient.OdinClient.Companion.mc
import me.odinclient.events.impl.ReceivePacketEvent
import me.odinclient.features.Module
import me.odinclient.features.Category
import me.odinclient.utils.Utils.equalsOneOf
import me.odinclient.utils.skyblock.dungeon.DungeonUtils.inDungeons
import net.minecraft.network.play.client.C0DPacketCloseWindow
import net.minecraft.network.play.server.S2DPacketOpenWindow
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object CloseChest : Module(
    "Block opening secret chests",
    category = Category.DUNGEON,
    description = "Cancels secret chests from opening."
){
    @SubscribeEvent
    fun onOpenWindow(event: ReceivePacketEvent) {
        if (!inDungeons) return
        if (event.packet !is S2DPacketOpenWindow) return
        if (event.packet.windowTitle.unformattedText.equalsOneOf("Chest", "Large Chest") ) {
            mc.netHandler.networkManager.sendPacket(C0DPacketCloseWindow(event.packet.windowId))
            event.isCanceled = true
        }
    }
}