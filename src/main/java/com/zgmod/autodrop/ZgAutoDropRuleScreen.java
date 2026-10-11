package com.zgmod.autodrop;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ZgAutoDropRuleScreen extends Screen {

    private final Screen parent;
    private int scrollY = 0;
    private int contentHeight = 0;
    private boolean showSchemeList = false;

    public ZgAutoDropRuleScreen(Screen parent) {
        super(Component.literal("zgautodrop 规则"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = 40 - scrollY;

        // 方案栏
        this.addRenderableWidget(Button.builder(
                Component.literal("§6方案: " + ZgAutoDropConfig.activeScheme + " ▶"),
                b -> {
                    showSchemeList = !showSchemeList;
                    this.rebuildWidgets();
                }
        ).bounds(cx - 200, y, 200, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("§a新建"),
                b -> {
                    String name = "scheme" + (ZgAutoDropConfig.schemes.size());
                    ZgAutoDropConfig.schemes.put(name, new ZgAutoDropConfig.Scheme());
                    ZgAutoDropConfig.save();
                    this.rebuildWidgets();
                }
        ).bounds(cx + 10, y, 90, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("§c删除当前"),
                b -> {
                    if (!ZgAutoDropConfig.activeScheme.equals("default")) {
                        ZgAutoDropConfig.schemes.remove(ZgAutoDropConfig.activeScheme);
                        ZgAutoDropConfig.activeScheme = "default";
                        ZgAutoDropConfig.save();
                        this.rebuildWidgets();
                    }
                }
        ).bounds(cx + 110, y, 90, 20).build());
        y += 25;

        if (showSchemeList) {
            for (String name : new ArrayList<>(ZgAutoDropConfig.schemes.keySet())) {
                final String schemeName = name;
                String prefix = name.equals(ZgAutoDropConfig.activeScheme) ? "§a▶ " : "§7  ";
                this.addRenderableWidget(Button.builder(
                        Component.literal(prefix + name),
                        b -> {
                            ZgAutoDropConfig.activeScheme = schemeName;
                            ZgAutoDropConfig.save();
                            showSchemeList = false;
                            this.rebuildWidgets();
                        }
                ).bounds(cx - 200, y, 400, 20).build());
                y += 22;
            }
            y += 10;
        }

        // 新规则输入
        EditBox ruleBox = new EditBox(this.font, cx - 200, y, 400, 20, Component.literal("rule"));
        ruleBox.setHint(Component.literal("名字 或 路径=值，例如: *宝石* 或 type=minecraft:diamond"));
        this.addRenderableWidget(ruleBox);
        y += 25;

        this.addRenderableWidget(Button.builder(Component.literal("加黑名单"), b -> {
            if (!ruleBox.getValue().isEmpty()) {
                ZgAutoDropConfig.currentScheme().dropRules.add(ruleBox.getValue());
                ZgAutoDropConfig.save();
                ruleBox.setValue("");
                this.rebuildWidgets();
            }
        }).bounds(cx - 100, y, 95, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("加白名单"), b -> {
            if (!ruleBox.getValue().isEmpty()) {
                ZgAutoDropConfig.currentScheme().keepRules.add(ruleBox.getValue());
                ZgAutoDropConfig.save();
                ruleBox.setValue("");
                this.rebuildWidgets();
            }
        }).bounds(cx + 5, y, 95, 20).build());
        y += 30;

        // 黑名单
        ZgAutoDropConfig.Scheme scheme = ZgAutoDropConfig.currentScheme();
        List<String> dropList = scheme.dropRules;
        this.addRenderableWidget(Button.builder(
                Component.literal("§c黑名单 (" + dropList.size() + ")"),
                b -> {}
        ).bounds(cx - 200, y, 400, 20).build());
        y += 22;

        for (int i = 0; i < dropList.size(); i++) {
            final int idx = i;
            String ruleText = dropList.get(i);
            if (ruleText.length() > 35) ruleText = ruleText.substring(0, 35) + "...";

            this.addRenderableWidget(Button.builder(Component.literal("§7" + ruleText), b -> {})
                    .bounds(cx - 200, y, 340, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("§c删除"), b -> {
                ZgAutoDropConfig.currentScheme().dropRules.remove(idx);
                ZgAutoDropConfig.save();
                this.rebuildWidgets();
            }).bounds(cx + 145, y, 55, 20).build());
            y += 22;
        }

        y += 10;

        // 白名单
        List<String> keepList = scheme.keepRules;
        this.addRenderableWidget(Button.builder(
                Component.literal("§a白名单 (" + keepList.size() + ")"),
                b -> {}
        ).bounds(cx - 200, y, 400, 20).build());
        y += 22;

        for (int i = 0; i < keepList.size(); i++) {
            final int idx = i;
            String ruleText = keepList.get(i);
            if (ruleText.length() > 35) ruleText = ruleText.substring(0, 35) + "...";

            this.addRenderableWidget(Button.builder(Component.literal("§7" + ruleText), b -> {})
                    .bounds(cx - 200, y, 340, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("§c删除"), b -> {
                ZgAutoDropConfig.currentScheme().keepRules.remove(idx);
                ZgAutoDropConfig.save();
                this.rebuildWidgets();
            }).bounds(cx + 145, y, 55, 20).build());
            y += 22;
        }

        // 返回
        this.addRenderableWidget(Button.builder(Component.literal("返回"), b -> {
            this.minecraft.setScreen(parent);//26.1.2
            //this.minecraft.gui.setScreen(parent);//26.2
        }).bounds(cx - 50, this.height - 30, 100, 20).build());

        contentHeight = y + scrollY - 40;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int viewHeight = this.height - 80;
        int maxScroll = Math.max(0, contentHeight - viewHeight);

        scrollY -= (int) (verticalAmount * 20);
        if (scrollY < 0) scrollY = 0;
        if (scrollY > maxScroll) scrollY = maxScroll;

        this.rebuildWidgets();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}