package com.aitchn.validation.botania.logic;

import java.util.Optional;

/** Mana spreader: collects mana and shoots it in a fixed direction as a burst. */
public final class Spreader implements ManaCollector {

    public static final int CAPACITY = 1000;
    public static final int BURST_MANA = 160;
    public static final int COOLDOWN_TICKS = 20;
    public static final double RANGE = 16.0;

    private final Pos pos;
    private int mana;
    private float yaw;
    private float pitch;
    private int cooldown;

    public Spreader(Pos pos, int mana, float yaw, float pitch) {
        this.pos = pos;
        this.mana = Math.clamp(mana, 0, CAPACITY);
        this.yaw = yaw;
        this.pitch = pitch;
    }

    @Override
    public Pos pos() {
        return pos;
    }

    @Override
    public synchronized int mana() {
        return mana;
    }

    @Override
    public int capacity() {
        return CAPACITY;
    }

    @Override
    public synchronized int receive(int amount) {
        int accepted = Math.max(0, Math.min(amount, CAPACITY - mana));
        mana += accepted;
        return accepted;
    }

    public synchronized void aim(float newYaw, float newPitch) {
        yaw = newYaw;
        pitch = newPitch;
    }

    public synchronized float yaw() {
        return yaw;
    }

    public synchronized float pitch() {
        return pitch;
    }

    /** Fires if there is enough mana and the cooldown is over; the caller advances the returned burst. */
    public synchronized Optional<Burst> fire() {
        if (cooldown > 0 || mana < BURST_MANA) {
            return Optional.empty();
        }
        mana -= BURST_MANA;
        cooldown = COOLDOWN_TICKS;
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double dx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double dy = -Math.sin(pitchRad);
        double dz = Math.cos(yawRad) * Math.cos(pitchRad);
        return Optional.of(new Burst(pos, dx, dy, dz, BURST_MANA, RANGE));
    }

    @Override
    public synchronized void tick(WorldView world, ManaNetwork network) {
        if (cooldown > 0) {
            cooldown--;
        }
    }

    @Override
    public synchronized String serialize() {
        return "spreader;" + mana + ";" + yaw + ";" + pitch;
    }

    static Spreader fromParts(Pos pos, String[] parts) {
        return new Spreader(pos, Integer.parseInt(parts[1]), Float.parseFloat(parts[2]), Float.parseFloat(parts[3]));
    }
}
