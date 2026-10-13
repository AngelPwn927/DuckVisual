package dev.duckvisual.module.impl.visual;

import dev.duckvisual.event.WorldRender;
import dev.duckvisual.module.Category;
import dev.duckvisual.module.Module;
import dev.duckvisual.setting.BooleanSetting;
import dev.duckvisual.setting.NumberSetting;
import dev.duckvisual.util.ColorUtil;
import dev.duckvisual.util.Render3D;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Predicts where the held projectile (bow, crossbow, trident, pearl, snowball, egg,
 * potions, xp bottle) will land, and optionally the path of projectiles already in flight.
 * Physics follow vanilla: per-tick gravity, then drag.
 */
public class Trajectories extends Module {
    private final BooleanSetting held = add(new BooleanSetting("Held Item", true));
    private final BooleanSetting inFlight = add(new BooleanSetting("In-Flight", true));
    private final BooleanSetting landing = add(new BooleanSetting("Landing Marker", true));
    private final BooleanSetting pearlInfo = add(new BooleanSetting("Pearl Owner + Time", true));
    private final NumberSetting maxTicks = add(new NumberSetting("Max Ticks", 150, 30, 400, 10));

    private record Physics(double speed, double gravity, double drag, float pitchOffset) {}

    private record Path(List<Vec3d> points, HitResult.Type hit, Vec3d end, Entity entityHit, Vec3d normal) {}

    public Trajectories() {
        super("Trajectories", "Shows where projectiles will land", Category.VISUALS);
    }

    private Physics physicsFor(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof BowItem) {
            int use = mc.player.getActiveItem() == stack ? mc.player.getItemUseTime() : 0;
            float pull = BowItem.getPullProgress(use);
            if (pull < 0.1f) return null;
            return new Physics(pull * 3.0, 0.05, 0.99, 0);
        }
        if (item instanceof CrossbowItem) return CrossbowItem.isCharged(stack) ? new Physics(3.15, 0.05, 0.99, 0) : null;
        if (item instanceof TridentItem) {
            return mc.player.getActiveItem() == stack && mc.player.getItemUseTime() >= 10 ? new Physics(2.5, 0.05, 0.99, 0) : null;
        }
        if (item instanceof EnderPearlItem || item instanceof SnowballItem || item instanceof EggItem) return new Physics(1.5, 0.03, 0.99, 0);
        if (item instanceof SplashPotionItem || item instanceof LingeringPotionItem) return new Physics(0.5, 0.05, 0.99, -20);
        if (item instanceof ExperienceBottleItem) return new Physics(0.7, 0.07, 0.99, -20);
        return null;
    }

    @Override
    public void onRender3D(WorldRender r) {
        float td = r.tickDelta();
        if (held.get()) {
            for (ItemStack stack : new ItemStack[]{mc.player.getMainHandStack(), mc.player.getOffHandStack()}) {
                Physics ph = physicsFor(stack);
                if (ph == null) continue;
                float yaw = mc.player.getYaw(td), pitch = mc.player.getPitch(td);
                Vec3d dir = direction(pitch + ph.pitchOffset, yaw);
                Vec3d vel = dir.multiply(ph.speed);
                Vec3d pv = mc.player.getVelocity();
                vel = vel.add(pv.x, mc.player.isOnGround() ? 0 : pv.y, pv.z);
                Vec3d start = mc.player.getLerpedPos(td).add(0, mc.player.getStandingEyeHeight() - 0.1, 0);
                Path path = simulate(start, vel, ph.gravity, ph.drag, mc.player);
                draw(r, path, 0);
                break;
            }
        }
        if (inFlight.get()) {
            int i = 1;
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof ProjectileEntity)) continue;
                double gravity, drag = 0.99;
                if (e instanceof PersistentProjectileEntity pe) {
                    if (pe.isOnGround() || e.getVelocity().lengthSquared() < 0.01) continue;
                    gravity = 0.05;
                } else if (e instanceof ThrownItemEntity) {
                    gravity = 0.03;
                } else continue;
                Path path = simulate(e.getLerpedPos(td), e.getVelocity(), gravity, drag, e);
                draw(r, path, i++ * 6);
                if (pearlInfo.get() && e instanceof net.minecraft.entity.projectile.thrown.EnderPearlEntity pearl && path.hit != HitResult.Type.MISS) {
                    String owner = pearl.getOwner() != null ? pearl.getOwner().getName().getString() : "Pearl";
                    float secs = (path.points.size() - 1) / 20f;
                    Render3D.text(r, owner + " \u00A77" + String.format("%.1fs", secs), r.rel(path.end).add(0, 0.6, 0), 1.2f,
                            ColorUtil.theme(), true);
                }
            }
        }
    }

    private static Vec3d direction(float pitch, float yaw) {
        float p = pitch * MathHelper.RADIANS_PER_DEGREE, y = yaw * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(-MathHelper.sin(y) * MathHelper.cos(p), -MathHelper.sin(p), MathHelper.cos(y) * MathHelper.cos(p));
    }

    private Path simulate(Vec3d pos, Vec3d vel, double gravity, double drag, Entity owner) {
        List<Vec3d> points = new ArrayList<>();
        points.add(pos);
        for (int t = 0; t < maxTicks.getInt(); t++) {
            Vec3d next = pos.add(vel);
            BlockHitResult bhr = mc.world.raycast(new RaycastContext(pos, next, RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, owner));
            Vec3d end = bhr.getType() == HitResult.Type.MISS ? next : bhr.getPos();
            // entity collision along the segment
            Box sweep = new Box(pos, end).expand(1);
            Entity best = null;
            Vec3d bestHit = null;
            for (Entity e : mc.world.getOtherEntities(owner, sweep, x -> x.canHit() && !x.isSpectator() && x != mc.player)) {
                var hit = e.getBoundingBox().expand(0.3).raycast(pos, end);
                if (hit.isPresent() && (bestHit == null || hit.get().squaredDistanceTo(pos) < bestHit.squaredDistanceTo(pos))) {
                    best = e;
                    bestHit = hit.get();
                }
            }
            if (best != null) {
                points.add(bestHit);
                return new Path(points, HitResult.Type.ENTITY, bestHit, best, null);
            }
            if (bhr.getType() != HitResult.Type.MISS) {
                points.add(end);
                Vec3d n = Vec3d.of(bhr.getSide().getVector());
                return new Path(points, HitResult.Type.BLOCK, end, null, n);
            }
            points.add(next);
            pos = next;
            vel = vel.multiply(mc.world.getBlockState(net.minecraft.util.math.BlockPos.ofFloored(pos)).getFluidState().isEmpty() ? drag : 0.6)
                    .add(0, -gravity, 0);
            if (pos.y < mc.world.getBottomY() - 64) break;
        }
        return new Path(points, HitResult.Type.MISS, pos, null, null);
    }

    private void draw(WorldRender r, Path path, int colorOffset) {
        if (path.points.size() < 2) return;
        Matrix4f m = r.matrices().peek().getPositionMatrix();
        boolean hitsEntity = path.hit == HitResult.Type.ENTITY;
        BufferBuilder b = Render3D.buffer(VertexFormat.DrawMode.DEBUG_LINE_STRIP);
        int n = path.points.size();
        for (int i = 0; i < n; i++) {
            Vec3d p = r.rel(path.points.get(i));
            // fade in near the player so the line does not block the view
            float a = Math.min(1, i / 4f);
            int c = hitsEntity ? 0xFFFF4545 : ColorUtil.theme(colorOffset + i / 2);
            b.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(ColorUtil.withAlpha(c, (int) (230 * a)));
        }
        Render3D.draw(b);

        if (!landing.get() || path.hit == HitResult.Type.MISS) return;
        Vec3d end = r.rel(path.end);
        int c = hitsEntity ? 0xFFFF4545 : ColorUtil.theme(colorOffset);
        if (hitsEntity) {
            Box box = path.entityHit.getBoundingBox().offset(path.entityHit.getPos().negate()).offset(r.rel(path.entityHit.getPos()));
            Render3D.filledBox(r, box, ColorUtil.withAlpha(c, 50), ColorUtil.withAlpha(c, 50));
            Render3D.outlineBox(r, box, c, c);
        } else if (path.normal != null && Math.abs(path.normal.y) > 0.5) {
            Render3D.ring(r, end.add(0, 0.02 * Math.signum(path.normal.y), 0), 0.25f, 0.4f,
                    ColorUtil.withAlpha(c, 200), ColorUtil.withAlpha(c, 0));
            Render3D.ring(r, end.add(0, 0.02 * Math.signum(path.normal.y), 0), 0.0f, 0.12f,
                    ColorUtil.withAlpha(c, 220), ColorUtil.withAlpha(c, 220));
        } else {
            Render3D.glow(r, end, 0.35f, ColorUtil.withAlpha(c, 200));
        }
    }
}
