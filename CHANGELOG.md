# Changelog / 更新日志

## 1.1.0

The client mod is optional now. / 客户端可以不装了。

- Players without the mod (vanilla clients too) can join, see the blocks and items as vanilla stand-ins and use them: block dialogs, selectors with particle outlines, the portal pickaxe.
  没装模组的玩家（包括原版客户端）也能进服：方块和物品显示成原版替身，右键方块是对话框界面，选区器用粒子显示选区，传送门挖掘镐照常能用。
- Messages and item names reach them in their language (Chinese or English).
  提示和物品名字按他们的客户端语言显示（中文或英文）。
- Players with the mod see and use everything as before; the versions still have to match.
  装了模组的玩家和以前一样，版本仍然要和服务端一致。

## 1.0.0

First release. / 第一个版本。

- Portal Cutter Block, Light Suppressor Block, the two selectors and the Portal Pickaxe.
  切门方块、光抑方块、两种选区器和传送门挖掘镐。
- Zones by anvil binding, GUI import or typed coordinates; zone boxes drawn in the world.
  区域可以用铁砧绑定、界面导入或手动输入坐标设置，世界里会画出区域线框。
- Light suppressor modes: untouched / lock current / fixed, with presets; chunks that were not loaded are recorded when they load.
  光抑模式：不干预 / 锁定当前 / 固定值，附预设；当时没加载的区块在加载时补记。
- Server settings for break time and pickaxe time, synced to clients; Mod Menu screen; `/easysuppressions` command.
  破坏时间和挖掘镐时间的服务端设置（同步给客户端）、Mod Menu 设置界面、`/easysuppressions` 命令。
- Clients without the mod, or with another version, get a clear message at login.
  没装模组或版本不一致的客户端登录时会收到清楚的提示。
