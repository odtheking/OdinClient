package me.odinclient.mixin.mixins;

import me.odinmain.events.impl.*;
import me.odinmain.features.impl.floor7.p3.TerminalSolver;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiContainer.class, priority = 1)
public class MixinGuiContainer {

    private final GuiContainer gui = (GuiContainer) (Object) this;

    @Shadow
    public Container inventorySlots;

    @Shadow protected int xSize;

    @Shadow protected int ySize;

    @Shadow protected int guiLeft;

    @Shadow protected int guiTop;


    @Inject(method = "drawSlot", at = @At("HEAD"), cancellable = true)
    private void onDrawSlot(Slot slotIn, CallbackInfo ci) {
        if (MinecraftForge.EVENT_BUS.post(new DrawSlotEvent(inventorySlots, gui, slotIn, slotIn.xDisplayPosition, slotIn.yDisplayPosition)))
            ci.cancel();
    }

    @Inject(method = "drawScreen", at = @At(value = "HEAD"), cancellable = true)
    private void startDrawScreen(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        DrawGuiScreenEvent event = new DrawGuiScreenEvent(gui.inventorySlots, gui, this.xSize, this.ySize, guiLeft, guiTop);
        if (MinecraftForge.EVENT_BUS.post(event))
            ci.cancel();
    }

    @Inject(method = "drawSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/RenderItem;renderItemAndEffectIntoGUI(Lnet/minecraft/item/ItemStack;II)V"), cancellable = true)
    private void size(Slot slotIn, CallbackInfo ci) {
        if (TerminalSolver.INSTANCE.getShouldBlockWrong() && slotIn.slotNumber <= gui.inventorySlots.inventorySlots.size() - 37) {
            ci.cancel();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(int mouseX, int mouseY, int mouseButton, CallbackInfo ci) {
        if (MinecraftForge.EVENT_BUS.post(new GuiClickEvent(gui.inventorySlots, gui, mouseX, mouseY, mouseButton)))
            ci.cancel();
    }

    @Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true)
    private void keyTyped(char typedChar, int keyCode, CallbackInfo ci) {
        if (MinecraftForge.EVENT_BUS.post(new GuiKeyPressEvent(gui.inventorySlots, gui, keyCode)))
            ci.cancel();
    }

    @Inject(method = "onGuiClosed", at = @At("HEAD"))
    private void onGuiClosed(CallbackInfo ci) {
        MinecraftForge.EVENT_BUS.post(new GuiClosedEvent(gui));
    }
}