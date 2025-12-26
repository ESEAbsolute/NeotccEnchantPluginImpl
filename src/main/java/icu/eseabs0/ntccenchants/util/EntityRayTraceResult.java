package icu.eseabs0.ntccenchants.util;

import org.bukkit.entity.Entity;
import org.bukkit.util.RayTraceResult;

public class EntityRayTraceResult {
    private final Entity entity;
    private final RayTraceResult rayTraceResult;
    private final double distanceSquared;

    public EntityRayTraceResult(Entity entity, RayTraceResult result, double distanceSquared) {
        this.entity = entity;
        this.rayTraceResult = result;
        this.distanceSquared = distanceSquared;
    }

    public Entity getEntity() {
        return entity;
    }

    public RayTraceResult getRayTraceResult() {
        return rayTraceResult;
    }

    public double getDistanceSquared() {
        return distanceSquared;
    }

    public double getDistance() {
        return Math.sqrt(distanceSquared);
    }
}
