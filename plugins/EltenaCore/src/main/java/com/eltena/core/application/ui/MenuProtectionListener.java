package com.eltena.core.application.ui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

public final class MenuProtectionListener implements Listener {

    private final MenuSystem menuSystem;

    public MenuProtectionListener(MenuSystem menuSystem) {
        this.menuSystem = menuSystem;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!menuSystem.isProtectedView(event.getView())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!menuSystem.isProtectedView(event.getView())) {
            return;
        }
        event.setCancelled(true);
    }
}
