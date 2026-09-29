package com.eltena.addon.client.skilltree;

public final class SkillTreeViewState {
    private double offsetX;
    private double offsetY;
    private double zoom;

    public SkillTreeViewState(double offsetX, double offsetY, double zoom) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.zoom = zoom;
    }

    public double offsetX() {
        return offsetX;
    }

    public double offsetY() {
        return offsetY;
    }

    public double zoom() {
        return zoom;
    }

    public void pan(double deltaX, double deltaY) {
        offsetX += deltaX;
        offsetY += deltaY;
    }

    public void adjustZoom(double amount) {
        zoom = Math.max(0.70D, Math.min(1.60D, zoom + amount));
    }

    public void set(double offsetX, double offsetY, double zoom) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.zoom = Math.max(0.70D, Math.min(1.60D, zoom));
    }
}
