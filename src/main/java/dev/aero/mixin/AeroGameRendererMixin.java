package dev.aero.mixin;

import dev.aero.AeroClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class AeroGameRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V", at = @At("HEAD"))
    private void aero$frame(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        AeroClient.renderFrame(MinecraftClient.getInstance());
    }

    @Inject(method = "render(Lnet/minecraft/client/render/RenderTickCounter;Z)V", at = @At("TAIL"))
    private void aero$capture(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        AeroClient.captureAfterFrame(MinecraftClient.getInstance());
    }
}
