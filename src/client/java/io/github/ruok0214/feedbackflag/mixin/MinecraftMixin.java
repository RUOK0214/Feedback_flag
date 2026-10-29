package io.github.ruok0214.feedbackflag.mixin;

import io.github.ruok0214.feedbackflag.FeedbackFlagClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void feedbackFlag$use(CallbackInfo ci) {
        if (FeedbackFlagClient.manager != null && FeedbackFlagClient.manager.use()) ci.cancel();
    }
}
