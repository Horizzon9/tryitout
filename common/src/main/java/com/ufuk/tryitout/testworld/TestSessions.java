package com.ufuk.tryitout.testworld;

import com.ufuk.tryitout.TryItOut;
import com.ufuk.tryitout.testworld.net.TestStatusPayload;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Entering and leaving the test world. Every step is written to disk first ({@link SessionStore}); the real
 * profile is never only in memory. Entering is refused unless the player is in the REAL state (no nesting), and
 * the only way out is {@link #exit}: anyone found in the test world without a session, or out of it while in a
 * session, is put right on the next tick.
 */
public final class TestSessions {
    /** Ticks after taking damage during which entering is refused (no escaping a fight). */
    static final int HURT_COOLDOWN_TICKS = 200;

    private static MinecraftServer server;
    private static SessionStore store;
    private static final Map<UUID, Integer> lastHurt = new HashMap<>();
    /** Finished sessions whose REAL state could not be written yet (file locked): never recovered twice. */
    private static final Map<UUID, SessionStore.Session> pendingDone = new HashMap<>();
    /** Cache of the live states; the files stay the source of truth. */
    private static final Map<UUID, SessionStore.Session> active = new HashMap<>();

    private TestSessions() {
    }

    static void onServerStarted(MinecraftServer started) {
        server = started;
        store = new SessionStore(started);
        active.clear();
        lastHurt.clear();
        pendingDone.clear();
        deathProfiles.clear();
    }

    static void onServerStopped() {
        flushPending();
        server = null;
        store = null;
        active.clear();
        lastHurt.clear();
    }

    static void onHurt(ServerPlayer player) {
        lastHurt.put(player.getUUID(), player.server.getTickCount());
    }

    public static boolean isInTest(ServerPlayer player) {
        SessionStore.Session session = active.get(player.getUUID());
        return session != null && session.state() == SessionState.IN_TEST;
    }

    /** Border markers always; walls and ceiling unless the room says they may be broken. */
    static boolean isProtected(ServerPlayer player, net.minecraft.core.BlockPos pos) {
        SessionStore.Session session = active.get(player.getUUID());
        if (session == null || !TestWorld.isTestWorld(player.level())) {
            return false;
        }
        TestArea area = area(session);
        if (area.isMarker(pos.getX(), pos.getY(), pos.getZ())) {
            return true; // the red border edges never break
        }
        // Walls, ceiling and floor only when the room owner made them unbreakable.
        return RoomBlocks.locked(session.room())
                && (TestAreaBuilder.isFloor(area, pos) || TestAreaBuilder.isStructure(session.room(), area, pos));
    }

    // ---- enter ----

    /** Enters a new test world built from the pocket scene's room. */
    public static void enter(ServerPlayer player, SceneSnapshot room, net.minecraft.world.item.ItemStack target) {
        if (store == null || refuse(player)) {
            return;
        }
        if (!validSize(room.size())) {
            TestNetwork.send(player, new TestStatusPayload(false, "tryitout.test.refused.too_big"));
            return;
        }
        ServerLevel testLevel = player.server.getLevel(TestWorld.DIMENSION);
        try {
            int slot = store.slotFor(player.getUUID());
            begin(player, slot, room.settingsTag(), area -> TestAreaBuilder.reset(testLevel, area, room, room.settingsTag()), null,
                    target, isWeapon(target));
        } catch (IOException e) {
            TryItOut.LOGGER.error("TryItOut: no test area slot for {}", player.getGameProfile().getName(), e);
        }
    }

    /** Enters one of the player's saved test worlds: its room, blocks, entities and test character. */
    public static void enterSaved(ServerPlayer player, String rawName) {
        if (store == null || refuse(player)) {
            return;
        }
        String name = TestSaves.cleanName(rawName);
        Optional<CompoundTag> save = name.isEmpty() ? Optional.empty()
                : TestSaves.load(player.server, player.getUUID(), name);
        if (save.isEmpty()) {
            TestNetwork.send(player, new TestStatusPayload(false, "tryitout.test.save.missing"));
            return;
        }
        CompoundTag room = save.get().getCompound("Room").copy();
        room.putString("SavedAs", name);
        int size = room.getInt("Size");
        if (!validSize(size)) {
            TestNetwork.send(player, new TestStatusPayload(false, "tryitout.test.refused.too_big"));
            return;
        }
        SceneSnapshot settings = SceneSnapshot.fromTag(room, null);
        ServerLevel testLevel = player.server.getLevel(TestWorld.DIMENSION);
        try {
            int slot = store.slotFor(player.getUUID());
            CompoundTag profile = save.get().getCompound("Profile").copy();
            begin(player, slot, room, area -> {
                TestAreaBuilder.reset(testLevel, area, settings, room);
                TestSaves.place(testLevel, area, save.get());
            }, profile.isEmpty() ? null : profile, net.minecraft.world.item.ItemStack.EMPTY, false);
        } catch (IOException e) {
            TryItOut.LOGGER.error("TryItOut: no test area slot for {}", player.getGameProfile().getName(), e);
        }
    }

    /** A guest joins the host's test world with a fresh test character of their own. */
    static void enterAsGuest(ServerPlayer guest, ServerPlayer host) {
        SessionStore.Session hostSession = active.get(host.getUUID());
        if (store == null || hostSession == null || hostSession.state() != SessionState.IN_TEST
                || hostSession.room().contains("Host")) {
            TestNetwork.send(guest, new TestStatusPayload(false, "tryitout.test.invite.gone"));
            return;
        }
        if (refuse(guest)) {
            return;
        }
        CompoundTag room = hostSession.room().copy();
        room.putString("Host", host.getUUID().toString());
        room.remove("Body");
        room.remove("SavedAs");
        if (begin(guest, hostSession.slot(), room, null, null, net.minecraft.world.item.ItemStack.EMPTY, false)) {
            TestNetwork.send(host, new TestStatusPayload(true, "tryitout.test.invite.joined"));
        }
    }

    /** Sends the refusal message and returns true when the player may not enter now. */
    private static boolean refuse(ServerPlayer player) {
        String refusal = refusal(player);
        if (refusal != null) {
            TestNetwork.send(player, new TestStatusPayload(isInTest(player), refusal));
            return true;
        }
        return false;
    }

    /**
     * The swap into the test world, the same for every way in: real profile to disk first, area built (unless a
     * guest joins an existing one), test profile on, teleport, body left in the real world, state IN_TEST.
     */
    private static boolean begin(ServerPlayer player, int slot, CompoundTag room,
            java.util.function.Consumer<TestArea> build, CompoundTag testProfile,
            net.minecraft.world.item.ItemStack target, boolean dummy) {
        ServerLevel testLevel = player.server.getLevel(TestWorld.DIMENSION);
        UUID id = player.getUUID();
        try {
            CompoundTag real = TestProfiles.capture(player);
            SessionStore.Session session = new SessionStore.Session(SessionState.ENTERING, slot, real,
                    player.level().dimension().location().toString(), player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot(), room.copy());
            store.save(id, session); // the real profile is on disk before anything changes
            active.put(id, session);
            TestArea area = area(session);
            if (build != null) {
                build.accept(area);
            }
            TestProfiles.apply(player, testProfile != null ? testProfile
                    : TestProfiles.blankTestProfile(real, player.getMaxHealth()));
            player.addTag(OWNER_TAG + id); // gone again when the real NBT is loaded back
            giveTarget(player, target);
            double[] spawn = TestAreaBuilder.safeCentre(testLevel, area);
            teleport(player, testLevel, spawn[0], spawn[1], spawn[2], 0.0F, 0.0F);
            broadcastGameMode(player);
            // The body stays where the player stood; recorded before it is added (unrecorded bodies are refused).
            ServerLevel realLevel = levelOrOverworld(session.realDimension());
            net.minecraft.world.entity.decoration.ArmorStand body = TestBodies.create(player, session, realLevel);
            CompoundTag liveRoom = session.room();
            if (body != null) {
                liveRoom.putString("Body", body.getUUID().toString());
            }
            session = new SessionStore.Session(SessionState.IN_TEST, slot, real, session.realDimension(), session.x(),
                    session.y(), session.z(), session.yaw(), session.pitch(), liveRoom);
            active.put(id, session);
            store.save(id, session);
            if (body != null && !realLevel.addFreshEntity(body)) {
                TryItOut.LOGGER.warn("TryItOut: the body of {} could not be placed",
                        player.getGameProfile().getName());
            }
            TestNetwork.send(player, new TestStatusPayload(true, "tryitout.test.entered"));
            TestNetwork.sendSettings(player, settingsOf(player, session));
            sendSaves(player);
            if (dummy) {
                TestDummies.add(player, area); // a weapon scene: something to hit right away
            }
            return true;
        } catch (IOException | RuntimeException e) {
            TryItOut.LOGGER.error("TryItOut: entering the test world failed for {}",
                    player.getGameProfile().getName(), e);
            recover(player, "tryitout.test.enter_failed");
            return false;
        }
    }

    // ---- saves, materials, invites ----

    private static boolean isGuest(SessionStore.Session session) {
        return session.room().contains("Host");
    }

    /** Saved worlds of the player (and whether they may save here). */
    public static void sendSaves(ServerPlayer player) {
        SessionStore.Session session = active.get(player.getUUID());
        boolean canSave = session != null && session.state() == SessionState.IN_TEST && !isGuest(session)
                && TestServerConfig.get().saveLimit > 0;
        String current = session == null ? "" : session.room().getString("SavedAs");
        TestNetwork.sendSaves(player, new com.ufuk.tryitout.testworld.net.TestSavesPayload(
                TestSaves.list(player.server, player.getUUID()), TestServerConfig.get().saveLimit, current, canSave));
    }

    /** Saves the test world as it is now (blocks, entities, test character) under {@code rawName}. */
    public static void save(ServerPlayer player, String rawName) {
        SessionStore.Session session = active.get(player.getUUID());
        ServerLevel testLevel = player.server.getLevel(TestWorld.DIMENSION);
        if (session == null || session.state() != SessionState.IN_TEST || testLevel == null || isGuest(session)) {
            TestNetwork.send(player, new TestStatusPayload(isInTest(player), "tryitout.test.save.not_allowed"));
            return;
        }
        // An empty name is the quick-save key: the save this world came from (or was last saved as), else a fixed one.
        String name = rawName == null || rawName.isBlank()
                ? (session.room().getString("SavedAs").isEmpty() ? QUICK_SAVE : session.room().getString("SavedAs"))
                : TestSaves.cleanName(rawName);
        int limit = TestServerConfig.get().saveLimit;
        if (name.isEmpty()) {
            TestNetwork.send(player, new TestStatusPayload(true, "tryitout.test.save.bad_name"));
            return;
        }
        if (!TestSaves.exists(player.server, player.getUUID(), name)
                && TestSaves.list(player.server, player.getUUID()).size() >= limit) {
            TestNetwork.send(player, new TestStatusPayload(true, "tryitout.test.save.limit"));
            return;
        }
        try {
            CompoundTag room = session.room().copy();
            room.remove("Body");
            room.remove("SavedAs");
            TestSaves.save(player.server, player.getUUID(), name, room, TestProfiles.capture(player), testLevel,
                    area(session));
            CompoundTag live = session.room();
            live.putString("SavedAs", name);
            store.save(player.getUUID(), session);
            TestNetwork.send(player, new TestStatusPayload(true, "tryitout.test.save.done"));
            if (SableCompat.shipCount(testLevel, area(session)) > 0) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "tryitout.test.save.ships"), false);
            }
            sendSaves(player);
        } catch (IOException | RuntimeException e) {
            TryItOut.LOGGER.error("TryItOut: saving the test world of {} failed",
                    player.getGameProfile().getName(), e);
            TestNetwork.send(player, new TestStatusPayload(true, "tryitout.test.save.failed"));
        }
    }

    public static void deleteSave(ServerPlayer player, String rawName) {
        String name = TestSaves.cleanName(rawName);
        try {
            if (!name.isEmpty()) {
                TestSaves.delete(player.server, player.getUUID(), name);
            }
        } catch (IOException e) {
            TryItOut.LOGGER.warn("TryItOut: could not delete test world {}", name, e);
        }
        sendSaves(player);
    }

    /** The build's materials in the player's test area. */
    public static void materials(ServerPlayer player) {
        SessionStore.Session session = active.get(player.getUUID());
        ServerLevel testLevel = player.server.getLevel(TestWorld.DIMENSION);
        if (session == null || session.state() != SessionState.IN_TEST || testLevel == null) {
            return;
        }
        Map<String, Integer> counts = TestMaterials.count(testLevel, area(session), session.room());
        TryItOut.LOGGER.info("TryItOut: materials for {}: {} kinds", player.getGameProfile().getName(),
                counts.size());
        TestNetwork.sendMaterials(player, new com.ufuk.tryitout.testworld.net.TestMaterialsPayload(
                new java.util.ArrayList<>(counts.keySet()), new java.util.ArrayList<>(counts.values())));
    }

    /** Invites another online player into this test world. */
    public static void invite(ServerPlayer host, String targetName) {
        SessionStore.Session session = active.get(host.getUUID());
        if (session == null || session.state() != SessionState.IN_TEST || isGuest(session)) {
            TestNetwork.send(host, new TestStatusPayload(isInTest(host), "tryitout.test.invite.not_owner"));
            return;
        }
        if (!TestServerConfig.get().allowFriends) {
            TestNetwork.send(host, new TestStatusPayload(true, "tryitout.test.invite.disabled"));
            return;
        }
        ServerPlayer target = host.server.getPlayerList().getPlayerByName(targetName.trim());
        if (target == null || target == host) {
            TestNetwork.send(host, new TestStatusPayload(true, "tryitout.test.invite.no_player"));
            return;
        }
        TestInvites.invite(host, target);
        TestNetwork.send(host, new TestStatusPayload(true, "tryitout.test.invite.sent"));
    }

    /**
     * The scene's item goes into the test character's hand (first hotbar slot). It exists only in the test
     * profile: nothing of the test profile ever reaches the real one.
     */
    private static void giveTarget(ServerPlayer player, net.minecraft.world.item.ItemStack target) {
        if (target == null || target.isEmpty()) {
            return;
        }
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(target.getItem()).toString();
        if (TestServerConfig.get().bannedItems.contains(id)
                || (target.getItem() instanceof net.minecraft.world.item.BlockItem block
                        && block.getBlock() instanceof net.minecraft.world.level.block.GameMasterBlock)) {
            return;
        }
        player.getInventory().selected = 0;
        player.getInventory().setItem(0, target.copyWithCount(1));
        player.inventoryMenu.broadcastChanges();
    }

    /** Anything that is not placed as a block (swords, axes, bows, tridents...) gets a dummy on entry. */
    private static boolean isWeapon(net.minecraft.world.item.ItemStack target) {
        return target != null && !target.isEmpty()
                && !(target.getItem() instanceof net.minecraft.world.item.BlockItem)
                && !(target.getItem() instanceof net.minecraft.world.item.HangingEntityItem);
    }

    /** Why the player may not enter now, as a message key; null when allowed. */
    private static String refusal(ServerPlayer player) {
        if (player.server.getLevel(TestWorld.DIMENSION) == null) {
            return "tryitout.test.missing_dimension";
        }
        SessionState state = stateOf(player.getUUID());
        if (!state.canEnter() || TestWorld.isTestWorld(player.level())) {
            return "tryitout.test.refused.already";
        }
        if (!player.isAlive()) {
            return "tryitout.test.refused.dead";
        }
        // Entering mid-fight, while falling or burning is allowed (user decision): the player is responsible. The
        // real body stays where the player was and keeps taking whatever damage comes. Riding / sleeping simply end.
        if (player.isPassenger()) {
            player.stopRiding();
        }
        if (player.isSleeping()) {
            player.stopSleepInBed(true, true);
        }
        return null;
    }

    private static SessionState stateOf(UUID id) {
        SessionStore.Session cached = active.get(id);
        if (cached != null) {
            return cached.state();
        }
        return store == null ? SessionState.REAL
                : store.load(id).map(SessionStore.Session::state).orElse(SessionState.REAL);
    }

    /** True while {@code owner} is in the test world and {@code body} is the body recorded for that session. */
    static boolean ownsBody(UUID owner, UUID body) {
        SessionStore.Session session = active.get(owner);
        return session != null && session.state() == SessionState.IN_TEST
                && body.toString().equals(session.room().getString("Body"));
    }

    /** The body was hurt: the real profile loses that much health; at zero the player dies for real. */
    static void bodyHurt(MinecraftServer server, UUID owner, float amount) {
        SessionStore.Session session = active.get(owner);
        if (session == null || session.state() != SessionState.IN_TEST || store == null) {
            return;
        }
        CompoundTag real = session.real();
        float health = real.getFloat("Health") - amount;
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        try {
            real.putFloat("Health", Math.max(1.0F, health));
            store.save(owner, session);
        } catch (IOException e) {
            TryItOut.LOGGER.warn("TryItOut: could not save the body damage of {}", owner, e);
        }
        if (player == null) {
            return;
        }
        if (health <= 0.0F) {
            recover(player, "tryitout.test.body.died");
            player.kill(); // the real character dies where the body stood, by the server's own rules
        } else {
            player.displayClientMessage(TestBodies.hurtMessage(amount, health), true);
        }
    }

    // ---- settings inside the test world ----

    /** Current settings of the player's room and of the test dimension's time and weather. */
    static com.ufuk.tryitout.testworld.net.TestSettingsPayload settingsOf(ServerPlayer player,
            SessionStore.Session session) {
        CompoundTag room = session.room();
        int flags = 0;
        flags |= room.getBoolean("Ceiling") ? com.ufuk.tryitout.testworld.net.TestSettingsPayload.CEILING : 0;
        flags |= room.getBoolean("Commands") ? com.ufuk.tryitout.testworld.net.TestSettingsPayload.COMMANDS : 0;
        flags |= TestRules.mobSpawning() ? com.ufuk.tryitout.testworld.net.TestSettingsPayload.MOB_SPAWNING : 0;
        flags |= TestRules.fireSpread() ? com.ufuk.tryitout.testworld.net.TestSettingsPayload.FIRE_SPREAD : 0;
        flags |= TestRules.randomTicks() ? com.ufuk.tryitout.testworld.net.TestSettingsPayload.RANDOM_TICKS : 0;
        flags |= TestRules.keepInventory() ? com.ufuk.tryitout.testworld.net.TestSettingsPayload.KEEP_INVENTORY : 0;
        flags |= RoomBlocks.locked(room) ? com.ufuk.tryitout.testworld.net.TestSettingsPayload.LOCKED : 0;
        flags |= room.getBoolean(TestDummies.KILLABLE) ? com.ufuk.tryitout.testworld.net.TestSettingsPayload.DUMMY_KILLABLE : 0;
        flags |= room.getBoolean(TestAreaBuilder.BORDER_HIDDEN) ? com.ufuk.tryitout.testworld.net.TestSettingsPayload.BORDER_HIDDEN : 0;
        flags |= room.getBoolean(UNLIMITED) ? com.ufuk.tryitout.testworld.net.TestSettingsPayload.UNLIMITED : 0;
        flags |= TestServerConfig.get().allowUnlimited ? com.ufuk.tryitout.testworld.net.TestSettingsPayload.UNLIMITED_ALLOWED : 0;
        flags |= room.getBoolean(TestDummies.REMOVE) ? com.ufuk.tryitout.testworld.net.TestSettingsPayload.DUMMY_REMOVE : 0;
        return new com.ufuk.tryitout.testworld.net.TestSettingsPayload(room.getInt("Walls"), flags,
                TestClock.timeSetting(), TestClock.weather(), player.gameMode.getGameModeForPlayer().getId(),
                session.size(), largestSize(), room.getString(RoomBlocks.FLOOR), room.getString(RoomBlocks.WALL),
                room.getString(RoomBlocks.CEILING), TestDummies.health(room));
    }

    /**
     * Applies what the player asked for: walls, ceiling, commands (this room); game mode (this player); time,
     * weather and nature rules (the whole test dimension). The floor never changes here.
     */
    public static void applySettings(ServerPlayer player, com.ufuk.tryitout.testworld.net.TestSettingsPayload wanted) {
        SessionStore.Session session = active.get(player.getUUID());
        ServerLevel testLevel = player.server.getLevel(TestWorld.DIMENSION);
        if (session == null || session.state() != SessionState.IN_TEST || testLevel == null
                || !TestWorld.isTestWorld(player.level())) {
            return;
        }
        if (isGuest(session)) {
            TestNetwork.send(player, new TestStatusPayload(true, "tryitout.test.invite.guest_settings"));
            TestNetwork.sendSettings(player, settingsOf(player, session));
            return;
        }
        com.ufuk.tryitout.testworld.net.TestSettingsPayload next = wanted.sanitized();
        TryItOut.LOGGER.info("TryItOut: test settings for {}: walls={} flags={} time={} weather={} mode={}",
                player.getGameProfile().getName(), next.walls(), next.flags(), next.time(), next.weather(),
                next.gameMode());
        boolean ceiling = next.has(com.ufuk.tryitout.testworld.net.TestSettingsPayload.CEILING);
        CompoundTag before = session.room();
        CompoundTag after = before.copy();
        after.putInt("Walls", next.walls());
        after.putBoolean("Ceiling", ceiling);
        after.putBoolean("Commands", next.has(com.ufuk.tryitout.testworld.net.TestSettingsPayload.COMMANDS));
        after.putBoolean(RoomBlocks.LOCKED, next.has(com.ufuk.tryitout.testworld.net.TestSettingsPayload.LOCKED));
        after.putBoolean(TestDummies.KILLABLE, next.has(com.ufuk.tryitout.testworld.net.TestSettingsPayload.DUMMY_KILLABLE));
        after.putBoolean(TestDummies.REMOVE, next.has(com.ufuk.tryitout.testworld.net.TestSettingsPayload.DUMMY_REMOVE));
        after.putBoolean(TestAreaBuilder.BORDER_HIDDEN, next.has(com.ufuk.tryitout.testworld.net.TestSettingsPayload.BORDER_HIDDEN));
        // Unlimited only when the server allows it; red edges are not shown while it is on.
        after.putBoolean(UNLIMITED, next.has(com.ufuk.tryitout.testworld.net.TestSettingsPayload.UNLIMITED) && TestServerConfig.get().allowUnlimited);
        boolean edgesBefore = !before.getBoolean(TestAreaBuilder.BORDER_HIDDEN) && !before.getBoolean(UNLIMITED);
        boolean edgesAfter = !after.getBoolean(TestAreaBuilder.BORDER_HIDDEN) && !after.getBoolean(UNLIMITED);
        boolean edgesChanged = edgesBefore != edgesAfter;
        boolean dummiesChanged = before.getBoolean(TestDummies.KILLABLE) != after.getBoolean(TestDummies.KILLABLE);
        int size = session.size();
        if (next.size() != 0 && next.size() != size && validSize(next.size())) {
            size = next.size();
            after.putInt("Size", size);
        }
        try {
            if (size != session.size()) {
                TestArea from = area(session);
                TestArea to = TestArea.forSlot(session.slot(), size);
                TestAreaBuilder.resize(testLevel, from, to, before, after);
                resizeGuests(player.getUUID(), size);
                TryItOut.LOGGER.info("TryItOut: test area of {} is now {}x{}",
                        player.getGameProfile().getName(), size, size);
            } else if (before.getInt("Walls") != next.walls() || before.getBoolean("Ceiling") != ceiling) {
                TestAreaBuilder.rebuildStructure(testLevel, area(session), before, after);
            }
            SessionStore.Session updated = new SessionStore.Session(session.state(), session.slot(), session.real(),
                    session.realDimension(), session.x(), session.y(), session.z(), session.yaw(), session.pitch(), after);
            store.save(player.getUUID(), updated);
            active.put(player.getUUID(), updated);
            if (dummiesChanged) {
                TestDummies.applySettings(testLevel, area(updated), after);
            }
            if (edgesChanged && size == session.size()) {
                TestAreaBuilder.setEdgesVisible(testLevel, area(updated), edgesAfter);
            }
            if (next.time() != TestClock.timeSetting()) {
                TestClock.setTime(player.server, next.time());
            }
            if (next.weather() != TestClock.weather()) {
                TestClock.setWeather(player.server, next.weather());
            }
            TestRules.set(next.has(com.ufuk.tryitout.testworld.net.TestSettingsPayload.MOB_SPAWNING),
                    next.has(com.ufuk.tryitout.testworld.net.TestSettingsPayload.FIRE_SPREAD),
                    next.has(com.ufuk.tryitout.testworld.net.TestSettingsPayload.RANDOM_TICKS),
                    next.has(com.ufuk.tryitout.testworld.net.TestSettingsPayload.KEEP_INVENTORY));
            net.minecraft.world.level.GameType mode = net.minecraft.world.level.GameType.byId(next.gameMode());
            if (player.gameMode.getGameModeForPlayer() != mode) {
                player.setGameMode(mode);
            }
            TestNetwork.sendSettings(player, settingsOf(player, updated));
        } catch (IOException | RuntimeException e) {
            TryItOut.LOGGER.error("TryItOut: test world settings of {} failed",
                    player.getGameProfile().getName(), e);
        }
    }

    /** Why a command typed in the test world is refused (null: it may run). Commands are off unless the room allows them. */
    public static String commandRefusal(ServerPlayer player, String command) {
        SessionStore.Session session = active.get(player.getUUID());
        if (session == null || !session.room().getBoolean("Commands")) {
            return "tryitout.test.command.off";
        }
        java.util.List<String> others = new java.util.ArrayList<>();
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (other != player) {
                others.add(other.getGameProfile().getName().toLowerCase(java.util.Locale.ROOT));
            }
        }
        return CommandFilter.refusal(command, TestServerConfig.get().extraCommands, others,
                player.getX(), player.getY(), player.getZ());
    }

    // ---- death inside the test world ----

    /** Test profile at the moment of death (inventory kept or not, as the test world's rule says). */
    private static final Map<UUID, CompoundTag> deathProfiles = new HashMap<>();

    static void onDeath(ServerPlayer player) {
        SessionStore.Session session = active.get(player.getUUID());
        if (session == null || session.state() != SessionState.IN_TEST || !TestWorld.isTestWorld(player.level())) {
            return;
        }
        CompoundTag profile = TestProfiles.capture(player);
        if (!TestRules.keepInventory()) {
            profile.put("Inventory", new net.minecraft.nbt.ListTag()); // the items drop in the test world
            profile.putInt("XpLevel", 0);
            profile.putFloat("XpP", 0.0F);
            profile.putInt("XpTotal", 0);
        }
        deathProfiles.put(player.getUUID(), profile);
    }

    /** Respawn of a test player: back in the test world with the test profile, never at the real spawn. */
    static void onRespawn(ServerPlayer player) {
        CompoundTag profile = deathProfiles.remove(player.getUUID());
        SessionStore.Session session = active.get(player.getUUID());
        ServerLevel testLevel = player.server.getLevel(TestWorld.DIMENSION);
        if (profile == null || session == null || session.state() != SessionState.IN_TEST || testLevel == null) {
            return; // not a test death: the tick puts things right if needed
        }
        profile.putFloat("Health", player.getMaxHealth());
        profile.putInt("foodLevel", 20);
        profile.putFloat("foodSaturationLevel", 5.0F);
        profile.remove("DeathTime");
        profile.remove("HurtTime");
        profile.remove("active_effects");
        profile.putShort("Fire", (short) 0);
        profile.putShort("Air", (short) 300);
        TestProfiles.apply(player, profile);
        TestArea area = area(session);
        double[] spawn = TestAreaBuilder.safeCentre(testLevel, area);
        teleport(player, testLevel, spawn[0], spawn[1], spawn[2], 0.0F, 0.0F);
        TestNetwork.send(player, new TestStatusPayload(true, "tryitout.test.respawned"));
        TestNetwork.sendSettings(player, settingsOf(player, session));
    }

    /** Adds a training dummy in front of the player, or removes every dummy of the player's area. */
    public static void dummy(ServerPlayer player, boolean add) {
        SessionStore.Session session = active.get(player.getUUID());
        if (session == null || session.state() != SessionState.IN_TEST || !TestWorld.isTestWorld(player.level())) {
            return;
        }
        TestArea area = area(session);
        String message = add ? TestDummies.add(player, area) : TestDummies.removeAll(player, area);
        TestNetwork.send(player, new TestStatusPayload(true, message));
    }

    /** Dummy health typed by the owner (1..10 000); applied to the area's dummies at once. */
    public static void setDummyHealth(ServerPlayer player, String value) {
        SessionStore.Session session = active.get(player.getUUID());
        ServerLevel testLevel = player.server.getLevel(TestWorld.DIMENSION);
        if (session == null || session.state() != SessionState.IN_TEST || testLevel == null || isGuest(session)) {
            return;
        }
        int hp;
        try {
            hp = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return;
        }
        hp = Math.max(1, Math.min(com.ufuk.tryitout.testworld.net.TestSettingsPayload.MAX_DUMMY_HP, hp));
        session.room().putInt(TestDummies.HEALTH, hp);
        try {
            store.save(player.getUUID(), session);
        } catch (IOException e) {
            TryItOut.LOGGER.warn("TryItOut: could not save the dummy health of {}",
                    player.getGameProfile().getName(), e);
        }
        TestDummies.applySettings(testLevel, area(session), session.room());
        TestNetwork.sendSettings(player, settingsOf(player, session));
    }

    /** Renames one dummy of the player's area ({@code arg} = "uuid|name"; an empty name gives back "Dummy N"). */
    public static void renameDummy(ServerPlayer player, String arg) {
        SessionStore.Session session = active.get(player.getUUID());
        int bar = arg == null ? -1 : arg.indexOf('|');
        if (session == null || session.state() != SessionState.IN_TEST || bar < 0) {
            return;
        }
        try {
            TestDummies.rename(player, area(session), UUID.fromString(arg.substring(0, bar)), arg.substring(bar + 1));
        } catch (IllegalArgumentException e) {
            // not a UUID
        }
    }

    /** Removes one dummy ({@code id} = its UUID) or, with an empty id, every dummy of the player's area. */
    public static void removeDummy(ServerPlayer player, String id) {
        SessionStore.Session session = active.get(player.getUUID());
        if (session == null || session.state() != SessionState.IN_TEST || !TestWorld.isTestWorld(player.level())) {
            return;
        }
        TestArea area = area(session);
        if (id == null || id.isBlank()) {
            TestNetwork.send(player, new TestStatusPayload(true, TestDummies.removeAll(player, area)));
            return;
        }
        try {
            TestDummies.removeOne(player, area, UUID.fromString(id.trim()));
        } catch (IllegalArgumentException e) {
            // not a UUID: nothing to remove
        }
    }

    // ---- exit ----

    /** The one way out: the saved real profile goes back on the player, the unsaved area is cleared. */
    public static void exit(ServerPlayer player) {
        if (stateOf(player.getUUID()) != SessionState.IN_TEST) {
            TestNetwork.send(player, new TestStatusPayload(false, "tryitout.test.refused.not_in_test"));
            return;
        }
        SessionStore.Session session = active.get(player.getUUID());
        recover(player, "tryitout.test.exited", session == null || !isGuest(session));
    }

    static void recover(ServerPlayer player, String messageKey) {
        recover(player, messageKey, false);
    }

    /** Test area sizes (the pocket scene's and the big ones), up to the configured largest area. */
    private static boolean validSize(int size) {
        for (int allowed : TestArea.SIZES) {
            if (allowed == size) {
                return size <= TestServerConfig.get().areaSize;
            }
        }
        return false;
    }

    /**
     * Puts the saved real profile back whatever the current state is (normal exit, crash recovery, a player who
     * left the test world some other way). Safe to call twice.
     */
    static void recover(ServerPlayer player, String messageKey, boolean sendRoom) {
        Optional<UUID> owner = store == null ? Optional.empty() : sessionOwner(player);
        if (owner.isEmpty()) {
            active.remove(player.getUUID());
            return;
        }
        UUID id = owner.get();
        Optional<SessionStore.Session> saved = store.load(id);
        if (saved.isEmpty() || !saved.get().state().needsRecovery()) {
            active.remove(id);
            return;
        }
        SessionStore.Session session = saved.get();
        boolean guest = isGuest(session);
        if (!guest) {
            evacuateGuests(id);
        }
        try {
            store.save(id, session.withState(SessionState.EXITING));
            ServerLevel testLevelBefore = player.server.getLevel(TestWorld.DIMENSION);
            byte[] room = null;
            if (sendRoom && testLevelBefore != null) {
                room = TestAreaBuilder.captureBytes(testLevelBefore, area(session), session.room());
            }
            ServerLevel target = levelOrOverworld(session.realDimension());
            boolean sameLevel = target == player.level();
            TestProfiles.apply(player, session.real());
            if (sameLevel) {
                // Already in the right dimension (left the test world some other way): a plain teleport.
                player.connection.teleport(session.x(), session.y(), session.z(), session.yaw(), session.pitch());
                player.server.getPlayerList().sendAllPlayerInfo(player);
            } else {
                teleport(player, target, session.x(), session.y(), session.z(), session.yaw(), session.pitch());
            }
            broadcastGameMode(player);
            ServerLevel testLevel = player.server.getLevel(TestWorld.DIMENSION);
            if (testLevel != null && !guest) {
                TestAreaBuilder.clear(testLevel, area(session));
                clearUsed(testLevel, session);
            }
            TestBodies.remove(player.server, session);
            // The real profile is back on the player: from here on, never apply it a second time.
            SessionStore.Session done = new SessionStore.Session(SessionState.REAL, session.slot(), new CompoundTag(),
                    "", 0, 0, 0, 0, 0, new CompoundTag());
            active.remove(id);
            try {
                store.save(id, done);
            } catch (IOException e) {
                pendingDone.put(id, done); // written again on later ticks
                TryItOut.LOGGER.warn("TryItOut: could not mark the session of {} finished yet; retrying",
                        player.getGameProfile().getName(), e);
            }
            TestNetwork.send(player, new TestStatusPayload(false, messageKey));
            if (room != null) {
                TestNetwork.sendRoom(player, room);
            }
        } catch (IOException | RuntimeException e) {
            // The file still says EXITING with the real profile in it: the next join tries again.
            TryItOut.LOGGER.error("TryItOut: restoring the real profile of {} failed",
                    player.getGameProfile().getName(), e);
        }
    }

    /** Every guest of {@code host} who is online goes back to their real profile (the host is leaving). */
    private static void evacuateGuests(UUID host) {
        if (server == null) {
            return;
        }
        String hostId = host.toString();
        for (ServerPlayer other : java.util.List.copyOf(server.getPlayerList().getPlayers())) {
            SessionStore.Session session = active.get(other.getUUID());
            if (session != null && hostId.equals(session.room().getString("Host"))) {
                recover(other, "tryitout.test.invite.host_left");
            }
        }
    }

    /** Everyone in the test world goes back to their real profile (crash guard). */
    static void evacuateAll(String messageKey) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : java.util.List.copyOf(server.getPlayerList().getPlayers())) {
            if (active.containsKey(player.getUUID())) {
                recover(player, messageKey);
            }
        }
    }

    /** Areas of every owner in the test world now (for entity limits). */
    /** True where blocks may be placed / fluids may flow: inside an active area's build box, or on a ship. */
    public static boolean canBuildAt(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        return insideBuildBox(pos.getX(), pos.getY(), pos.getZ()) || SableCompat.isShipBlock(level, pos);
    }

    /**
     * True for a block an explosion must spare: the red border edges, and the floor / walls / ceiling of a room set
     * to unbreakable.
     */
    public static boolean unbreakable(net.minecraft.core.BlockPos pos) {
        for (SessionStore.Session session : active.values()) {
            if (session.state() != SessionState.IN_TEST || isGuest(session)) {
                continue;
            }
            TestArea area = area(session);
            if (area.isMarker(pos.getX(), pos.getY(), pos.getZ())) {
                return true;
            }
            if (RoomBlocks.locked(session.room()) && (TestAreaBuilder.isFloor(area, pos)
                    || TestAreaBuilder.isStructure(session.room(), area, pos))) {
                return true;
            }
        }
        return false;
    }

    /** True inside the build box of an active test area (fluids stop at its border). */
    public static boolean insideBuildBox(int x, int y, int z) {
        for (SessionStore.Session session : active.values()) {
            if (session.state() != SessionState.IN_TEST || isGuest(session)) {
                continue;
            }
            if (inside(limits(session), x, y, z)) {
                return true;
            }
        }
        return false;
    }

    static final String UNLIMITED = "Unlimited";
    /** Room settings: the part of the region an unlimited area really used (cleared on exit). */
    private static final String USED = "UsedBox";

    static boolean unlimited(SessionStore.Session session) {
        return session.room().getBoolean(UNLIMITED) && TestServerConfig.get().allowUnlimited;
    }

    /** Allowed space of a session's area (see {@link TestArea#limits}). */
    static int[] limits(SessionStore.Session session) {
        return area(session).limits(unlimited(session));
    }

    private static boolean inside(int[] box, double x, double y, double z) {
        return x >= box[0] && x < box[3] + 1 && y >= box[1] && y < box[4] + 1 && z >= box[2] && z < box[5] + 1;
    }

    private static boolean insideXZ(int[] box, double x, double z) {
        return x >= box[0] - 1 && x < box[3] + 2 && z >= box[2] - 1 && z < box[5] + 2;
    }

    /**
     * The collision box of the border (one block outside the allowed space on every side) of the area at (x, z),
     * or null (no area there).
     */
    static double[] borderBoxAt(double x, double z) {
        for (SessionStore.Session session : active.values()) {
            if (session.state() != SessionState.IN_TEST || isGuest(session)) {
                continue;
            }
            int[] box = limits(session);
            if (insideXZ(box, x, z)) {
                return outer(box);
            }
        }
        return null;
    }

    static double[] borderBoxOf(ServerPlayer player) {
        SessionStore.Session session = active.get(player.getUUID());
        if (session == null || session.state() != SessionState.IN_TEST) {
            return null;
        }
        SessionStore.Session owner = isGuest(session) ? hostOf(session) : session;
        return outer(owner == null ? limits(session) : limits(owner));
    }

    private static double[] outer(int[] box) {
        return new double[] {box[0] - 1, box[1] - 1, box[2] - 1, box[3] + 1, box[4] + 1, box[5] + 1};
    }

    private static SessionStore.Session hostOf(SessionStore.Session guest) {
        try {
            return active.get(UUID.fromString(guest.room().getString("Host")));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Unlimited areas: remember how far the player went (plus reach) so leaving can clean it all up. */
    private static void trackUsed(ServerPlayer player, SessionStore.Session session) {
        SessionStore.Session owner = isGuest(session) ? hostOf(session) : session;
        if (owner == null || !unlimited(owner)) {
            return;
        }
        int[] used = owner.room().getIntArray(USED);
        TestArea area = area(owner);
        if (used.length != 4) {
            used = new int[] {area.markerMinX(), area.markerMinZ(), area.markerMaxX(), area.markerMaxZ()};
        }
        int x = player.getBlockX();
        int z = player.getBlockZ();
        int reach = 12;
        int[] grown = {Math.min(used[0], x - reach), Math.min(used[1], z - reach), Math.max(used[2], x + reach),
                Math.max(used[3], z + reach)};
        if (!java.util.Arrays.equals(grown, used)) {
            owner.room().putIntArray(USED, grown);
        }
    }

    /** Area cleanup also covers what an unlimited area used outside its border box. */
    private static void clearUsed(ServerLevel level, SessionStore.Session session) {
        int[] used = session.room().getIntArray(USED);
        if (used.length == 4) {
            TestAreaBuilder.clearColumns(level, used[0], used[1], used[2], used[3]);
            session.room().remove(USED);
        }
    }

    /**
     * "gamemode <mode>" for oneself (what F3+F4 / F3+N send) always works in the test world, cheats or commands
     * off: it only changes the test character. True when handled.
     */
    public static boolean quickGameMode(ServerPlayer player, String command) {
        if (!active.containsKey(player.getUUID())) {
            return false;
        }
        String[] parts = command.trim().replaceFirst("^/", "").toLowerCase(java.util.Locale.ROOT).split("\\s+");
        if (parts.length < 2 || parts.length > 3 || !parts[0].equals("gamemode")) {
            return false;
        }
        if (parts.length == 3 && !parts[2].equals("@s")
                && !parts[2].equals(player.getGameProfile().getName().toLowerCase(java.util.Locale.ROOT))) {
            return false;
        }
        net.minecraft.world.level.GameType mode = net.minecraft.world.level.GameType.byName(parts[1], null);
        if (mode == null) {
            return false;
        }
        player.setGameMode(mode);
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("commands.gamemode.success.self",
                net.minecraft.network.chat.Component.translatable("gameMode." + mode.getName())));
        return true;
    }

    /** True for a floor cell of an active test area (falling floor blocks are held there). */
    public static boolean isFloorCell(net.minecraft.core.BlockPos pos) {
        for (SessionStore.Session session : active.values()) {
            if (session.state() == SessionState.IN_TEST && !isGuest(session)
                    && TestAreaBuilder.isFloor(area(session), pos)) {
                return true;
            }
        }
        return false;
    }

    /** The active area whose border box holds (x, z), or null. */
    static TestArea areaAt(double x, double z) {
        for (SessionStore.Session session : active.values()) {
            if (session.state() != SessionState.IN_TEST || isGuest(session)) {
                continue;
            }
            if (insideXZ(limits(session), x, z)) {
                return area(session);
            }
        }
        return null;
    }

    /** The test area the player is in (owner or guest), or null. */
    static TestArea areaOf(ServerPlayer player) {
        SessionStore.Session session = active.get(player.getUUID());
        return session == null || session.state() != SessionState.IN_TEST ? null : area(session);
    }

    /** Room settings of the active area at (x, z), or null (no area there). */
    static CompoundTag roomAt(double x, double z) {
        for (SessionStore.Session session : active.values()) {
            if (session.state() != SessionState.IN_TEST || isGuest(session)) {
                continue;
            }
            if (insideXZ(limits(session), x, z)) {
                return session.room();
            }
        }
        return null;
    }

    static java.util.List<TestArea> activeAreas() {
        java.util.List<TestArea> areas = new java.util.ArrayList<>();
        for (SessionStore.Session session : active.values()) {
            if (session.state() == SessionState.IN_TEST && !isGuest(session)) {
                areas.add(area(session));
            }
        }
        return areas;
    }

    /** Marks a player carrying a test profile with the owner of the session (survives in the player's NBT). */
    private static final String OWNER_TAG = "tryitout_test_owner_";

    /**
     * Whose saved real profile belongs on this player: their own session; else the owner named by the marker
     * tag the test profile carries; else, for the single player host (whose player data is one slot in
     * level.dat, whatever the name), the newest unfinished session of the world.
     */
    private static Optional<UUID> sessionOwner(ServerPlayer player) {
        flushPending();
        UUID own = player.getUUID();
        if (pendingDone.containsKey(own)) {
            return Optional.empty(); // already restored, only the file is behind
        }
        if (store.load(own).map(session -> session.state().needsRecovery()).orElse(false)) {
            return Optional.of(own);
        }
        for (String tag : player.getTags()) {
            if (tag.startsWith(OWNER_TAG)) {
                try {
                    UUID owner = UUID.fromString(tag.substring(OWNER_TAG.length()));
                    if (store.load(owner).map(session -> session.state().needsRecovery()).orElse(false)) {
                        return Optional.of(owner);
                    }
                } catch (IllegalArgumentException ignored) {
                    // not one of ours
                }
            }
        }
        if (player.server.isSingleplayerOwner(player.getGameProfile())) {
            return store.newestNeedingRecovery();
        }
        return Optional.empty();
    }

    // ---- events ----

    /** A player joins: a session left half-way (crash, disconnect, server stop) is closed, unsaved. */
    static void onJoin(ServerPlayer player) {
        if (store == null) {
            return;
        }
        if (sessionOwner(player).isPresent()) {
            recover(player, "tryitout.test.recovered");
        } else if (TestWorld.isTestWorld(player.level())) {
            sendOut(player);
        }
    }

    /** Server stopping: put every test player back so their saved player data is the real one. */
    static void onServerStopping() {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (active.containsKey(player.getUUID())) {
                recover(player, "tryitout.test.exited");
            }
        }
    }

    /** Retries writing finished sessions whose file was locked. */
    static void flushPending() {
        if (store == null || pendingDone.isEmpty()) {
            return;
        }
        pendingDone.entrySet().removeIf(entry -> {
            try {
                store.save(entry.getKey(), entry.getValue());
                return true;
            } catch (IOException e) {
                return false;
            }
        });
    }

    static void onPlayerTick(ServerPlayer player) {
        if (store == null) {
            return;
        }
        if (!pendingDone.isEmpty() && player.server.getTickCount() % 20 == 0) {
            flushPending();
        }
        SessionStore.Session session = active.get(player.getUUID());
        boolean inTestLevel = TestWorld.isTestWorld(player.level());
        if (session == null) {
            if (inTestLevel) {
                if (sessionOwner(player).isPresent()) {
                    recover(player, "tryitout.test.recovered");
                } else {
                    sendOut(player); // came in by /tp, a portal or another mod: no profile swap, not allowed
                }
            }
            return;
        }
        if (session.state() != SessionState.IN_TEST) {
            return;
        }
        if (!inTestLevel) {
            recover(player, "tryitout.test.left_unexpectedly"); // left by death, portal or a mod
            return;
        }
        // The border is an invisible wall; only someone who still got past it (a spectator flies through blocks,
        // a mod teleport) is set back just inside, where they were, without a message.
        SessionStore.Session owner = isGuest(session) ? hostOf(session) : session;
        int[] box = owner == null ? limits(session) : limits(owner);
        if (!inside(box, player.getX(), player.getY(), player.getZ())) {
            double x = Math.max(box[0] + 0.5, Math.min(box[3] + 0.5, player.getX()));
            double y = Math.max(box[1], Math.min(box[4] - 1.0, player.getY()));
            double z = Math.max(box[2] + 0.5, Math.min(box[5] + 0.5, player.getZ()));
            player.connection.teleport(x, y, z, player.getYRot(), player.getXRot());
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            player.fallDistance = 0.0F;
        }
        if (player.tickCount % 10 == 0) {
            trackUsed(player, session);
        }
    }

    static final String QUICK_SAVE = "Quick save";

    /** Largest test area size this server allows. */
    static int largestSize() {
        int best = TestArea.SIZES[0];
        for (int size : TestArea.SIZES) {
            if (size <= TestServerConfig.get().areaSize) {
                best = size;
            }
        }
        return best;
    }

    /** Guests in an owner's area follow its new size (their area is computed from their own room copy). */
    private static void resizeGuests(UUID owner, int size) {
        for (Map.Entry<UUID, SessionStore.Session> entry : new java.util.ArrayList<>(active.entrySet())) {
            SessionStore.Session guest = entry.getValue();
            if (owner.toString().equals(guest.room().getString("Host"))) {
                guest.room().putInt("Size", size);
                try {
                    store.save(entry.getKey(), guest);
                } catch (IOException e) {
                    TryItOut.LOGGER.warn("TryItOut: could not save the guest session {}", entry.getKey(), e);
                }
            }
        }
    }

    /**
     * Floor, walls or ceiling become {@code blockId} ("" = back to the default). Only the room owner, only usable
     * blocks; cells still as built are swapped right away.
     */
    public static void setRoomBlock(ServerPlayer player, String part, String blockId) {
        SessionStore.Session session = active.get(player.getUUID());
        ServerLevel testLevel = player.server.getLevel(TestWorld.DIMENSION);
        if (session == null || session.state() != SessionState.IN_TEST || testLevel == null || isGuest(session)) {
            return;
        }
        String key = switch (part) {
            case "floor" -> RoomBlocks.FLOOR;
            case "wall" -> RoomBlocks.WALL;
            case "ceiling" -> RoomBlocks.CEILING;
            default -> null;
        };
        if (key == null) {
            return;
        }
        String id = blockId == null ? "" : blockId.trim();
        if (!id.isEmpty()) {
            ResourceLocation location = ResourceLocation.tryParse(id);
            if (location == null || !net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(location)
                    || !RoomBlocks.usable(net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(location))) {
                TestNetwork.send(player, new TestStatusPayload(true, "tryitout.test.block_not_usable"));
                return;
            }
            id = location.toString();
        }
        CompoundTag before = session.room();
        CompoundTag after = before.copy();
        after.putString(key, id);
        try {
            if (key.equals(RoomBlocks.FLOOR)) {
                TestAreaBuilder.rebuildFloor(testLevel, area(session), before, after);
            } else {
                TestAreaBuilder.rebuildStructure(testLevel, area(session), before, after);
            }
            SessionStore.Session updated = new SessionStore.Session(session.state(), session.slot(), session.real(),
                    session.realDimension(), session.x(), session.y(), session.z(), session.yaw(), session.pitch(), after);
            store.save(player.getUUID(), updated);
            active.put(player.getUUID(), updated);
            TestNetwork.sendSettings(player, settingsOf(player, updated));
        } catch (IOException | RuntimeException e) {
            TryItOut.LOGGER.error("TryItOut: changing the room block of {} failed",
                    player.getGameProfile().getName(), e);
        }
    }

    /** Someone in the test world without a session goes to their spawn point (their profile was never swapped). */
    private static void sendOut(ServerPlayer player) {
        ServerLevel overworld = player.server.overworld();
        var spawn = overworld.getSharedSpawnPos();
        teleport(player, overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), 0.0F);
        TestNetwork.send(player, new TestStatusPayload(false, "tryitout.test.kicked_back"));
    }

    // ---- helpers ----

    private static TestArea area(SessionStore.Session session) {
        return TestArea.forSlot(session.slot(), session.size());
    }

    private static ServerLevel levelOrOverworld(String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        if (id != null) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
            if (level != null && !TestWorld.isTestWorld(level)) {
                return level;
            }
        }
        return server.getLevel(Level.OVERWORLD);
    }

    private static void teleport(ServerPlayer player, ServerLevel level, double x, double y, double z, float yaw,
            float pitch) {
        // Our own moves are the only ones allowed into or out of the test dimension.
        TestWorld.allowDimensionChange(() -> player.teleportTo(level, x, y, z, yaw, pitch));
    }

    /** Other players' tab list shows the new game mode. */
    private static void broadcastGameMode(ServerPlayer player) {
        player.server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE, player));
    }
}
