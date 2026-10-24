package uk.co.iceconchy.aerowarptics.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import uk.co.iceconchy.aerowarptics.AWConfig;
import uk.co.iceconchy.aerowarptics.AeroWarptics;
import uk.co.iceconchy.aerowarptics.flotilla.Formation;
import uk.co.iceconchy.aerowarptics.flotilla.FormationSlot;
import uk.co.iceconchy.aerowarptics.flotilla.FleetMuster;
import uk.co.iceconchy.aerowarptics.network.ClientboundFlotillaCuePacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundMusterPacket;
import uk.co.iceconchy.aerowarptics.network.ClientboundMusterPacket.Entry;
import uk.co.iceconchy.aerowarptics.registry.AWParticles;
import uk.co.iceconchy.aerowarptics.registry.AWSounds;
import uk.co.iceconchy.aerowarptics.util.AWLang;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What a client shows and plays of the flotillas it can see: the muster, and every beat of a fold.
 *
 * <p>Nothing here decides anything; the server sends the muster twice a second and a cue for each beat,
 * and this dresses them. The rest of the mod's effect systems are borrowed wherever they fit - the
 * beacon's additive beam for the tethers, the clearance overlay's line boxes for the slots, the warp's
 * own shake and streak particles for the countdown, the Rift Storm's sky for the dimming - so a fleet
 * reads as the same machinery as a single ship.
 *
 * <h2>The sequence, as heard</h2>
 * Sound Stations: a horn on every ship, the tethers snapping out. Harmonising: every drive's hum,
 * each escort's sliding onto the flagship's note, its tether steadying as it arrives. When the last one
 * does, the hums resolve into a chord. The countdown: a rising note, the sky dimming, lightning along the
 * tethers, streaks pulling forward, the camera starting to shake. Then the fleet goes, flagship first.
 *
 * <p>Client state kept in static fields like every other effect here, and cleared by
 * {@code LevelChangeReset} on a change of dimension and on logging out.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = AeroWarptics.MODID, value = Dist.CLIENT)
public final class FlotillaEffects {

    /** Musters by flagship ship, with the client tick they were last heard of. */
    static final Map<UUID, Heard> MUSTERS = new HashMap<>();

    /** A muster packet and when it arrived. */
    record Heard(ClientboundMusterPacket packet, long tick) {
    }

    /** Hums playing, by which drive. */
    private static final Map<String, FlotillaHum> HUMS = new HashMap<>();

    /** How long a muster is drawn after the last word of it: three missed broadcasts. */
    private static final int LAPSE = 30;
    /** At most this many drives hum at once, the nearest first. A fleet of twenty is a wall of noise. */
    private static final int MAX_HUMS = 9;
    /** Ticks the shaken-loose flash lasts. */
    private static final int FLASH_TICKS = 30;
    /** Ticks the tethers stay bright after snapping out. */
    private static final int SNAP_TICKS = 20;
    /** Points along a tether. */
    private static final int TETHER_POINTS = 14;
    private static final int GLOW_LIGHT = 240;

    private static long ticks;
    private static int countdownLeft;
    private static int countdownTotal;
    private static int flash;
    private static int snap;

    private FlotillaEffects() {
    }

    // ----------------------------------------------------------------- packets

    public static void accept(ClientboundMusterPacket packet) {
        MUSTERS.put(packet.flagshipShip(), new Heard(packet, ticks));
    }

    public static void cue(ClientboundFlotillaCuePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        switch (packet.cue()) {
            case SOUND_STATIONS -> title(AWLang.translate("gui.flotilla.title.sound_stations")
                    .style(ChatFormatting.GOLD).component(), null);
            case TETHERS_OUT -> snap = SNAP_TICKS;
            case RESOLVED -> {
                for (float pitch : FlotillaVisuals.chord(flagshipTier())) {
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(AWSounds.FLOTILLA_CHORD.get(),
                            pitch, 0.7F));
                }
                if (minecraft.player != null) {
                    minecraft.player.displayClientMessage(AWLang.translate("gui.flotilla.resolved")
                            .style(ChatFormatting.AQUA).component(), true);
                }
            }
            case STOOD_DOWN -> countdownLeft = 0;
            case COUNTDOWN -> {
                countdownTotal = Math.max(1, packet.ticks());
                countdownLeft = countdownTotal;
            }
            case SLOT_STATE -> title(packet.good()
                    ? AWLang.translate("gui.flotilla.title.slot_in_station", packet.slot())
                            .style(ChatFormatting.GREEN).component()
                    : AWLang.translate("gui.flotilla.title.slot_adrift", packet.slot())
                            .style(ChatFormatting.RED).component(), null);
            case CALLED_OFF -> {
                countdownLeft = 0;
                if (minecraft.player != null) {
                    minecraft.player.displayClientMessage(AWLang.translate("gui.flotilla.called_off")
                            .style(ChatFormatting.GRAY).component(), true);
                }
            }
            case ASSEMBLED -> title(AWLang.translate("gui.flotilla.title.assembled")
                    .style(ChatFormatting.AQUA).component(), packet.good() ? null
                    : AWLang.translate("gui.flotilla.subtitle.straggler").style(ChatFormatting.GRAY).component());
            case ECHO_INBOUND -> {
                // The sound is the server's, played at every ship; this says what it was.
                if (minecraft.player != null) {
                    minecraft.player.displayClientMessage(AWLang.translate("gui.flotilla.echo_inbound")
                            .style(ChatFormatting.GRAY).component(), true);
                }
            }
            case SHAKEN_LOOSE -> {
                flash = FLASH_TICKS;
                title(AWLang.translate("gui.flotilla.title.shaken_loose").style(ChatFormatting.GRAY).component(),
                        null);
            }
            case LEFT_BEHIND -> title(AWLang.translate("gui.flotilla.title.left_behind")
                    .style(ChatFormatting.RED).component(), AWLang.translate("gui.flotilla.subtitle.left_behind")
                    .style(ChatFormatting.GRAY).component());
        }
    }

    /** A title, if the player has not turned them off. The sounds of a beat play either way. */
    private static void title(Component title, @Nullable Component subtitle) {
        if (!AWConfig.FLOTILLA_TITLES.get()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gui.setTimes(4, 36, 14);
        minecraft.gui.setSubtitle(subtitle == null ? Component.empty() : subtitle);
        minecraft.gui.setTitle(title);
    }

    /** The flagship tier of the muster this client knows best - the chord is rooted on its note. */
    private static int flagshipTier() {
        for (Heard heard : MUSTERS.values()) {
            return heard.packet().flagshipTier();
        }
        return 2;
    }

    public static void clear() {
        MUSTERS.clear();
        for (FlotillaHum hum : HUMS.values()) {
            hum.end();
        }
        HUMS.clear();
        countdownLeft = 0;
        countdownTotal = 0;
        flash = 0;
        snap = 0;
    }

    // -------------------------------------------------------------------- tick

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            if (!MUSTERS.isEmpty() || !HUMS.isEmpty()) {
                clear();
            }
            return;
        }
        ticks++;
        MUSTERS.values().removeIf(heard -> ticks - heard.tick() > LAPSE);
        if (flash > 0) {
            flash--;
        }
        if (snap > 0) {
            snap--;
        }
        if (countdownLeft > 0) {
            countdownLeft--;
            float progress = 1.0F - countdownLeft / (float) countdownTotal;
            WarpScreenShake.add(FlotillaVisuals.countdownShake(progress, (float) (double) AWConfig.COUNTDOWN_SHAKE.get()));
            RiftStormSky.lend((float) (double) AWConfig.COUNTDOWN_DIMMING.get() * progress);
            streaks(minecraft, level, progress);
        }
        hums(minecraft, level);
    }

    /** Streak particles pulling forward past the crew, thicker as the countdown runs out. */
    private static void streaks(Minecraft minecraft, ClientLevel level, float progress) {
        Player player = minecraft.player;
        Heard nearest = nearest(player == null ? Vec3.ZERO : player.position());
        if (player == null || nearest == null) {
            return;
        }
        Pose3dc pose = TerrainHologram.poseOf(level, nearest.packet().flagshipCoupler(), 1.0F);
        if (pose == null) {
            return;
        }
        Vector3d bow = pose.transformNormal(new Vector3d(nearest.packet().bowX(), 0, nearest.packet().bowZ()));
        double density = AWConfig.PARTICLE_DENSITY.get();
        int count = (int) Math.round(4.0D * progress * density);
        for (int i = 0; i < count; i++) {
            double ox = (level.random.nextDouble() - 0.5D) * 24.0D;
            double oy = (level.random.nextDouble() - 0.3D) * 10.0D;
            double oz = (level.random.nextDouble() - 0.5D) * 24.0D;
            double speed = 0.6D + progress * 1.4D;
            level.addParticle(AWParticles.WARP_STREAK.get(), player.getX() + ox - bow.x * 10.0D,
                    player.getY() + oy, player.getZ() + oz - bow.z * 10.0D,
                    bow.x * speed, 0.0D, bow.z * speed);
        }
    }

    /**
     * Keeps a hum playing for every drive in every muster within earshot, the nearest few of them, and
     * lets each stop itself when its muster or its ship is gone.
     */
    private static void hums(Minecraft minecraft, ClientLevel level) {
        HUMS.values().removeIf(FlotillaHum::isStopped);
        Player player = minecraft.player;
        if (player == null) {
            return;
        }
        record Want(String key, int tier, java.util.function.Supplier<FlotillaHum.Voice> voice, double distance) {
        }
        List<Want> wanted = new ArrayList<>();
        for (Heard heard : MUSTERS.values()) {
            ClientboundMusterPacket packet = heard.packet();
            if (!humming(packet)) {
                continue;
            }
            UUID flagship = packet.flagshipShip();
            Vec3 at = worldOf(level, packet.flagshipCoupler());
            if (at != null) {
                wanted.add(new Want("f" + flagship, packet.flagshipTier(), () -> flagshipVoice(flagship),
                        at.distanceToSqr(player.position())));
            }
            for (Entry entry : packet.escorts()) {
                Vec3 escortAt = worldOf(level, entry.coupler());
                if (escortAt == null) {
                    continue;
                }
                BlockPos coupler = entry.coupler();
                wanted.add(new Want("e" + flagship + coupler.asLong(), entry.tier(),
                        () -> escortVoice(flagship, coupler), escortAt.distanceToSqr(player.position())));
            }
        }
        wanted.sort(java.util.Comparator.comparingDouble(Want::distance));
        for (int i = 0; i < Math.min(MAX_HUMS, wanted.size()); i++) {
            Want want = wanted.get(i);
            if (!HUMS.containsKey(want.key())) {
                FlotillaHum hum = new FlotillaHum(want.tier(), want.voice());
                HUMS.put(want.key(), hum);
                minecraft.getSoundManager().play(hum);
            }
        }
    }

    /** Hums play from the horn until the rifts open. */
    private static boolean humming(ClientboundMusterPacket packet) {
        int phase = packet.phase();
        return phase >= FleetMuster.Phase.MUSTER.ordinal() && phase < FleetMuster.Phase.FOLDING.ordinal();
    }

    @Nullable
    private static FlotillaHum.Voice flagshipVoice(UUID flagship) {
        Heard heard = MUSTERS.get(flagship);
        ClientLevel level = Minecraft.getInstance().level;
        if (heard == null || level == null || !humming(heard.packet())) {
            return null;
        }
        Vec3 at = worldOf(level, heard.packet().flagshipCoupler());
        return at == null ? null : new FlotillaHum.Voice(at, FlotillaVisuals.humPitch(heard.packet().flagshipTier()), 0.45F);
    }

    @Nullable
    private static FlotillaHum.Voice escortVoice(UUID flagship, BlockPos coupler) {
        Heard heard = MUSTERS.get(flagship);
        ClientLevel level = Minecraft.getInstance().level;
        if (heard == null || level == null || !humming(heard.packet())) {
            return null;
        }
        for (Entry entry : heard.packet().escorts()) {
            if (entry.coupler().equals(coupler)) {
                Vec3 at = worldOf(level, coupler);
                return at == null ? null : new FlotillaHum.Voice(at, FlotillaVisuals.slide(entry.tier(),
                        heard.packet().flagshipTier(), entry.harmony(), ticks), 0.35F);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------- world

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // Under a shader pack this is drawn once the pack has finished the frame - see AfterShaders.
        if (AfterShaders.defer(event)) {
            return;
        }
        if (MUSTERS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.level instanceof ClientLevel level)) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 eye = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS && AWConfig.SLOT_MARKERS.get()) {
            pose.pushPose();
            pose.translate(-eye.x, -eye.y, -eye.z);
            MultiBufferSource.BufferSource buffers = AfterShaders.buffers();
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            for (Heard heard : MUSTERS.values()) {
                slots(pose, lines, level, heard.packet(), partialTick);
            }
            buffers.endBatch(RenderType.lines());
            pose.popPose();
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES && AWConfig.HARMONIC_TETHERS.get()) {
            pose.pushPose();
            pose.translate(-eye.x, -eye.y, -eye.z);
            Matrix4f matrix = pose.last().pose();
            MultiBufferSource.BufferSource buffers = AfterShaders.buffers();
            VertexConsumer buffer = buffers.getBuffer(AWRenderTypes.RIFT_FIRE);
            boolean arcs = !minecraft.options.hideLightningFlash().get() && AWConfig.RIFT_LIGHTNING.get();
            for (Heard heard : MUSTERS.values()) {
                tethers(buffer, matrix, eye, level, heard.packet(), partialTick, arcs);
            }
            buffers.endBatch(AWRenderTypes.RIFT_FIRE);
            pose.popPose();
        }
    }

    /**
     * Each escort's slot in the sky, as a box the size of the room its ship takes: amber while empty or
     * adrift, green once it is in station. Drawn in the flagship's frame, so the formation turns with it.
     */
    private static void slots(PoseStack pose, VertexConsumer lines, ClientLevel level, ClientboundMusterPacket packet,
                              float partialTick) {
        if (packet.phase() == FleetMuster.Phase.FOLDING.ordinal()) {
            return;
        }
        Pose3dc flagship = TerrainHologram.poseOf(level, packet.flagshipCoupler(), partialTick);
        if (flagship == null) {
            return;
        }
        float pulse = 0.55F + 0.25F * Mth.sin((ticks + partialTick) * 0.15F);
        for (Entry entry : packet.escorts()) {
            double[] step = Formation.toShip(new FormationSlot(entry.right(), entry.up(), entry.ahead()),
                    packet.bowX(), packet.bowZ());
            Vec3 at = flagship.transformPosition(packet.centre().add(step[0], step[1], step[2]));
            double r = Math.max(1.0D, entry.radius());
            double h = Math.max(1.0D, entry.halfHeight());
            AABB box = new AABB(at.x - r, at.y - h, at.z - r, at.x + r, at.y + h, at.z + r);
            if (entry.inStation()) {
                LevelRenderer.renderLineBox(pose, lines, box, 0.29F, 0.85F, 0.77F, pulse);
            } else {
                LevelRenderer.renderLineBox(pose, lines, box, 1.00F, 0.72F, 0.25F, pulse);
            }
        }
    }

    /**
     * The harmonic tethers, from the flagship's coupler to each escort's, in the escort's own colour:
     * ragged and flickering while its drive finds the note, straight and steady once it has. Through the
     * countdown, rift lightning runs along them.
     */
    private static void tethers(VertexConsumer buffer, Matrix4f matrix, Vec3 eye, ClientLevel level,
                                ClientboundMusterPacket packet, float partialTick, boolean arcs) {
        if (packet.phase() == FleetMuster.Phase.STANDBY.ordinal()
                || packet.phase() == FleetMuster.Phase.FOLDING.ordinal()) {
            return;
        }
        Vec3 from = worldOf(level, packet.flagshipCoupler(), partialTick);
        if (from == null) {
            return;
        }
        float time = ticks + partialTick;
        boolean counting = packet.phase() == FleetMuster.Phase.COUNTDOWN.ordinal();
        for (Entry entry : packet.escorts()) {
            Vec3 to = worldOf(level, entry.coupler(), partialTick);
            if (to == null || from.distanceToSqr(to) > 512.0D * 512.0D) {
                continue;
            }
            int seed = entry.number() * 7919;
            float alpha = FlotillaVisuals.tetherAlpha(entry.harmony(), time, seed) * (snap > 0 ? 1.3F : 1.0F);
            float sag = FlotillaVisuals.tetherSag(entry.harmony());
            List<Vec3> points = new ArrayList<>(TETHER_POINTS + 1);
            for (int i = 0; i <= TETHER_POINTS; i++) {
                float t = i / (float) TETHER_POINTS;
                double bend = Math.sin(Math.PI * t) * sag * Math.sin(time * 0.9D + i * 1.7D + seed);
                points.add(from.lerp(to, t).add(0.0D, bend, 0.0D));
            }
            int colour = entry.tether() & 0xFF_FF_FF;
            ribbon(buffer, matrix, eye, points, 0.08F, colour, Math.min(1.0F, alpha));
            ribbon(buffer, matrix, eye, points, 0.35F, colour, Math.min(1.0F, alpha) * 0.25F);
            if (counting && arcs) {
                arc(buffer, matrix, eye, from, to, time, seed, packet.countdown());
            }
        }
    }

    /**
     * A bolt of rift lightning along a tether: a jagged run between the two couplers, redrawn every few
     * ticks, more often and brighter as the countdown runs out.
     */
    private static void arc(VertexConsumer buffer, Matrix4f matrix, Vec3 eye, Vec3 from, Vec3 to, float time, int seed,
                            float progress) {
        int period = Math.max(2, 7 - Math.round(progress * 5.0F));
        int cycle = (int) (time / period);
        float life = (time % period) / period;
        float alpha = (1.0F - life) * (0.35F + 0.65F * progress);
        if (alpha <= 0.02F) {
            return;
        }
        List<Vec3> points = new ArrayList<>();
        int segments = 10;
        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            double jag = i == 0 || i == segments ? 0.0D : 1.1D;
            points.add(from.lerp(to, t).add(
                    RiftLightning.jitter(seed, 0, cycle, i) * jag,
                    RiftLightning.jitter(seed, 1, cycle, i) * jag,
                    RiftLightning.jitter(seed, 2, cycle, i) * jag));
        }
        ribbon(buffer, matrix, eye, points, 0.06F, 0xF0E6FF, alpha);
        ribbon(buffer, matrix, eye, points, 0.22F, 0x8A6BFF, alpha * 0.4F);
    }

    /** A flat strip through a run of points, turned to face the camera along each piece of it. */
    private static void ribbon(VertexConsumer buffer, Matrix4f matrix, Vec3 eye, List<Vec3> points, float half,
                               int colour, float alpha) {
        int argb = (Mth.clamp(Math.round(alpha * 255.0F), 0, 255) << 24) | colour;
        for (int i = 0; i + 1 < points.size(); i++) {
            Vec3 a = points.get(i);
            Vec3 b = points.get(i + 1);
            Vec3 side = b.subtract(a).cross(eye.subtract(a));
            if (side.lengthSqr() < 1.0e-8D) {
                continue;
            }
            side = side.normalize().scale(half);
            vertex(buffer, matrix, a.subtract(side), argb);
            vertex(buffer, matrix, a.add(side), argb);
            vertex(buffer, matrix, b.add(side), argb);
            vertex(buffer, matrix, b.subtract(side), argb);
        }
    }

    /** The beacon beam's vertex, for the beacon beam's reason: {@code RIFT_FIRE}'s format asks for all of it. */
    private static void vertex(VertexConsumer buffer, Matrix4f matrix, Vec3 at, int argb) {
        buffer.addVertex(matrix, (float) at.x, (float) at.y, (float) at.z)
                .setColor(argb)
                .setUv(0.5F, 0.5F)
                .setUv2(GLOW_LIGHT, GLOW_LIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    /** Where a coupler's block is in the world, on whichever ship it rides; {@code null} if that ship is not here. */
    @Nullable
    private static Vec3 worldOf(ClientLevel level, BlockPos pos) {
        return worldOf(level, pos, 1.0F);
    }

    @Nullable
    private static Vec3 worldOf(ClientLevel level, BlockPos pos, float partialTick) {
        Pose3dc pose = TerrainHologram.poseOf(level, pos, partialTick);
        // A coupler's position is in its ship's plot. Without the ship's pose it is somewhere meaningless,
        // thousands of blocks off - which is exactly where a tether must not be drawn to.
        return pose == null ? null : pose.transformPosition(Vec3.atCenterOf(pos));
    }

    @Nullable
    private static Heard nearest(Vec3 from) {
        Heard best = null;
        double bestDistance = Double.MAX_VALUE;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        for (Heard heard : MUSTERS.values()) {
            Vec3 at = worldOf(level, heard.packet().flagshipCoupler());
            if (at != null && at.distanceToSqr(from) < bestDistance) {
                bestDistance = at.distanceToSqr(from);
                best = heard;
            }
        }
        return best;
    }

    // --------------------------------------------------------------------- hud

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, AeroWarptics.id("flotilla"), FlotillaEffects::renderHud);
    }

    /**
     * The escort pilot's station HUD, and the shaken-loose flash.
     *
     * <p>Holding station is the whole of an escort pilot's job while a muster is on, and they should not
     * need a screen open to do it: so, above the hotbar, which slot they have, how far off it they are, and
     * an arrow the way to go - turned with the player, so "up" is always "the way you are facing" - with a
     * word about height when they are above or below it.
     */
    private static void renderHud(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        if (flash > 0 && !minecraft.options.hideLightningFlash().get()) {
            // Grey, not white: a straggler's world goes drained for a moment rather than bright.
            float alpha = flash / (float) FLASH_TICKS * 0.55F;
            graphics.fill(0, 0, width, height, (Mth.clamp(Math.round(alpha * 255.0F), 0, 255) << 24) | 0x8C8C8C);
        }
        if (!AWConfig.STATION_HUD.get() || minecraft.player == null || !(minecraft.level instanceof ClientLevel level)) {
            return;
        }
        SubLevel aboard = Sable.HELPER.getTrackingOrVehicleSubLevel(minecraft.player);
        if (aboard == null) {
            return;
        }
        UUID ship = aboard.getUniqueId();
        for (Heard heard : MUSTERS.values()) {
            ClientboundMusterPacket packet = heard.packet();
            if (!humming(packet)) {
                continue;
            }
            for (Entry entry : packet.escorts()) {
                if (ship.equals(entry.ship())) {
                    station(graphics, minecraft, level, packet, entry, width, height);
                    return;
                }
            }
        }
    }

    private static void station(GuiGraphics graphics, Minecraft minecraft, ClientLevel level,
                                ClientboundMusterPacket packet, Entry entry, int width, int height) {
        float drift = (float) Math.sqrt(entry.dRight() * entry.dRight() + entry.dUp() * entry.dUp()
                + entry.dAhead() * entry.dAhead());
        boolean inStation = entry.inStation();
        int colour = inStation ? 0xFF_49_D9_C4 : 0xFF_E0_B0_4A;
        int cx = width / 2;
        int top = height - 72;
        String label = AWLang.translate("gui.flotilla.hud.slot", entry.number()).string();
        String reading = inStation ? AWLang.translate("gui.flotilla.hud.in_station").string()
                : AWLang.translate("gui.flotilla.hud.drift", AWLang.distance(drift)).string();
        int panel = Math.max(minecraft.font.width(label), minecraft.font.width(reading)) + 30;
        graphics.fill(cx - panel / 2, top - 2, cx + panel / 2, top + 20, 0x88_10_0C_14);
        graphics.drawString(minecraft.font, label, cx - panel / 2 + 24, top, 0xFF_D9_C3_92, false);
        graphics.drawString(minecraft.font, reading, cx - panel / 2 + 24, top + 10, colour, false);

        // The arrow, in a small circle to the left of the text.
        int ax = cx - panel / 2 + 11;
        int ay = top + 9;
        if (inStation) {
            graphics.fill(ax - 3, ay - 3, ax + 3, ay + 3, colour);
        } else {
            Pose3dc flagship = TerrainHologram.poseOf(level, packet.flagshipCoupler(), 1.0F);
            if (flagship != null) {
                Vector3d bow = flagship.transformNormal(new Vector3d(packet.bowX(), 0, packet.bowZ()));
                double angle = FlotillaVisuals.arrow(entry.dRight(), entry.dAhead(), minecraft.player.getYRot(),
                        FlotillaVisuals.yawOf(bow.x, bow.z));
                graphics.pose().pushPose();
                graphics.pose().translate(ax, ay, 0);
                graphics.pose().mulPose(Axis.ZP.rotation((float) angle));
                graphics.fill(-1, -7, 1, 5, colour);
                graphics.fill(-3, -5, 3, -4, colour);
                graphics.fill(-2, -6, 2, -5, colour);
                graphics.pose().popPose();
            }
        }
        // Above or below the slot by more than half the tolerance: say so, since the arrow is flat.
        if (Math.abs(entry.dUp()) > packet.tolerance() * 0.5F) {
            String vertical = entry.dUp() > 0
                    ? AWLang.translate("gui.flotilla.hud.descend", AWLang.distance(Math.abs(entry.dUp()))).string()
                    : AWLang.translate("gui.flotilla.hud.climb", AWLang.distance(Math.abs(entry.dUp()))).string();
            graphics.drawCenteredString(minecraft.font, vertical, cx, top + 22, 0xFF_E0_B0_4A);
        }
    }
}
