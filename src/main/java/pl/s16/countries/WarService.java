package pl.s16.countries;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class WarService implements Listener {
    private final S16Countries plugin;
    private final Map<UUID, Capture> captures = new HashMap<>();
    public WarService(S16Countries plugin) { this.plugin = plugin; }

    public War between(Nation a, Nation b) {
        if (a==null || b==null || a==b) return null;
        return plugin.store().wars.stream().filter(w -> w.between(a,b) && !w.expired()).findFirst().orElse(null);
    }
    public boolean activeBetween(Nation a, Nation b) { War war=between(a,b); return war!=null && war.active(); }
    public int involving(Nation n) {
        int count=0;
        for (War w:plugin.store().wars) if(w.involves(n)&&!w.expired()) count++;
        return count;
    }
    public void declare(Nation a, Nation b, long prepSeconds, long durationSeconds) {
        long start = System.currentTimeMillis() + prepSeconds*1000L;
        long end = start + durationSeconds*1000L;
        plugin.store().wars.add(new War(a,b,start,end,false,false));
        plugin.store().save();
    }
    public void stop(War war) {
        plugin.store().wars.remove(war);
        long now=System.currentTimeMillis();
        war.a.lastWarEnded=now;
        war.b.lastWarEnded=now;
        plugin.store().save();
        plugin.broadcast("war-end", "a",war.a.name,"b",war.b.name);
        captures.values().stream().filter(c -> c.target==war.a || c.target==war.b).map(c -> c.player).toList()
                .forEach(id -> cancel(id,"wojna się skończyła"));
    }
    public void tickWars() {
        for (War war: new ArrayList<>(plugin.store().wars)) {
            if (war.expired()) { stop(war); continue; }
            if (!war.active()) continue;
            // Jedna wiadomość w momencie rozpoczęcia wojny (równanie sekund przy ticku).
            if (System.currentTimeMillis() - war.start < 1000) plugin.broadcast("war-start", "a",war.a.name,"b",war.b.name);
        }
    }
    public void startCapture(Player player) {
        Nation attacker=plugin.store().of(player.getUniqueId());
        ClaimKey key=ClaimKey.of(player.getLocation());
        Nation defender=plugin.store().at(key);
        if (attacker==null) { plugin.msg(player,plugin.config().message("not-in-guild")); return; }
        if (!attacker.hasPermission(player.getUniqueId(), "CAPTURE", plugin.config())) { plugin.msg(player,plugin.config().message("no-permission")); return; }
        if (defender==null || defender==attacker || !activeBetween(attacker, defender)) {
            plugin.msg(player,plugin.config().message("war-not-active")); return;
        }
        if (attacker.claims.size() >= plugin.config().integer("claims.max-per-guild")) { plugin.msg(player,plugin.config().message("claim-limit")); return; }
        if (plugin.config().bool("war.capture-requires-adjacency") && !attacker.touches(key)) {
            plugin.msg(player,plugin.config().message("claim-must-touch")); return;
        }
        if (!defender.remainsConnectedWithout(key)) { plugin.msg(player,plugin.config().message("claim-disconnect")); return; }
        if (!plugin.config().bool("war.allow-capital-capture") && defender.home != null && key.equals(ClaimKey.of(defender.home))) {
            plugin.msg(player,plugin.config().message("capture-cancel", "reason", "chroniona stolica")); return;
        }
        if (plugin.config().bool("war.conquest-require-online-defender") && defender.members.keySet().stream().noneMatch(id -> Bukkit.getPlayer(id)!=null)) {
            plugin.msg(player,plugin.config().message("capture-cancel", "reason", "brak obrońców online")); return;
        }
        int cost=Math.max(0,plugin.config().integer("currency.capture-cost"));
        if (!canAffordMessage(player,cost)) return;
        if (captures.containsKey(player.getUniqueId())) { plugin.msg(player,"§cJuż prowadzisz jeden podbój."); return; }
        int seconds=Math.max(1,plugin.config().integer("war.capture-seconds"));
        Capture capture = new Capture(player.getUniqueId(), attacker, defender, key, player.getLocation(), seconds);
        captures.put(player.getUniqueId(), capture);
        plugin.msg(player,plugin.config().message("capture-start", "seconds", seconds));
        capture.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> captureTick(capture), 20L, 20L);
    }
    private boolean canAffordMessage(Player player, int cost) {
        if(cost<=0) return true;
        if (!plugin.currency().isSet()) { plugin.msg(player,plugin.config().message("missing-currency")); return false; }
        int have=plugin.currency().count(player);
        if (have<cost) { plugin.msg(player,plugin.config().message("not-enough-currency", "cost",cost,"have",have)); return false; }
        return true;
    }
    private void captureTick(Capture c) {
        Player player=Bukkit.getPlayer(c.player);
        if (player==null || player.isDead() || !player.isOnline()) { cancel(c.player,"gracz offline"); return; }
        if (plugin.store().of(c.player)!=c.attacker || plugin.store().at(c.key)!=c.target || !activeBetween(c.attacker,c.target)) {
            cancel(c.player,"zmieniła się sytuacja wojny"); return;
        }
        if (!c.key.equals(ClaimKey.of(player.getLocation())) || player.getLocation().distanceSquared(c.start)>Math.pow(plugin.config().integer("war.capture-max-distance-from-start"),2)) {
            cancel(c.player,"opuściłeś teren podboju"); return;
        }
        if (c.attacker.claims.size() >= plugin.config().integer("claims.max-per-guild") ||
                (plugin.config().bool("war.capture-requires-adjacency") && !c.attacker.touches(c.key)) ||
                !c.target.remainsConnectedWithout(c.key)) {
            cancel(c.player,"nieprawidłowe granice lub limit"); return;
        }
        int radius=Math.max(0,plugin.config().integer("war.capture-defender-radius"));
        boolean contested=player.getWorld().getPlayers().stream().anyMatch(other ->
                plugin.store().of(other.getUniqueId()) == c.target && other.getLocation().distanceSquared(player.getLocation())<=radius*radius);
        if (contested) {
            if (!c.contested) plugin.msg(player,plugin.config().message("capture-contested"));
            c.contested=true;
            return;
        }
        c.contested=false;
        c.remaining--;
        if(c.remaining>0) {
            if(c.remaining%10==0 || c.remaining<=5) player.sendActionBar(net.kyori.adventure.text.Component.text("Podbój: " + c.remaining + " s"));
            return;
        }
        int cost=Math.max(0,plugin.config().integer("currency.capture-cost"));
        if (!canAffordMessage(player,cost)) { cancel(c.player,"brak przedmiotów na koniec podboju"); return; }
        if (!plugin.currency().pay(player,cost)) { cancel(c.player,"nie udało się pobrać waluty"); return; }
        plugin.store().claim(c.attacker,c.key);
        if (c.target.home != null && c.key.equals(ClaimKey.of(c.target.home))) c.target.home=null;
        plugin.store().save();
        plugin.msg(player,plugin.config().message("capture-success", "guild",c.target.name));
        if(c.task!=null) c.task.cancel();
        captures.remove(c.player);
    }
    public void cancel(UUID player, String why) {
        Capture c=captures.remove(player);
        if(c==null) return;
        if(c.task!=null) c.task.cancel();
        Player p=Bukkit.getPlayer(player);
        if(p!=null) plugin.msg(p,plugin.config().message("capture-cancel", "reason",why));
    }
    @EventHandler public void quit(PlayerQuitEvent e) { cancel(e.getPlayer().getUniqueId(), "wylogowano"); }
    private static final class Capture {
        final UUID player;
        final Nation attacker, target;
        final ClaimKey key;
        final Location start;
        int remaining;
        boolean contested;
        BukkitTask task;
        Capture(UUID player, Nation attacker, Nation target, ClaimKey key, Location start, int time) {
            this.player=player; this.attacker=attacker;this.target=target;this.key=key;this.start=start.clone();this.remaining=time;
        }
    }
}
