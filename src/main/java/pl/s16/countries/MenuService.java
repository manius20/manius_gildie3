package pl.s16.countries;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class MenuService implements Listener {
    private final S16Countries plugin;
    public MenuService(S16Countries plugin) { this.plugin=plugin; }
    private static final class MenuHolder implements InventoryHolder {
        final String kind;
        final Inventory inventory;
        MenuHolder(String kind, int size, String title) {
            this.kind=kind;
            inventory=Bukkit.createInventory(this, size, title);
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    private ItemStack icon(Material type, String name, List<String> lore) {
        ItemStack stack=new ItemStack(type);
        ItemMeta meta=stack.getItemMeta();
        meta.setDisplayName(plugin.config().text(name));
        meta.setLore(lore.stream().map(plugin.config()::text).toList());
        stack.setItemMeta(meta);
        return stack;
    }
    private Material material(String text, Material fallback) {
        Material m=Material.matchMaterial(text == null ? "" : text);
        return m == null || m.isAir() ? fallback : m;
    }
    private int validSize(int candidate) {
        return candidate>=9 && candidate<=54 && candidate%9==0 ? candidate : 27;
    }
    public void guild(Player player) {
        ConfigurationSection section=plugin.config().gui().getConfigurationSection("guild-menu");
        if(section==null) return;
        int size=validSize(section.getInt("size",27));
        MenuHolder holder = new MenuHolder("guild",size,plugin.config().text(section.getString("title","&6Gildia")));
        ItemStack filler=icon(material(section.getString("fill-material"),Material.GRAY_STAINED_GLASS_PANE)," ",List.of());
        for(int slot=0;slot<size;slot++) holder.inventory.setItem(slot,filler);
        ConfigurationSection buttons=section.getConfigurationSection("buttons");
        if(buttons!=null) for(String key:buttons.getKeys(false)) {
            ConfigurationSection b=buttons.getConfigurationSection(key);
            if(b==null) continue;
            int slot=b.getInt("slot",-1);
            if(slot<0||slot>=size) continue;
            holder.inventory.setItem(slot,icon(material(b.getString("material"),Material.PAPER), b.getString("name",key),b.getStringList("lore")));
        }
        player.openInventory(holder.inventory);
    }
    public void currency(Player player) {
        ConfigurationSection section=plugin.config().gui().getConfigurationSection("currency-menu");
        if(section==null) return;
        int size=validSize(section.getInt("size",27));
        MenuHolder holder=new MenuHolder("currency",size,plugin.config().text(section.getString("title","&eWaluta")));
        int help=section.getInt("help-slot",4),save=section.getInt("save-slot",22),preview=section.getInt("preview-slot",13);
        if(help>=0&&help<size) holder.inventory.setItem(help, icon(material(section.getString("help-material"),Material.PAPER),section.getString("help-name","&ePomoc"), section.getStringList("help-lore")));
        if(save>=0&&save<size) holder.inventory.setItem(save,icon(material(section.getString("save-material"),Material.EMERALD_BLOCK),section.getString("save-name","&aZapisz"),List.of()));
        if(preview>=0&&preview<size) holder.inventory.setItem(preview,plugin.currency().template());
        player.openInventory(holder.inventory);
    }
    @EventHandler public void inventoryClick(InventoryClickEvent e) {
        if(!(e.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        if(!(e.getWhoClicked() instanceof Player player)) return;
        int raw=e.getRawSlot();
        if(raw<0) return;
        if(raw >= e.getView().getTopInventory().getSize()) {
            if(e.isShiftClick() || e.getAction()==InventoryAction.COLLECT_TO_CURSOR) e.setCancelled(true);
            return;
        }
        // WSZYSTKIE kliknięcia w górne GUI anulowane; żadnych przeniesień i duplikacji.
        e.setCancelled(true);
        if(holder.kind.equals("currency")) {
            int preview=plugin.config().gui().getInt("currency-menu.preview-slot",13);
            int save=plugin.config().gui().getInt("currency-menu.save-slot",22);
            if(raw==preview) {
                ItemStack cursor=e.getCursor();
                if(cursor==null || cursor.getType().isAir()) holder.inventory.setItem(preview,null);
                else { ItemStack copy=cursor.clone();copy.setAmount(1);holder.inventory.setItem(preview,copy); }
                return;
            }
            if(raw==save) {
                ItemStack chosen=holder.inventory.getItem(preview);
                if(chosen==null || chosen.getType().isAir()) { plugin.msg(player,"§cNajpierw wybierz przedmiot w slocie waluty."); return; }
                plugin.currency().set(chosen);
                plugin.msg(player,"§aWaluta ustawiona: §e"+chosen.getType()+"§a (uwzględnia nazwę i inne metadata).");
                player.closeInventory();
            }
            return;
        }
        if(holder.kind.equals("guild")) {
            ConfigurationSection buttons=plugin.config().gui().getConfigurationSection("guild-menu.buttons");
            if(buttons==null) return;
            for(String action:buttons.getKeys(false)) if(buttons.getInt(action+".slot",-1)==raw) {
                player.closeInventory();
                String sub=action.equals("wars")?"wojny":action.equals("home")?"dom":action;
                String configured=buttons.getString(action+".command", "gildia "+sub);
                String command=configured.startsWith("/")?configured.substring(1):configured;
                Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(command));
                return;
            }
        }
    }
    @EventHandler public void drag(InventoryDragEvent e) {
        if(!(e.getView().getTopInventory().getHolder() instanceof MenuHolder)) return;
        int size=e.getView().getTopInventory().getSize();
        if(e.getRawSlots().stream().anyMatch(slot -> slot<size)) e.setCancelled(true);
    }
}
