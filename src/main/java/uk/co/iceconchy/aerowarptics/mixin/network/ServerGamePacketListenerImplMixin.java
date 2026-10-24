package uk.co.iceconchy.aerowarptics.mixin.network;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import uk.co.iceconchy.aerowarptics.warp.PlayerMoveGuard;

/**
 * Takes the "moved too quickly" exemption away from a move no player could have made.
 *
 * <p>A mixin because there is no event for a player's movement packet. It changes one answer - whether
 * vanilla's own speed check may let the player off - and only for a move of more than a kilometre in a
 * tick; vanilla then refuses it exactly as it refuses any other player moving too quickly. Everything else,
 * the singleplayer owner's exemption and Sable's for players on ships included, is left as it was.
 *
 * <p>The packet's position is read after Sable has turned a deck position into a world one, which it does
 * at the head of this method. See {@link PlayerMoveGuard} for the bug this closes.
 *
 * <p>Nothing else lives here, and nothing may: classes in a mixin package cannot be loaded as ordinary
 * classes.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Shadow
    public ServerPlayer player;

    @Shadow
    private double firstGoodX;

    @Shadow
    private double firstGoodY;

    @Shadow
    private double firstGoodZ;

    @ModifyExpressionValue(method = "handleMovePlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;isSingleplayerOwner()Z"))
    private boolean aerowarptics$refuseImpossibleMoves(boolean exempt,
                                                        @Local(argsOnly = true) ServerboundMovePlayerPacket packet) {
        return PlayerMoveGuard.keepExemption(exempt, player,
                packet.getX(player.getX()) - firstGoodX,
                packet.getY(player.getY()) - firstGoodY,
                packet.getZ(player.getZ()) - firstGoodZ);
    }
}
