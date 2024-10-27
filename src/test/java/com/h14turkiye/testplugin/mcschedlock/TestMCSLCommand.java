package com.h14turkiye.testplugin.mcschedlock;

import java.util.concurrent.TimeUnit;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import com.h14turkiye.mcschedlock.lock.AsyncLock;
import com.tcoded.folialib.impl.PlatformScheduler;

@SuppressWarnings("deprecation")
public class TestMCSLCommand implements CommandExecutor {
    
    private TestMCSLPlugin plugin;
    private PlatformScheduler scheduler;
    
    public TestMCSLCommand(TestMCSLPlugin plugin) {
        this.plugin = plugin;
        scheduler = plugin.getScheduler();
    }
    
    long start1;
    public void lockedTask() {
        plugin.getLogger().info("Locked task bypassToken same start IT IS EXPECTED TO ACQUIRE INSTANTLY SINCE bypassToken IS SAME");
        if (true) {
            AsyncLock lock = AsyncLock.of("lockedTask", "wow");
            
            
            lock.onLockAcquiredAsync(() -> {
                plugin.getLogger().info("First Lock acquired BT");
                start1 = System.currentTimeMillis();
            }).releaseAfterTask(false).acquire();
            
            // WILL BE ACQUIRED SINCE BYPASSTOKEN IS CORRECT
            scheduler.runLater((task) -> {
                AsyncLock lockSame = AsyncLock.of("lockedTask", "wow");
                lockSame.onLockAcquiredAsync(() -> {
                    long end = System.currentTimeMillis();
                    plugin.getLogger().info("Second Lock acquired after " + (end - start1) + " ms BT");
                }).onLockTryAcquireFailedAsync(() -> {plugin.getLogger().info("Second Lock BT failed");}).acquire(); // will release
            }, 50L, TimeUnit.MILLISECONDS);
            
        }
        scheduler.runLater((task) -> {
            long start = System.currentTimeMillis();
            plugin.getLogger().info("Locked task start It is expected to acquire after 5 seconds "+ start);
            AsyncLock lock = AsyncLock.of("lockedTask2");
            lock.onLockAcquiredAsync(() -> {
                long end = System.currentTimeMillis();
                plugin.getLogger().info("First Lock acquired after "+ (end - start) + " ms");
            }).onLockTryAcquireFailedAsync(() -> {plugin.getLogger().info("First Lock failed "+System.currentTimeMillis());})
            .releaseAfterTask(false).acquire();
            
            
            scheduler.runLater(() -> lock.release(), 5, TimeUnit.SECONDS);
            
            scheduler.runLaterAsync((task3) -> {
                AsyncLock lockSame = AsyncLock.of("lockedTask2");
                lockSame.onLockAcquiredAsync(() -> {
                    long end = System.currentTimeMillis();
                    plugin.getLogger().info("Second Lock acquired after " + (end - start) + " ms");
                }).onLockTryAcquireFailedAsync(() -> {plugin.getLogger().info("Second Lock failed");}).acquire();
            }, 50L, TimeUnit.MILLISECONDS);
        }, 1L, TimeUnit.SECONDS);
    }
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage("lockedTask 6s~");
        }
        else if  (args[0].equalsIgnoreCase("lockedTask")) {
            lockedTask();        
        } 
        return true;
    }
}