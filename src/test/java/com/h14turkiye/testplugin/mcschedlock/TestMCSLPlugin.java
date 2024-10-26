package com.h14turkiye.testplugin.mcschedlock;

import org.bukkit.plugin.java.JavaPlugin;

import com.h14turkiye.mcschedlock.lock.LockConnect;
import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.impl.PlatformScheduler;

public class TestMCSLPlugin extends JavaPlugin {
    
    private PlatformScheduler scheduler;

    @Override
    public void onEnable() {
        LockConnect.connectToMongoDB(this, "testplugin_mcschedlock", "localhost", 27017, null, null, null, false);
        scheduler = new FoliaLib(this).getScheduler();
        this.getCommand("initiateTestMCSL").setExecutor(new TestMCSLCommand(this));
    }
    
    public PlatformScheduler getScheduler() {
        return scheduler;
    }
}
