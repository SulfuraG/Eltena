package com.eltena.effectcore.trigger;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.util.BoundingBox;

public record AreaTriggerDefinition(
    String id,
    boolean enabled,
    String displayName,
    AreaTriggerType triggerType,
    String worldName,
    BoundingBox box,
    String permissionBypass,
    long cooldownMs,
    List<AreaTriggerAction> actions
) {
    public boolean matches(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!worldName.equals(location.getWorld().getName())) {
            return false;
        }
        return box.contains(location.getX(), location.getY(), location.getZ());
    }
}
