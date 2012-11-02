package me.asofold.bpl.testncp;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.checks.access.IViolationInfo;
import fr.neatmonster.nocheatplus.hooks.APIUtils;
import fr.neatmonster.nocheatplus.hooks.IFirst;
import fr.neatmonster.nocheatplus.hooks.IStats;
import fr.neatmonster.nocheatplus.hooks.NCPHook;
import fr.neatmonster.nocheatplus.hooks.NCPHookManager;

public class TestNCP extends JavaPlugin implements NCPHook, IStats, IFirst{
    
    protected final Set<String> testers = new HashSet<String>();
    
    protected boolean toConsole = true;
    
    protected boolean details = true;
    
    protected boolean testAll = false;
    
    protected final Set<ParameterName> detailsUsed = new LinkedHashSet<ParameterName>();
    
    protected final DecimalFormat format = new DecimalFormat("#.###");
    
    public TestNCP(){
        for (final ParameterName param : ParameterName.values()){
            if (param == ParameterName.IP || param == ParameterName.PLAYER || param == ParameterName.VIOLATIONS || param == ParameterName.CHECK) continue;
            else detailsUsed.add(param);
        }
        DecimalFormatSymbols sym = format.getDecimalFormatSymbols();
        sym.setDecimalSeparator('.');
        format.setMaximumFractionDigits(3);
        format.setDecimalSeparatorAlwaysShown(false);
        format.setMinimumIntegerDigits(1);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
            String label, String[] args) {
        String cmd = "";
        if (args.length > 0) cmd = args[0].trim().toLowerCase();
        if (cmd.equals("reload") && sender.hasPermission("testncp.admin.reload")){
            reloadSettings();
            sender.sendMessage("[TestNCP] Settings reloaded.");
            return true;
        }
        return false;
    }

    @Override
    public void onDisable() {
        NCPHookManager.removeHook(this);
        System.out.println(getDescription().getFullName() + " is disabled.");
    }

    @Override
    public void onEnable() {
        reloadSettings();
        NCPHookManager.addHook(CheckType.ALL, this);
        System.out.println(getDescription().getFullName() + " is enabled.");
    }
    
    public void reloadSettings() {
        this.testers.clear();
        MemoryConfiguration defaults = new MemoryConfiguration();
        defaults.set("testers", new LinkedList<String>());
        defaults.set("logging.console", true);
        defaults.set("logging.details", true);
        reloadConfig();
        Configuration config = getConfig();
        config.setDefaults(defaults);
        config.options().copyDefaults(true);
        saveConfig();
        toConsole = config.getBoolean("logging.console");
        details = config.getBoolean("logging.details");
        List<String> testers = config.getStringList("testers");
        for (String n : testers){
            this.testers.add(n.trim().toLowerCase());
        }
        testAll = this.testers.contains("*");
    }

    @Override
    public String getHookName() {
        return "TestNCPHook";
    }

    @Override
    public String getHookVersion() {
        return "1.0";
    }

    @Override
    public boolean onCheckFailure(final CheckType checkType, final Player player, final IViolationInfo info) {
        final String name = player.getName().toLowerCase();
        if (!testAll && !testers.contains(name)) return false;
        if (APIUtils.needsSynchronization(checkType)){
            Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
                @Override
                public void run() {
                    log(checkType, player, info);
                }
            });
        }
        else log(checkType, player, info);
        return false;
    }
    
    public void log(final CheckType checkType, final Player player, final IViolationInfo info) {
        String msg = ChatColor.YELLOW + "[TestNCP] " + ChatColor.WHITE + player.getName() + " " + ChatColor.AQUA + checkType.name() + ChatColor.WHITE + " vl " + format.format(info.getTotalVl()) + " (+" + format.format(info.getAddedVl()) + ")";
        if (details && info.needsParameters()){
            final StringBuilder builder = new StringBuilder(200);
            builder.append(msg);
            builder.append(ChatColor.GRAY + " Details:");
            final ViolationData data = (ViolationData) info;
            // TODO: Add a way to get the parameter map to NCP ?
            for (final ParameterName param : detailsUsed){
                final String paramName = param.name();
                final String val = data.getParameter(param);
                if (val == null || val.equals("<?" + paramName + ">")) continue;
                else builder.append(" " + param + "=" + val);
            }
            builder.append(" cancel=" + info.hasCancel());
            msg =  builder.toString();
        }
        if (toConsole) Bukkit.getLogger().info(ChatColor.stripColor(msg));
        for (final Player ref : Bukkit.getOnlinePlayers()){
            if (testAll || testers.contains(ref.getName().toLowerCase())) ref.sendMessage(msg);
        }
    }
    
}
