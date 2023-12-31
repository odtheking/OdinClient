package me.odinmain.commands.impl

import com.github.stivais.commodore.parsers.impl.GreedyString
import me.odinmain.OdinMain.mc
import me.odinmain.commands.CommandNode
import me.odinmain.commands.Commodore
import me.odinmain.events.impl.ChatPacketEvent
import me.odinmain.features.impl.dungeon.TPMaze
import me.odinmain.features.impl.render.ClickGUIModule.isDev
import me.odinmain.utils.*
import me.odinmain.utils.skyblock.dungeon.DungeonUtils
import me.odinmain.utils.skyblock.dungeon.ScanUtils
import me.odinmain.utils.skyblock.getChatBreak
import me.odinmain.utils.skyblock.modMessage
import me.odinmain.utils.skyblock.sendCommand
import net.minecraft.util.ChatComponentText
import net.minecraftforge.common.MinecraftForge

object DevCommand : Commodore {

    var devMode = false // add more usage maybe?
        get() = field || isDev

    override val command: CommandNode =
        literal("oddev") {
            requires {
                devMode
            }

            literal("getdata").runs { str: String ->
                if (str == "entity") copyEntityData()
                if (str == "block") copyBlockData()
            }

            literal("testtp").runs {
                TPMaze.getCorrectPortals(
                    mc.thePlayer.positionVector,
                    mc.thePlayer.rotationYaw,
                    mc.thePlayer.rotationPitch
                )
            }

            literal("resettp").runs {
                TPMaze.correctPortals = listOf()
                TPMaze.portals = setOf()
            }

            literal("giveaotv").runs {
                sendCommand("give @p minecraft:diamond_shovel 1 0 {ExtraAttributes:{ethermerge:1b}}")
            }

            literal("sendmessage").runs { string: String ->
                sendDataToServer("""{"$string": "This is a test message"}""")
                modMessage("""{"$string": "This is a test message"}""")
            }

            literal("simulate").runs { str: GreedyString ->
                mc.thePlayer.addChatMessage(ChatComponentText(str.string))
                MinecraftForge.EVENT_BUS.post(ChatPacketEvent(str.string))
            }

            literal("roomdata").runs {
                val room = DungeonUtils.currentRoom //?: return@does modMessage("§cYou are not in a dungeon!")
                val x = ((mc.thePlayer.posX + 200) / 32).toInt()
                val z = ((mc.thePlayer.posZ + 200) / 32).toInt()
                val xPos = -185 + x * 32
                val zPos = -185 + z * 32
                val core = ScanUtils.getCore(xPos, zPos)
                val northPos = DungeonUtils.Vec2(xPos, zPos - 4)
                val northCores = room?.positions?.map {
                    modMessage("Scanning ${it.x}, ${it.z - 4}: ${ScanUtils.getCore(it.x, it.z - 4)}")
                    ScanUtils.getCore(it.x, it.z - 4)
                } ?: listOf()

                modMessage(
                    """
                    ${getChatBreak()}
                    Middle: $xPos, $zPos
                    Room: ${room?.room?.data?.name}
                    Core: $core
                    North Core: $northCores
                    North Pos: ${northPos.x}, ${northPos.z}
                    Rotation: ${room?.room?.rotation}
                    Positions: ${room?.positions}
                    ${getChatBreak()}
                    """.trimIndent(), false
                )
                writeToClipboard(northCores.toString(), "Copied $northCores to clipboard!")
            }

            literal("getCore").runs {
                val core = ScanUtils.getCore(mc.thePlayer.posX.floor().toInt(), mc.thePlayer.posZ.floor().toInt())
                writeToClipboard(core.toString(), "Copied $core to clipboard!")
            }
        }
}
