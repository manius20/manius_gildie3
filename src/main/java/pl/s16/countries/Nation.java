package pl.s16.countries;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;

public final class Nation {
    public final String name;
    public UUID leader;
    public final Map<UUID, String> members = new LinkedHashMap<>();
    public final Set<ClaimKey> claims = new LinkedHashSet<>();
    public final Set<String> allies = new LinkedHashSet<>();
    public boolean friendlyFire;
    public Location home;
    public long lastWarEnded;
    public int kills;

    public Nation(String name, UUID leader, String leaderRole, boolean friendlyFire) {
        this.name = name;
        this.leader = leader;
        this.members.put(leader, leaderRole);
        this.friendlyFire = friendlyFire;
    }

    public boolean contains(Player player) { return members.containsKey(player.getUniqueId()); }
    public boolean hasPermission(UUID player, String permission, ConfigFiles config) {
        if (player.equals(leader)) return true;
        String role = members.get(player);
        if (role == null) return false;
        List<String> powers = config.roles().getStringList("roles." + role + ".permissions");
        return powers.stream().anyMatch(p -> p.equalsIgnoreCase("ALL") || p.equalsIgnoreCase(permission));
    }

    public boolean touches(ClaimKey key) {
        return claims.contains(key.offset(1, 0)) || claims.contains(key.offset(-1, 0))
                || claims.contains(key.offset(0, 1)) || claims.contains(key.offset(0, -1));
    }

    public boolean remainsConnectedWithout(ClaimKey removed) {
        Set<ClaimKey> remaining = new HashSet<>(claims);
        remaining.remove(removed);
        if (remaining.size() < 2) return true;
        Set<ClaimKey> seen = new HashSet<>();
        Deque<ClaimKey> queue = new ArrayDeque<>();
        ClaimKey first = remaining.iterator().next();
        queue.add(first);
        seen.add(first);
        while (!queue.isEmpty()) {
            ClaimKey current = queue.removeFirst();
            for (ClaimKey neighbor : List.of(current.offset(1,0),current.offset(-1,0),current.offset(0,1),current.offset(0,-1))) {
                if (remaining.contains(neighbor) && seen.add(neighbor)) queue.addLast(neighbor);
            }
        }
        return seen.size() == remaining.size();
    }
}
