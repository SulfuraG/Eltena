package com.eltena.core.application.ui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class EltenaMenuHolder implements InventoryHolder {

    private final MenuViewType type;
    private Inventory inventory;

    public EltenaMenuHolder(MenuViewType type) {
        this.type = type;
    }

    public MenuViewType type() {
        return type;
    }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
