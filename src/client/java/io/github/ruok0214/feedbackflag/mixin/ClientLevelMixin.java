package io.github.ruok0214.feedbackflag.mixin;

import io.github.ruok0214.feedbackflag.FeedbackFlagClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {
    @Inject(method = "setServerVerifiedBlockState", at = @At("HEAD"))
    private void feedbackFlag$observe(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        if (FeedbackFlagClient.manager != null)
            FeedbackFlagClient.manager.observed((ClientLevel)(Object)this, pos, state);
    }
    @Inject(method = "unload", at = @At("HEAD"))
    private void feedbackFlag$unload(LevelChunk chunk, CallbackInfo ci) {
        if (FeedbackFlagClient.manager != null)
            FeedbackFlagClient.manager.unloaded((ClientLevel)(Object)this, chunk.getPos().x(), chunk.getPos().z());
    }
}
