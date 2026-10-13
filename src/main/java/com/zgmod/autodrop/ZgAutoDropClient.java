package com.zgmod.autodrop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class ZgAutoDropClient implements ClientModInitializer {

    private static KeyMapping toggleKey;
    private static KeyMapping configKey;
    private static int dropTimer = 0;
    private static final Gson GSON = new GsonBuilder().create();

    @Override
    public void onInitializeClient() {
        ZgAutoDropConfig.load();

        KeyMapping.Category CATEGORY = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("zgautodrop", "custom_category")
        );

        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.zgautodrop.toggle",
                InputConstants.Type.KEYSYM,  // 26.2 用 KEYSYM
                GLFW.GLFW_KEY_H,
                CATEGORY
        ));

        configKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.zgautodrop.config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.consumeClick()) {
                ZgAutoDropConfig.autoDropEnabled = !ZgAutoDropConfig.autoDropEnabled;
                if (client.player != null) {
                    client.player.sendSystemMessage(Component.literal(
                            "§e[zgautodrop] 自动丢物品: " + (ZgAutoDropConfig.autoDropEnabled ? "§a开" : "§c关")));
                }
            }

            while (configKey.consumeClick()) {
                client.gui.setScreen(new ZgAutoDropRuleScreen(null));
            }

            dropTimer++;
            if (dropTimer >= 20) {
                dropTimer = 0;
                ZgAutoDropDrop.doAutoDrop(client);
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("zgdrop")
                    .then(ClientCommands.literal("on")
                            .executes(ctx -> {
                                ZgAutoDropConfig.autoDropEnabled = true;
                                ctx.getSource().sendFeedback(Component.literal("§a自动丢物品: 开"));
                                return 1;
                            }))
                    .then(ClientCommands.literal("off")
                            .executes(ctx -> {
                                ZgAutoDropConfig.autoDropEnabled = false;
                                ctx.getSource().sendFeedback(Component.literal("§a自动丢物品: 关"));
                                return 1;
                            }))
                    .then(ClientCommands.literal("add")
                            .then(ClientCommands.argument("rule", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        String rule = StringArgumentType.getString(ctx, "rule");
                                        ZgAutoDropConfig.currentScheme().dropRules.add(rule);
                                        ZgAutoDropConfig.save();
                                        ctx.getSource().sendFeedback(
                                                Component.literal("§a[" + ZgAutoDropConfig.activeScheme + "] 已加黑名单: " + rule));
                                        return 1;
                                    })))
                    .then(ClientCommands.literal("keep")
                            .then(ClientCommands.argument("rule", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        String rule = StringArgumentType.getString(ctx, "rule");
                                        ZgAutoDropConfig.currentScheme().keepRules.add(rule);
                                        ZgAutoDropConfig.save();
                                        ctx.getSource().sendFeedback(
                                                Component.literal("§a[" + ZgAutoDropConfig.activeScheme + "] 已加白名单: " + rule));
                                        return 1;
                                    })))
                    .then(ClientCommands.literal("remove")
                            .then(ClientCommands.argument("index", IntegerArgumentType.integer(0))
                                    .executes(ctx -> {
                                        int idx = IntegerArgumentType.getInteger(ctx, "index");
                                        var scheme = ZgAutoDropConfig.currentScheme();
                                        if (idx < scheme.dropRules.size()) {
                                            String removed = scheme.dropRules.remove(idx);
                                            ZgAutoDropConfig.save();
                                            ctx.getSource().sendFeedback(
                                                    Component.literal("§a已删规则: " + removed));
                                        } else {
                                            ctx.getSource().sendFeedback(
                                                    Component.literal("§c索引超出范围"));
                                        }
                                        return 1;
                                    })))
                    .then(ClientCommands.literal("mode")
                            .executes(ctx -> {
                                ZgAutoDropConfig.dropWholeStack = !ZgAutoDropConfig.dropWholeStack;
                                ZgAutoDropConfig.save();
                                ctx.getSource().sendFeedback(Component.literal(
                                        "§a丢物品模式: " + (ZgAutoDropConfig.dropWholeStack ? "整组" : "单个")));
                                return 1;
                            }))
                    .then(ClientCommands.literal("regex")
                            .executes(ctx -> {
                                ZgAutoDropConfig.useRegex = !ZgAutoDropConfig.useRegex;
                                ZgAutoDropConfig.save();
                                ctx.getSource().sendFeedback(Component.literal(
                                        "§a匹配模式: " + (ZgAutoDropConfig.useRegex ? "正则" : "通配符")));
                                return 1;
                            }))
                    .then(ClientCommands.literal("export")
                            .executes(ctx -> {
                                try {
                                    java.nio.file.Path path = net.fabricmc.loader.api.FabricLoader.getInstance()
                                            .getConfigDir().resolve("zgautodrop_export.json");
                                    java.nio.file.Files.writeString(path, new com.google.gson.GsonBuilder().setPrettyPrinting().create()
                                            .toJson(ZgAutoDropConfig.currentScheme()));
                                    ctx.getSource().sendFeedback(Component.literal(
                                            "§a已导出方案到: .minecraft/config/zgautodrop_export.json"));
                                } catch (Exception e) {
                                    ctx.getSource().sendFeedback(Component.literal("§c导出失败: " + e.getMessage()));
                                }
                                return 1;
                            }))
                    .then(ClientCommands.literal("import")
                            .executes(ctx -> {
                                try {
                                    java.nio.file.Path path = net.fabricmc.loader.api.FabricLoader.getInstance()
                                            .getConfigDir().resolve("zgautodrop_export.json");
                                    if (!java.nio.file.Files.exists(path)) {
                                        ctx.getSource().sendFeedback(Component.literal("§c找不到导出文件"));
                                        return 1;
                                    }
                                    String json = java.nio.file.Files.readString(path);
                                    ZgAutoDropConfig.Scheme scheme = GSON.fromJson(json, ZgAutoDropConfig.Scheme.class);
                                    if (scheme != null) {
                                        ZgAutoDropConfig.currentScheme().dropRules = scheme.dropRules;
                                        ZgAutoDropConfig.currentScheme().keepRules = scheme.keepRules;
                                        ZgAutoDropConfig.save();
                                        ctx.getSource().sendFeedback(
                                                Component.literal("§a已导入方案到: " + ZgAutoDropConfig.activeScheme));
                                    }
                                } catch (Exception e) {
                                    ctx.getSource().sendFeedback(
                                            Component.literal("§c导入失败: " + e.getMessage()));
                                }
                                return 1;
                            }))
                    .then(ClientCommands.literal("list")
                            .executes(ctx -> {
                                var scheme = ZgAutoDropConfig.currentScheme();
                                ctx.getSource().sendFeedback(Component.literal("§6===== 方案: " + ZgAutoDropConfig.activeScheme + " ====="));
                                ctx.getSource().sendFeedback(Component.literal("§e状态: " + (ZgAutoDropConfig.autoDropEnabled ? "§a开" : "§c关")));
                                ctx.getSource().sendFeedback(Component.literal("§e模式: " + (ZgAutoDropConfig.dropWholeStack ? "整组" : "单个")));
                                ctx.getSource().sendFeedback(Component.literal("§e匹配: " + (ZgAutoDropConfig.useRegex ? "正则" : "通配符")));
                                ctx.getSource().sendFeedback(Component.literal("§e黑名单:"));
                                for (int i = 0; i < scheme.dropRules.size(); i++) {
                                    ctx.getSource().sendFeedback(
                                            Component.literal("  §7[" + i + "] §f" + scheme.dropRules.get(i)));
                                }
                                ctx.getSource().sendFeedback(Component.literal("§e白名单:"));
                                for (int i = 0; i < scheme.keepRules.size(); i++) {
                                    ctx.getSource().sendFeedback(
                                            Component.literal("  §7[" + i + "] §f" + scheme.keepRules.get(i)));
                                }
                                return 1;
                            }))
                    .then(ClientCommands.literal("scheme")
                            .then(ClientCommands.literal("list")
                                    .executes(ctx -> {
                                        ctx.getSource().sendFeedback(Component.literal("§6===== 所有方案 ====="));
                                        for (String name : ZgAutoDropConfig.schemes.keySet()) {
                                            String mark = name.equals(ZgAutoDropConfig.activeScheme) ? "§a▶ " : "§7  ";
                                            ctx.getSource().sendFeedback(Component.literal(mark + name));
                                        }
                                        return 1;
                                    }))
                            .then(ClientCommands.literal("create")
                                    .then(ClientCommands.argument("name", StringArgumentType.word())
                                            .executes(ctx -> {
                                                String name = StringArgumentType.getString(ctx, "name");
                                                if (ZgAutoDropConfig.schemes.containsKey(name)) {
                                                    ctx.getSource().sendFeedback(
                                                            Component.literal("§c方案已存在: " + name));
                                                } else {
                                                    ZgAutoDropConfig.schemes.put(name, new ZgAutoDropConfig.Scheme());
                                                    ZgAutoDropConfig.save();
                                                    ctx.getSource().sendFeedback(
                                                            Component.literal("§a已创建方案: " + name));
                                                }
                                                return 1;
                                            })))
                            .then(ClientCommands.literal("switch")
                                    .then(ClientCommands.argument("name", StringArgumentType.word())
                                            .executes(ctx -> {
                                                String name = StringArgumentType.getString(ctx, "name");
                                                if (ZgAutoDropConfig.schemes.containsKey(name)) {
                                                    ZgAutoDropConfig.activeScheme = name;
                                                    ZgAutoDropConfig.save();
                                                    ctx.getSource().sendFeedback(
                                                            Component.literal("§a已切换到方案: " + name));
                                                } else {
                                                    ctx.getSource().sendFeedback(
                                                            Component.literal("§c方案不存在: " + name));
                                                }
                                                return 1;
                                            })))
                            .then(ClientCommands.literal("delete")
                                    .then(ClientCommands.argument("name", StringArgumentType.word())
                                            .executes(ctx -> {
                                                String name = StringArgumentType.getString(ctx, "name");
                                                if (name.equals("default")) {
                                                    ctx.getSource().sendFeedback(
                                                            Component.literal("§c不能删除默认方案"));
                                                } else if (ZgAutoDropConfig.schemes.remove(name) != null) {
                                                    if (ZgAutoDropConfig.activeScheme.equals(name)) {
                                                        ZgAutoDropConfig.activeScheme = "default";
                                                    }
                                                    ZgAutoDropConfig.save();
                                                    ctx.getSource().sendFeedback(
                                                            Component.literal("§a已删除方案: " + name));
                                                } else {
                                                    ctx.getSource().sendFeedback(
                                                            Component.literal("§c方案不存在: " + name));
                                                }
                                                return 1;
                                            }))))
            );
        });
    }
}