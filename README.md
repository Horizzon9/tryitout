# Test Room

**Try any block or item without leaving your world.** Hover it in your inventory, JEI or EMI, hold `W`, and a small
interactive room opens on top of your game. With the mod on the server too, a separate **Test World** dimension
gives you a second character and real game mechanics, while your real world stays untouched.

Minecraft 1.21.1 · NeoForge 21.1+ · Beta · requires [Architectury API](https://modrinth.com/mod/architectury-api)

## Features

- **Pocket Scene** (client-side, works on servers without the mod): place, break and use blocks, light and day/night,
  virtual redstone power, weapon mode with a training dummy, free camera, floors up to 101x101.
- **Test World** (mod on the server too): own inventory, ender chest and profile, room settings, training dummies,
  save/load, material list, friend invites, unlimited area, server-side crash protection.
- **Optional integrations:** Create / Ponder (open a Ponder scene as a pocket scene or test world), Create Aeronautics
  (ships keep their physics in the Test World), Immersive Engineering (shows a whole unformed multiblock), JEI and EMI.
  None of them is a dependency.

## Building

Needs JDK 21.

```
./gradlew build
```

The mod jars are written to `neoforge/build/libs/` and `fabric/build/libs/`. Only the NeoForge build is released for now.

Dev clients (the optional mods are never dependencies, they are only added to the dev run):

```
./gradlew :neoforge:runClient
./gradlew :neoforge:runClient -Pdev_create=true -Pdev_aero=true -Pdev_mekanism=true -Pdev_ie=true
```

## Layout

- `common/` shared code (scene, rendering, test world, networking, screens) and the shared tests
- `neoforge/` and `fabric/` loader entry points and loader-specific mixins
- the mod id is `pocketsandbox`

## License

[Mozilla Public License 2.0](LICENSE)
