/*
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.magmaguy.elitemobs.events.mobs;

import com.magmaguy.elitemobs.ChatColorConverter;
import com.magmaguy.elitemobs.EntityTracker;
import com.magmaguy.elitemobs.MetadataHandler;
import com.magmaguy.elitemobs.config.ConfigValues;
import com.magmaguy.elitemobs.config.EventsConfig;
import com.magmaguy.elitemobs.config.MobCombatSettingsConfig;
import com.magmaguy.elitemobs.events.BossSpecialAttackDamage;
import com.magmaguy.elitemobs.events.EventMessage;
import com.magmaguy.elitemobs.events.mobs.sharedeventproperties.BossMobDeathCountdown;
import com.magmaguy.elitemobs.events.mobs.sharedeventproperties.DynamicBossLevelConstructor;
import com.magmaguy.elitemobs.items.LootTables;
import com.magmaguy.elitemobs.mobconstructor.BossMobEntity;
import com.magmaguy.elitemobs.mobpowers.PowerCooldown;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class TreasureGoblin implements Listener {

    private static ArrayList<BossMobEntity> treasureGoblinList = new ArrayList<>();
    private static ArrayList<LivingEntity> radialGoldExplosionCooldown = new ArrayList<>();
    private static ArrayList<LivingEntity> goldShotgunCooldown = new ArrayList<>();

    public static boolean isTreasureGoblin(Entity entity) {
        if (!(entity instanceof LivingEntity)) return false;
        if (treasureGoblinList.isEmpty()) return false;
        for (BossMobEntity bossMobEntity : treasureGoblinList)
            if (bossMobEntity.getEliteMobEntity().getLivingEntity().equals(entity))
                return true;
        return false;
    }

    public static void createGoblin(Zombie treasureGoblin) {

        int mobLevel = DynamicBossLevelConstructor.findDynamicBossLevel();
        BossMobEntity treasureGoblinPluginEntity = new BossMobEntity(treasureGoblin, mobLevel,
                ChatColorConverter.convert(ConfigValues.eventsConfig.getString(EventsConfig.TREASURE_GOBLIN_NAME)));

        treasureGoblinList.add(treasureGoblinPluginEntity);

        (treasureGoblin).setBaby(true);

        equipTreasureGoblin(treasureGoblin);
        EventMessage.sendEventMessage(treasureGoblin, ConfigValues.eventsConfig.getString(EventsConfig.SMALL_TREASURE_GOBLIN_EVENT_ANNOUNCEMENT_TEXT));
        BossMobDeathCountdown.startDeathCountdown(treasureGoblin);

    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {

        if (isTreasureGoblin(event.getEntity())) {

            Entity entity = event.getEntity();

            for (int i = 0; i < ConfigValues.eventsConfig.getInt(EventsConfig.SMALL_TREASURE_GOBLIN_REWARD); i++)
                LootTables.generateLoot((LivingEntity) entity);


            for (Player player : Bukkit.getServer().getOnlinePlayers()) {

                if (((LivingEntity) entity).getKiller() != null) {

                    String newMessage = ConfigValues.eventsConfig.getString(EventsConfig.SMALL_TREASURE_GOBLIN_EVENT_PLAYER_END_TEXT).replace("$player", ((LivingEntity) entity).getKiller().getDisplayName());
                    player.sendMessage(ChatColorConverter.convert(newMessage));

                } else
                    player.sendMessage(ChatColorConverter.convert(ConfigValues.eventsConfig.getString(EventsConfig.SMALL_TREASURE_GOBLIN_EVENT_OTHER_END_TEXT)));

            }

        }

    }

    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {

        if (!(event.getDamager() instanceof LivingEntity ||
                event.getDamager() instanceof Projectile && ((Projectile) event.getDamager()).getShooter() instanceof LivingEntity))
            return;

        LivingEntity livingEntity;

        if (event.getDamager() instanceof Projectile)
            livingEntity = (LivingEntity) ((Projectile) event.getDamager()).getShooter();
        else
            livingEntity = (LivingEntity) event.getDamager();

        if (isTreasureGoblin(livingEntity))

            if (ThreadLocalRandom.current().nextDouble() < 0.20) {

                //run power
                if (ThreadLocalRandom.current().nextDouble() < 0.5)

                    if (!PowerCooldown.isInCooldown((LivingEntity) event.getEntity(), radialGoldExplosionCooldown)) {

                        PowerCooldown.startCooldownTimer((LivingEntity) event.getEntity(), radialGoldExplosionCooldown,
                                20 * ConfigValues.eventsConfig.getInt(EventsConfig.TREASURE_GOBLIN_RADIAL_EXPLOSION));
                        radialGoldExplosionInitializer((Zombie) event.getEntity());

                    } else if (!PowerCooldown.isInCooldown((LivingEntity) event.getEntity(), goldShotgunCooldown)) {

                        PowerCooldown.startCooldownTimer((LivingEntity) event.getEntity(), goldShotgunCooldown,
                                20 * ConfigValues.eventsConfig.getInt(EventsConfig.TREASURE_GOBLIN_GOLD_SHOTGUN_INTERVAL));
                        goldShotgunInitializer((Zombie) event.getEntity(), livingEntity.getLocation());

                    }


            }


    }

    private void radialGoldExplosionInitializer(Zombie zombie) {

        zombie.setAI(false);

        new BukkitRunnable() {

            int counter = 0;

            @Override
            public void run() {

                if (counter == 20 * 1.5) {

                    radialGoldExplosion(zombie);
                    zombie.setAI(true);
                    cancel();

                }

                if (ConfigValues.mobCombatSettingsConfig.getBoolean(MobCombatSettingsConfig.ENABLE_WARNING_VISUAL_EFFECTS))
                    zombie.getWorld().spawnParticle(Particle.SMOKE_NORMAL, zombie.getLocation(), counter, 1, 1, 1, 0);

                counter++;

            }

        }.runTaskTimer(MetadataHandler.PLUGIN, 0, 1);

    }

    private static void radialGoldExplosion(Zombie zombie) {

        int projectileCount = 200;
        List<Item> itemList = new ArrayList<>();

        for (int i = 0; i < projectileCount; i++) {

            double x = ThreadLocalRandom.current().nextDouble() - 0.5;
            double y = ThreadLocalRandom.current().nextDouble() / 1.5;
            double z = ThreadLocalRandom.current().nextDouble() - 0.5;

            Vector locationAdjuster = new Vector(x, 0, z).normalize();

            Location zombieLocation = zombie.getLocation().add(new Vector(0, 0.5, 0));
            Location projectileLocation = zombieLocation.add(locationAdjuster);
            ItemStack visualProjectileItemStack = new ItemStack(Material.GOLD_NUGGET, 1);

            /*
            Set custom lore to prevent item stacking
             */
            ItemMeta itemMeta = visualProjectileItemStack.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add("" + ThreadLocalRandom.current().nextDouble() + "" + ThreadLocalRandom.current().nextDouble());
            itemMeta.setLore(lore);
            visualProjectileItemStack.setItemMeta(itemMeta);

            Item visualProjectile = projectileLocation.getWorld().dropItem(projectileLocation, visualProjectileItemStack);

            Vector velocity = new Vector(x, y, z).multiply(2);
            visualProjectile.setVelocity(velocity);
            visualProjectile.setPickupDelay(Integer.MAX_VALUE);
            EntityTracker.registerCullableEntity(visualProjectile);

            itemList.add(visualProjectile);

        }

        new BukkitRunnable() {

            int counter = 0;

            @Override
            public void run() {

                if (itemList.size() == 0) cancel();

                Iterator<Item> iterator = itemList.iterator();

                while (iterator.hasNext()) {

                    Item item = iterator.next();

                    if (counter > 20 * 5) {

                        if (item != null && item.isValid()) iterator.remove();
                        if (item != null && item.isValid()) item.remove();
                        continue;

                    }

                    if (item == null || !item.isValid() || item.isDead())
                        iterator.remove();

                    else if (item.isOnGround()) {
                        iterator.remove();
                        item.remove();
                    } else if (goldNuggetDamage(item.getNearbyEntities(0, 0, 0), zombie)) {

                        iterator.remove();
                        item.remove();

                    }

                }

                if (counter > 20 * 5)
                    cancel();

                counter++;

            }

        }.runTaskTimer(MetadataHandler.PLUGIN, 0, 1);

    }

    private static void goldShotgunInitializer(Zombie zombie, Location targetLocation) {

        zombie.setAI(false);

        List<Vector> locationList = new ArrayList<>();

        int projectileCount = 200;

        for (int i = 0; i < projectileCount; i++) {

            double x = ThreadLocalRandom.current().nextDouble() * 2 - 1;
            double y = ThreadLocalRandom.current().nextDouble() * 2 - 1;
            double z = ThreadLocalRandom.current().nextDouble() * 2 - 1;

            Location tempLocation = targetLocation.clone();

            Location simulatedLocation = tempLocation.add(new Location(tempLocation.getWorld(), 0.0, 1.0, 0.0))
                    .add(new Location(tempLocation.getWorld(), x, y, z));

            Vector toPlayer = simulatedLocation.subtract(zombie.getLocation()).toVector().normalize();

            locationList.add(toPlayer);

        }

        new BukkitRunnable() {

            int counter = 0;

            @Override
            public void run() {

                if (counter == 20 * 1) {

                    goldShotgun(zombie, targetLocation);
                    zombie.setAI(true);
                    cancel();

                }

                if (ConfigValues.mobCombatSettingsConfig.getBoolean(MobCombatSettingsConfig.ENABLE_WARNING_VISUAL_EFFECTS)) {


                    Location zombieLocation = zombie.getLocation().clone();

                    int particleCount = 20;

                    for (int i = 0; i < particleCount; i++) {

                        double x = ThreadLocalRandom.current().nextDouble() * 2 - 1;
                        double y = ThreadLocalRandom.current().nextDouble() * 2 - 1;
                        double z = ThreadLocalRandom.current().nextDouble() * 2 - 1;

                        Location tempLocation = targetLocation.clone();

                        Location simulatedLocation = tempLocation.add(new Location(tempLocation.getWorld(), 0.0, 1.0, 0.0))
                                .add(new Location(tempLocation.getWorld(), x, y, z));

                        Vector toTarget = simulatedLocation.subtract(zombie.getLocation()).toVector().normalize();

                        zombieLocation.getWorld().spawnParticle(Particle.SMOKE_NORMAL, zombieLocation.clone().add(new Vector(0, 0.5, 0)), 0, toTarget.getX(), toTarget.getY(), toTarget.getZ(), 0.75);

                    }

                }

                counter++;

            }

        }.runTaskTimer(MetadataHandler.PLUGIN, 0, 1);

    }

    private static void goldShotgun(Zombie zombie, Location livingEntityLocation) {

        int projectileCount = 200;
        ItemStack visualProjectileItemStack = new ItemStack(Material.GOLD_NUGGET, 1);
        List<Item> itemList = new ArrayList<>();

        for (int i = 0; i < projectileCount; i++) {

            double x = ThreadLocalRandom.current().nextDouble() * 2 - 1;
            double y = ThreadLocalRandom.current().nextDouble() * 2 - 1;
            double z = ThreadLocalRandom.current().nextDouble() * 2 - 1;

            Location tempLocation = livingEntityLocation.clone();

            Location simulatedLocation = tempLocation.add(new Location(tempLocation.getWorld(), 0.0, 1.0, 0.0))
                    .add(new Location(tempLocation.getWorld(), x, y, z));

            Vector toPlayer = simulatedLocation.subtract(zombie.getLocation()).toVector().normalize();

            /*
            Set custom lore to prevent item stacking
             */
            ItemMeta itemMeta = visualProjectileItemStack.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add("" + ThreadLocalRandom.current().nextDouble() + "" + ThreadLocalRandom.current().nextDouble());
            itemMeta.setLore(lore);
            visualProjectileItemStack.setItemMeta(itemMeta);

            Item visualProjectile = zombie.getLocation().add(new Location(livingEntityLocation.getWorld(), 0.0, 1.0, 0.0)).getWorld().dropItem(zombie.getLocation(), visualProjectileItemStack);

            visualProjectile.setGravity(false);
            visualProjectile.setVelocity(toPlayer.multiply(0.9));
            visualProjectile.setPickupDelay(Integer.MAX_VALUE);
            EntityTracker.registerCullableEntity(visualProjectile);

            itemList.add(visualProjectile);

        }

        new BukkitRunnable() {

            int counter = 0;

            @Override
            public void run() {

                if (itemList.size() == 0) cancel();

                Iterator<Item> iterator = itemList.iterator();

                while (iterator.hasNext()) {

                    Item item = iterator.next();

                    if (item == null || !item.isValid() || item.isDead())
                        iterator.remove();

                    else if (goldNuggetDamage(item.getNearbyEntities(0.5, 0.5, 0.5), zombie)) {

                        iterator.remove();
                        item.remove();

                    }


                    if (counter > 20 * 3) {

                        iterator.remove();
                        if (item != null) item.remove();

                    }

                }

                if (counter > 20 * 3)
                    cancel();

                counter++;

            }

        }.runTaskTimer(MetadataHandler.PLUGIN, 0, 1);

    }

    private static boolean goldNuggetDamage(List<Entity> entityList, Zombie eliteMob) {

        for (Entity entity : entityList) {

            if (isTreasureGoblin(entity)) break;

            if (entity instanceof LivingEntity)
                return BossSpecialAttackDamage.dealSpecialDamage(eliteMob, (LivingEntity) entity, 1);

        }

        return false;

    }

    public static void equipTreasureGoblin(Zombie zombie) {

        zombie.getEquipment().setHelmet(new ItemStack(Material.GOLD_HELMET, 1));
        zombie.getEquipment().setChestplate(new ItemStack(Material.GOLD_CHESTPLATE));
        zombie.getEquipment().setLeggings(new ItemStack(Material.GOLD_LEGGINGS));
        zombie.getEquipment().setBoots(new ItemStack(Material.GOLD_BOOTS));

    }


}
