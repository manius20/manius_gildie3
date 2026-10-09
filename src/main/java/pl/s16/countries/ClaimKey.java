package pl.s16.countries;

import org.bukkit.Chunk;
import org.bukkit.Location;

import java.util.UUID;

public record ClaimKey(UUID world, int x, int z) {
    public static ClaimKey of(Chunk chunk) { return new ClaimKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ()); }
    public static ClaimKey of(Location location) { return new ClaimKey(location.getWorld().getUID(), location.getBlockX() >> 4, location.getBlockZ() >> 4); }
    public String serialize() { return world + ":" + x + ":" + z; }
    public ClaimKey offset(int dx, int dz) { return new ClaimKey(world, x + dx, z + dz); }
    public static ClaimKey parse(String data) {
        String[] parts = data.split(":", 3);
        if (parts.length != 3) throw new IllegalArgumentException("Błędny klucz chunka: " + data);
        return new ClaimKey(UUID.fromString(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }
}
