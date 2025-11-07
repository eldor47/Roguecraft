package com.eldor.roguecraft.models;

import org.bukkit.Location;
import org.bukkit.World;

public class Arena {
    private final String id;
    private final String name;
    private Location spawnPoint;
    private Location center;
    private double radius;
    private World world;
    private boolean isActive;

    public Arena(String id, String name) {
        this.id = id;
        this.name = name;
        this.isActive = false;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Location getSpawnPoint() {
        return spawnPoint;
    }

    public void setSpawnPoint(Location spawnPoint) {
        this.spawnPoint = spawnPoint;
        this.world = spawnPoint.getWorld();
    }

    public Location getCenter() {
        return center;
    }

    public void setCenter(Location center) {
        this.center = center;
    }

    public double getRadius() {
        return radius;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }

    public World getWorld() {
        return world;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public boolean isInArena(Location location) {
        if (center == null || location.getWorld() != world) {
            return false;
        }
        return center.distance(location) <= radius;
    }
}



