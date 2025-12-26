package icu.eseabs0.ntccenchants.impl;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import icu.eseabs0.ntccenchants.NeotccEnchantPluginImpl;
import icu.eseabs0.ntccenchants.util.EntityRayTraceResult;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

public class SonicEnchantmentImpl {
    private final NeotccEnchantPluginImpl plugin;

    public SonicEnchantmentImpl(NeotccEnchantPluginImpl plugin) {
        this.plugin = plugin;
    }

    private static final Map<UUID, Long> lastSonicUse = new HashMap<>();

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastSonicUse.remove(event.getPlayer().getUniqueId());
    }

    public boolean tryTrigger(Player player, ItemStack item) {
        int level = getEnchantmentLevel(item, NamespacedKey.fromString("neotcc:sonic"));
//        int level = getEnchantmentLevel(item, NamespacedKey.fromString("minecraft:sharpness"));
        if (level == 0) {
            return false;
        }

        long now = System.currentTimeMillis();
        Long lastTime = lastSonicUse.get(player.getUniqueId());
        if (lastTime != null && now - lastTime < 500) { // cd 500ms
            return true;
        }
        lastSonicUse.put(player.getUniqueId(), now);

        executeSonicAttack(player, level);
        return true;
    }

    private final double RAYCAST_RANGE                = 22.0; // 22 + 2 + 1 (Side-on hit detection tolerance) = 25
    private final double SONIC_RADIUS                 = 1.0;
    private final double MAX_REDUCTION_DISTANCE       = 20.0;
    private final double[] MAX_DAMAGE_PER_LEVEL       = {5, 4, 6, 8, 15, 25};
    private final double[] DAMAGE_REDUCTION_PER_LEVEL = {2, 0, 0, 0, 0, 10};
    private final double[] PLAYER_DAMAGE_PER_LEVEL    = {2, 2, 3, 4, 5, 8};

    @SuppressWarnings("UnstableApiUsage")
    private void executeSonicAttack(Player player, int level) {
        int extra = Math.max(0, level - 5);
        level     = Math.clamp(level, 0, 5);
        double maxDamage       = MAX_DAMAGE_PER_LEVEL[level]       + extra * MAX_DAMAGE_PER_LEVEL[0];
        double damageReduction = DAMAGE_REDUCTION_PER_LEVEL[level] + extra * DAMAGE_REDUCTION_PER_LEVEL[0];
        double playerDamage    = PLAYER_DAMAGE_PER_LEVEL[level]    + extra * PLAYER_DAMAGE_PER_LEVEL[0];

        DamageSource sonicDamageWithoutSource = DamageSource.builder(DamageType.SONIC_BOOM)
                .build();
        DamageSource indirectMagicDamage = DamageSource.builder(DamageType.INDIRECT_MAGIC)
                .withCausingEntity(player)
                .withDirectEntity(player)
                .build();

        Location eyeLoc = player.getEyeLocation();
        Vector eyeDir = eyeLoc.getDirection().normalize();

        List<EntityRayTraceResult> frontalHitResult = rayTraceEntities(
                eyeLoc.clone().add(eyeDir.clone().normalize().multiply(2)),
                eyeDir,
                RAYCAST_RANGE,
                SONIC_RADIUS,
                entity -> {
                    if (!(entity instanceof LivingEntity)) return false;
                    if (entity instanceof ArmorStand) return false;
                    return entity != player;
                },
                false
        );

        List<EntityRayTraceResult> collideHitResult = rayTraceEntities(
                eyeLoc,
                eyeDir,
                1.0,
                0.2,
                entity -> {
                    if (!(entity instanceof LivingEntity)) return false;
                    if (entity instanceof ArmorStand) return false;
                    return entity != player;
                },
                false
        );

        List<EntityRayTraceResult> combined = new ArrayList<>();
        combined.addAll(collideHitResult);
        combined.addAll(frontalHitResult);

        List<EntityRayTraceResult> entityResults = new ArrayList<>(
                new LinkedHashMap<Entity, EntityRayTraceResult>() {{
                    for (EntityRayTraceResult result : combined) {
                        putIfAbsent(result.getEntity(), result);
                    }
                }}.values()
        );

        for (EntityRayTraceResult result : entityResults) {
            // MUST be LivingEntity due to the filter above
            LivingEntity entity = (LivingEntity) result.getEntity();

            if (entity instanceof Player) {
                entity.getScheduler().run(
                        plugin,
                        task -> entity.damage(playerDamage, indirectMagicDamage),
                        null
                );
            } else {
                entity.getScheduler().run(
                        plugin,
                        task -> {
                            entity.damage(0, player);
                            if (damageReduction != 0) {
                                entity.damage(
                                maxDamage - Math.clamp(result.getDistance() - 3, 0, MAX_REDUCTION_DISTANCE)
                                                        / MAX_REDUCTION_DISTANCE * damageReduction,
                                        sonicDamageWithoutSource
                                );
                            } else {
                                entity.damage(maxDamage, sonicDamageWithoutSource);
                            }
                        },
                        null
                );
            }
        }

        if (player.getPitch() > 15.6) { // magic number
            player.damage(playerDamage, indirectMagicDamage);
        }

        Location endLoc = eyeLoc.clone().add(eyeDir.clone().multiply(RAYCAST_RANGE));
        renderSonicBoom(eyeLoc, endLoc);

        player.getWorld().playSound(player, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.0f);
    }

    private int getEnchantmentLevel(ItemStack item, NamespacedKey str) {
        try {
            Enchantment enc = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).getOrThrow(str);
            return item.getEnchantmentLevel(enc);
        } catch (NoSuchElementException e) {
            return 0;
        }
    }

    // source from Abs01uteMagicBullet with modification
    // why and when did i write this shit(?)
    public static List<EntityRayTraceResult> rayTraceEntities(
            /* params needed for bukkit raytrace */
            Location start, Vector direction, double maxDistance, double raySize, Predicate<? super Entity> filter,
            /* params for return value modification */
            boolean sortByDistance) {

        Preconditions.checkArgument(start != null, "Location start cannot be null");
        Preconditions.checkArgument(start.isFinite(), "Location start is not finite");

        Preconditions.checkArgument(direction != null, "Vector direction cannot be null");
        direction.checkFinite();

        Preconditions.checkArgument(direction.lengthSquared() > 0, "Direction's magnitude (%s) need to be greater than 0", direction.lengthSquared());

        if (maxDistance < 0.0D) {
            return Collections.emptyList();
        }

        List<EntityRayTraceResult> list = new ArrayList<>();
        World world = start.getWorld();

        Vector startPos = start.toVector();
        Vector dirNorm = direction.clone().normalize();
        Vector ray = dirNorm.clone().multiply(maxDistance);

        BoundingBox aabb = BoundingBox.of(startPos, startPos).expandDirectional(ray).expand(raySize);
//        Collection<Entity> entities = world.getNearbyEntities(aabb);
        Collection<Entity> entities = world.getNearbyEntities(aabb, filter);

        for (Entity entity : entities) {
            BoundingBox boundingBox = entity.getBoundingBox().expand(raySize);
            RayTraceResult hitResult = boundingBox.rayTrace(startPos, dirNorm, maxDistance);

            if (hitResult != null) {
                list.add(new EntityRayTraceResult(
                        entity, hitResult, startPos.distanceSquared(hitResult.getHitPosition())
                ));
            }
        }

        if (sortByDistance) {
            list.sort(Comparator.comparingDouble(EntityRayTraceResult::getDistanceSquared));
        }

        return list;
    }

    // source partially from (and modified from) Abs01uteMagicBullet -> renderLaserBeam()
    // not very shit i think
    private void renderSonicBoom(Location from, Location to) {
        Vector step = to.clone().subtract(from).toVector();
        int stepCount = (int) Math.floor(step.length());
        step.normalize();

        Location particleLoc = from.clone();

        for (int i = 0; i < stepCount; i++) {
            particleLoc.add(step);
            particleLoc.getWorld().spawnParticle(
                    Particle.SONIC_BOOM, particleLoc, 1,
                    0, 0, 0,
                    0, null, true
            );
        }
    }
}
