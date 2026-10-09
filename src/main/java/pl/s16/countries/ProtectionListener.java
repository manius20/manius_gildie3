package pl.s16.countries;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.inventory.Inventory;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.projectiles.ProjectileSource;

public final class ProtectionListener implements Listener {
    private final S16Countries plugin;
    private final Map<UUID, Long> lastWarn = new HashMap<>();
    public ProtectionListener(S16Countries plugin) { this.plugin=plugin; }
    private Nation nation(Location loc) {return loc==null || loc.getWorld()==null ? null : plugin.store().at(loc);}
    private boolean canBuild(Player player,Location loc) {
        Nation claimed=nation(loc);
        return claimed==null || (player!=null && (player.hasPermission("s16countries.bypass") || claimed.contains(player)));
    }
    private void deny(Player p, Location loc) {
        Nation owner=nation(loc);
        if(owner!=null) {
            long now=System.currentTimeMillis();
            if(now-lastWarn.getOrDefault(p.getUniqueId(),0L)>1500) {
                lastWarn.put(p.getUniqueId(),now);
                plugin.msg(p,plugin.config().message("protected","guild",owner.name));
            }
        }
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH)
    public void blockBreak(BlockBreakEvent e) {
        if(plugin.config().bool("claims.protect-block-break")&&!canBuild(e.getPlayer(),e.getBlock().getLocation())) {e.setCancelled(true);deny(e.getPlayer(),e.getBlock().getLocation());}
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH)
    public void blockPlace(BlockPlaceEvent e) {
        if(plugin.config().bool("claims.protect-block-place")&&!canBuild(e.getPlayer(),e.getBlockPlaced().getLocation())) {e.setCancelled(true);deny(e.getPlayer(),e.getBlockPlaced().getLocation());}
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH)
    public void interact(PlayerInteractEvent e) {
        if(!plugin.config().bool("claims.protect-interactions") || e.getClickedBlock()==null) return;
        Block block=e.getClickedBlock();
        if(e.getAction()==Action.RIGHT_CLICK_BLOCK && block.getType().isInteractable() && !canBuild(e.getPlayer(),block.getLocation())) {
            e.setCancelled(true);deny(e.getPlayer(),block.getLocation());
        }
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH)
    public void inventoryOpen(InventoryOpenEvent e) {
        if(!plugin.config().bool("claims.protect-containers") || !(e.getPlayer() instanceof Player p))return;
        Location loc=e.getInventory().getLocation();
        if(loc!=null && !canBuild(p,loc)) {e.setCancelled(true);deny(p,loc);}
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH)
    public void bucketEmpty(PlayerBucketEmptyEvent e) {
        Block target=e.getBlockClicked().getRelative(e.getBlockFace());
        if(!canBuild(e.getPlayer(),target.getLocation())) {e.setCancelled(true);deny(e.getPlayer(),target.getLocation());}
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH)
    public void bucketFill(PlayerBucketFillEvent e) {
        if(!canBuild(e.getPlayer(),e.getBlockClicked().getLocation())) {e.setCancelled(true);deny(e.getPlayer(),e.getBlockClicked().getLocation());}
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH)
    public void entityInteract(PlayerInteractEntityEvent e) {
        if(plugin.config().bool("claims.protect-entities") && !canBuild(e.getPlayer(),e.getRightClicked().getLocation())) {
            e.setCancelled(true);
            deny(e.getPlayer(),e.getRightClicked().getLocation());
        }
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH)
    public void armorStand(PlayerArmorStandManipulateEvent e) {
        if(plugin.config().bool("claims.protect-entities")&&!canBuild(e.getPlayer(),e.getRightClicked().getLocation())) {e.setCancelled(true);deny(e.getPlayer(),e.getRightClicked().getLocation());}
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH)
    public void hangingPlace(HangingPlaceEvent e) {
        if(!plugin.config().bool("claims.protect-entities"))return;
        if(!canBuild(e.getPlayer(),e.getEntity().getLocation()))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH)
    public void hangingBreak(HangingBreakByEntityEvent e) {
        if(!plugin.config().bool("claims.protect-entities"))return;
        Entity remover=e.getRemover();
        Player player=remover instanceof Player p?p:null;
        if(!canBuild(player,e.getEntity().getLocation()))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH)
    public void damage(EntityDamageByEntityEvent e) {
        Player attacker=null;
        if(e.getDamager() instanceof Player player)attacker=player;
        else if(e.getDamager() instanceof Projectile projectile) {
            ProjectileSource shooter=projectile.getShooter();
            if(shooter instanceof Player p)attacker=p;
        }
        if(e.getEntity() instanceof Player victim && attacker!=null) {
            if(!plugin.config().bool("pvp.enabled")) {e.setCancelled(true);return;}
            Nation attackNation=plugin.store().of(attacker.getUniqueId());
            Nation victimNation=plugin.store().of(victim.getUniqueId());
            if(attackNation!=null&&attackNation==victimNation&&!attackNation.friendlyFire) {
                e.setCancelled(true);
                // Nie spamujemy wiadomości co każde uderzenie.
            }
            return;
        }
        // Ochrona zwierząt i bytów w cudzym claimie przed atakami graczy.
        if(plugin.config().bool("claims.protect-entities")&&attacker!=null&&!canBuild(attacker,e.getEntity().getLocation())) {
            e.setCancelled(true);
        }
    }
    @EventHandler(ignoreCancelled=true,priority=EventPriority.HIGH)
    public void entityChangeBlock(EntityChangeBlockEvent e) {
        if(plugin.config().bool("claims.protect-entities")&&nation(e.getBlock().getLocation())!=null)e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true)
    public void entityExplode(EntityExplodeEvent e) {
        if(plugin.config().bool("claims.protect-explosions"))e.blockList().removeIf(block->nation(block.getLocation())!=null);
    }
    @EventHandler(ignoreCancelled=true)
    public void blockExplode(BlockExplodeEvent e) {
        if(plugin.config().bool("claims.protect-explosions"))e.blockList().removeIf(block->nation(block.getLocation())!=null);
    }
    @EventHandler(ignoreCancelled=true)
    public void pistonExtend(BlockPistonExtendEvent e) {
        if(!plugin.config().bool("claims.protect-pistons"))return;
        Nation piston=nation(e.getBlock().getLocation());
        if(piston!=nation(e.getBlock().getRelative(e.getDirection()).getLocation())) {e.setCancelled(true);return;}
        for(Block moved:e.getBlocks()) {
            Nation from=nation(moved.getLocation());
            Nation to=nation(moved.getRelative(e.getDirection()).getLocation());
            if(from!=to || piston!=from) {e.setCancelled(true);return;}
        }
    }
    @EventHandler(ignoreCancelled=true)
    public void pistonRetract(BlockPistonRetractEvent e) {
        if(!plugin.config().bool("claims.protect-pistons"))return;
        Nation piston=nation(e.getBlock().getLocation());
        for(Block moved:e.getBlocks()) {
            Nation from=nation(moved.getLocation());
            Nation to=nation(moved.getRelative(e.getDirection().getOppositeFace()).getLocation());
            if(from!=to||piston!=from) {e.setCancelled(true);return;}
        }
    }
    @EventHandler(ignoreCancelled=true)
    public void fluid(BlockFromToEvent e) {
        if(plugin.config().bool("claims.protect-fluids")&&nation(e.getBlock().getLocation())!=nation(e.getToBlock().getLocation()))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true)
    public void ignite(BlockIgniteEvent e) {
        if(!plugin.config().bool("claims.protect-fire"))return;
        Location loc=e.getBlock().getLocation();
        if(nation(loc)==null)return;
        if(e.getPlayer()==null || !canBuild(e.getPlayer(),loc))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true)
    public void fireSpread(BlockSpreadEvent e) {
        if(plugin.config().bool("claims.protect-fire")&&nation(e.getBlock().getLocation())!=null)e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true)
    public void hopper(InventoryMoveItemEvent e) {
        if(!plugin.config().bool("claims.protect-hoppers"))return;
        Location from=e.getSource().getLocation(),to=e.getDestination().getLocation();
        if(from==null||to==null)return;
        Nation a=nation(from),b=nation(to);
        if(a!=b && (a!=null||b!=null))e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true)
    public void move(PlayerMoveEvent e) {
        if(!plugin.config().bool("claims.show-enter-title") || e.getTo()==null)return;
        ClaimKey before=ClaimKey.of(e.getFrom()),after=ClaimKey.of(e.getTo());
        if(before.equals(after))return;
        Nation a=plugin.store().at(before),b=plugin.store().at(after);
        if(a==b)return;
        e.getPlayer().sendTitle(b==null?"§7Dzicz":"§6"+b.name,b==null?"§7Teren wolny":"§7Terytorium państwa",5,30,10);
    }
}
