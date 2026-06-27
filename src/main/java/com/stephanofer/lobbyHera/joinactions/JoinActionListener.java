package com.stephanofer.lobbyHera.joinactions;

import com.stephanofer.networkplayersettings.settings.event.PlayerSettingsReadyEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class JoinActionListener implements Listener {

    private final JoinActionService joinActionService;

    public JoinActionListener(JoinActionService joinActionService) {
        this.joinActionService = joinActionService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSettingsReady(PlayerSettingsReadyEvent event) {
        this.joinActionService.run(event.player());
    }
}
