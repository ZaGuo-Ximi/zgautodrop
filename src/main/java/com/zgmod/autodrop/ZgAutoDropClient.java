package com.zgmod.autodrop;

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

    @Override
    public void onInitializeClient() {
        ZgAutoDropConfig.load();

        // ===== 按键分类 =====
        KeyMapping.Category CATEGORY = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("zgautodrop", "custom_category")
        );

        // 开关键（默认 H）
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.zgautodrop.toggle",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                CATEGORY
        ));

        // 打开配置键（默认 J）
        configKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.zgautodrop.config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                CATEGORY
        ));

        // ===== Tick 事件 =====
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 按 H 切换自动丢物品
            while (toggleKey.consumeClick()) {
                ZgAutoDropConfig.autoDropEnabled = !ZgAutoDropConfig.autoDropEnabled;
                if (client.player != null) {
                    client.player.sendSystemMessage(Component.literal(
                            "§e[zgautodrop] 自动丢物品: " + (ZgAutoDropConfig.autoDropEnabled ? "§a开" : "§c关")));
                }
            }

            // 按 J 打开配置界面
            while (configKey.consumeClick()) {
                //client.gui.setScreen(new ZgAutoDropRuleScreen(null));//26.2
                client.setScreen(new ZgAutoDropRuleScreen(null));//26.1.2
            }

            // 每 20 tick 扫一次背包
            dropTimer++;
            if (dropTimer >= 20) {
                dropTimer = 0;
                ZgAutoDropDrop.doAutoDrop(client);
            }
        });

        // ===== 命令 =====
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
                    .then(ClientCommands.literal("list")
                            .executes(ctx -> {
                                var scheme = ZgAutoDropConfig.currentScheme();
                                ctx.getSource().sendFeedback(Component.literal("§6===== 方案: " + ZgAutoDropConfig.activeScheme + " ====="));
                                ctx.getSource().sendFeedback(Component.literal("§e状态: " + (ZgAutoDropConfig.autoDropEnabled ? "§a开" : "§c关")));
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