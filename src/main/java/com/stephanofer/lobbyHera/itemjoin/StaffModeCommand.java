package com.stephanofer.lobbyHera.itemjoin;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class StaffModeCommand implements BasicCommand {

    private final ItemJoinService itemJoinService;

    public StaffModeCommand(ItemJoinService itemJoinService) {
        this.itemJoinService = itemJoinService;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendRichMessage("<red>Uso en consola: /staffmode <jugador>");
                return;
            }

            boolean enabled = this.itemJoinService.toggleStaffBypass(player.getUniqueId());
            sender.sendRichMessage(enabled
                    ? "<green>Staff mode activado. Ahora podés dropear/mover items gestionados."
                    : "<yellow>Staff mode desactivado. Vuelven las restricciones de ItemJoin.");
            return;
        }

        if (args.length > 1) {
            sender.sendRichMessage("<red>Uso: /staffmode [jugador]");
            return;
        }

        if (!sender.isOp() && !sender.hasPermission("lobbyhera.staffmode.others")) {
            sender.sendRichMessage("<red>No tenés permiso para modificar a otro jugador.");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendRichMessage("<red>Jugador no encontrado o desconectado.");
            return;
        }

        boolean enabled = this.itemJoinService.toggleStaffBypass(target.getUniqueId());
        sender.sendRichMessage(enabled
                ? "<green>Staff mode activado para <white>" + target.getName() + "<green>."
                : "<yellow>Staff mode desactivado para <white>" + target.getName() + "<yellow>.");

        if (!sender.equals(target)) {
            target.sendRichMessage(enabled
                    ? "<green>Se activó tu staff mode (bypass de ItemJoin)."
                    : "<yellow>Se desactivó tu staff mode (restricciones restauradas).");
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length != 1 || (!sender.isOp() && !sender.hasPermission("lobbyhera.staffmode.others"))) {
            return List.of();
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Override
    public @Nullable String permission() {
        return "lobbyhera.staffmode";
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender.isOp() || sender.hasPermission("lobbyhera.staffmode");
    }
}
