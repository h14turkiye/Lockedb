package com.h14turkiye.mcschedlock.test.plugin;

import org.bukkit.plugin.java.JavaPlugin;

import com.h14turkiye.mcschedlock.lock.platform.mongodb.MongoLockConnect;

public class TestPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        MongoLockConnect.connectToMongoDB(this, "testplugin_mcschedlock", "localhost", 27017, null, null, null, false);
    }
}
