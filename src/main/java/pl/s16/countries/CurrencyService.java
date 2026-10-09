package pl.s16.countries;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;

/** Stan waluty: dokładny wzorzec ItemStack (typ i metadata), bez Vault i bez kont. */
public final class CurrencyService {
    private final S16Countries plugin;
    private final File file;
    private ItemStack template;
    public CurrencyService(S16Countries plugin) {
        this.plugin=plugin;
        this.file=new File(plugin.getDataFolder(), "currency.yml");
        reload();
    }
    public void reload() {
        if (!file.exists()) { template=null; return; }
        ItemStack stack = YamlConfiguration.loadConfiguration(file).getItemStack("item");
        template = (stack == null || stack.getType().isAir()) ? null : stack.clone();
        if (template != null) template.setAmount(1);
    }
    public ItemStack template() { return template == null ? null : template.clone(); }
    public boolean isSet() { return template != null; }
    public void set(ItemStack item) {
        template = item == null || item.getType().isAir() ? null : item.clone();
        if (template != null) template.setAmount(1);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("item", template);
        try { yaml.save(file); } catch (IOException e) { plugin.getLogger().severe("Nie zapisano waluty: " + e.getMessage()); }
    }
    public int count(Player player) {
        if (template == null) return 0;
        int amount = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.isSimilar(template)) amount += item.getAmount();
        }
        return amount;
    }
    public boolean canPay(Player player, int amount) { return amount <= 0 || (template != null && count(player) >= amount); }
    public boolean pay(Player player, int amount) {
        if (amount <= 0) return true;
        if (!canPay(player, amount)) return false;
        int remaining = amount;
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int i=0; i<storage.length && remaining>0; i++) {
            ItemStack stack=storage[i];
            if (stack==null || !stack.isSimilar(template)) continue;
            int take=Math.min(remaining, stack.getAmount());
            if (stack.getAmount()==take) storage[i]=null;
            else { ItemStack copy=stack.clone(); copy.setAmount(stack.getAmount()-take); storage[i]=copy; }
            remaining-=take;
        }
        player.getInventory().setStorageContents(storage);
        return true;
    }
    public void refund(Player player, int amount) {
        if (amount <= 0 || template == null) return;
        int left=amount;
        while (left > 0) {
            ItemStack copy=template.clone();
            int take=Math.min(left, copy.getMaxStackSize());
            copy.setAmount(take);
            player.getInventory().addItem(copy).values().forEach(stack -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
            left-=take;
        }
    }
}
