package com.zgmod.autodrop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ZgAutoDropRuleScreen extends Screen {

    private final Screen parent;
    private int scrollY = 0;
    private int contentHeight = 0;
    private boolean showSchemeList = false;
    private String searchFilter = "";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public ZgAutoDropRuleScreen(Screen parent) {
        super(Component.literal("zgautodrop 规则"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y = 40 - scrollY;

        // ===== 方案栏 =====
        this.addRenderableWidget(Button.builder(
                Component.literal("§6方案: " + ZgAutoDropConfig.activeScheme + " ▶"),
                b -> {
                    showSchemeList = !showSchemeList;
                    this.rebuildWidgets();
                }
        ).bounds(cx - 200, y, 130, 20).build());

        EditBox schemeNameBox = new EditBox(this.font, cx - 60, y, 110, 20, Component.literal("name"));
        schemeNameBox.setHint(Component.literal("方案名"));
        this.addRenderableWidget(schemeNameBox);

        this.addRenderableWidget(Button.builder(Component.literal("§a新建"), b -> {
            String name = schemeNameBox.getValue().trim();
            if (name.isEmpty()) name = "scheme" + (ZgAutoDropConfig.schemes.size());
            if (ZgAutoDropConfig.schemes.containsKey(name)) return;
            ZgAutoDropConfig.schemes.put(name, new ZgAutoDropConfig.Scheme());
            ZgAutoDropConfig.save();
            schemeNameBox.setValue("");
            this.rebuildWidgets();
        }).bounds(cx + 55, y, 60, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("§c删除"), b -> {
            if (!ZgAutoDropConfig.activeScheme.equals("default")) {
                ZgAutoDropConfig.schemes.remove(ZgAutoDropConfig.activeScheme);
                ZgAutoDropConfig.activeScheme = "default";
                ZgAutoDropConfig.save();
                this.rebuildWidgets();
            }
        }).bounds(cx + 120, y, 60, 20).build());

        // ===== 帮助按钮 =====
        this.addRenderableWidget(Button.builder(
                Component.literal("§e?"),
                b -> {
                    if (this.minecraft.player != null) {
                        this.minecraft.player.sendSystemMessage(Component.literal("§6===== ZgAutoDrop 使用帮助 ====="));
                        this.minecraft.player.sendSystemMessage(Component.literal("§e规则格式: §f路径=值 §7或 §f直接填名字"));
                        this.minecraft.player.sendSystemMessage(Component.literal("§e路径示例:"));
                        this.minecraft.player.sendSystemMessage(Component.literal("  §7display.Name §f- 物品显示名"));
                        this.minecraft.player.sendSystemMessage(Component.literal("  §7lore §f- 物品描述"));
                        this.minecraft.player.sendSystemMessage(Component.literal("  §7type §f- 物品类型，如 minecraft:diamond"));
                        this.minecraft.player.sendSystemMessage(Component.literal("  §7custom_data.xxx.yyy §f- NBT 深层路径"));

                        this.minecraft.player.sendSystemMessage(Component.literal("§6--- 通配符模式 (§f默认§6) ---"));
                        this.minecraft.player.sendSystemMessage(Component.literal("  §f*宝石* §7- 名字含\"宝石\""));
                        this.minecraft.player.sendSystemMessage(Component.literal("  §f*碎片 §7- 名字以\"碎片\"结尾"));

                        this.minecraft.player.sendSystemMessage(Component.literal("§6--- 正则模式 (§f点匹配按钮切换§6) ---"));
                        this.minecraft.player.sendSystemMessage(Component.literal("  §f^传说.* §7- 名字以\"传说\"开头"));
                        this.minecraft.player.sendSystemMessage(Component.literal("  §f.*碎片$ §7- 名字以\"碎片\"结尾"));
                        this.minecraft.player.sendSystemMessage(Component.literal("  §f.*\\d+.* §7- 名字里含数字"));
                        this.minecraft.player.sendSystemMessage(Component.literal("  §f第\\d+层.* §7- 名字是\"第X层\"格式"));
                        this.minecraft.player.sendSystemMessage(Component.literal("  §f.*<颜色代码>.* §7- 名字含颜色代码"));
                        this.minecraft.player.sendSystemMessage(Component.literal("  §f(钻石|铁|金)剑 §7- 钻石/铁/金剑"));
                        this.minecraft.player.sendSystemMessage(Component.literal("  §f(rare|epic|legendary) §7- 三种品质之一"));
                        this.minecraft.player.sendSystemMessage(Component.literal("  §f^第\\d+层.*$ §7- 名字完全等于\"第X层\""));

                        this.minecraft.player.sendSystemMessage(Component.literal("§6--- 黑白名单 ---"));
                        this.minecraft.player.sendSystemMessage(Component.literal("  §f白名单优先，匹配白名单的不丢"));
                        this.minecraft.player.sendSystemMessage(Component.literal("  §f例: 黑名单填 §7* §f，白名单填 §7*宝石*"));
                        this.minecraft.player.sendSystemMessage(Component.literal("  §f→ 除宝石外全丢"));
                    }
                }
        ).bounds(cx + 200, y, 20, 20).build());
        y += 25;

        // 方案列表
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

        // ===== 新规则输入 =====
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

        // ===== 搜索框 =====
        EditBox searchBox = new EditBox(this.font, cx - 200, y, 400, 20, Component.literal("search"));
        searchBox.setHint(Component.literal("搜索名单..."));
        searchBox.setValue(searchFilter);
        searchBox.setResponder(text -> {
            searchFilter = text;
            this.rebuildWidgets();
        });
        this.addRenderableWidget(searchBox);
        y += 25;

        // ===== 导出 / 导入按钮 =====
        this.addRenderableWidget(Button.builder(
                Component.literal("§a导出方案"),
                b -> {
                    try {
                        Path path = FabricLoader.getInstance().getConfigDir().resolve("zgautodrop_export.json");
                        Files.writeString(path, GSON.toJson(ZgAutoDropConfig.currentScheme()));
                        if (this.minecraft.player != null) {
                            this.minecraft.player.sendSystemMessage(Component.literal(
                                    "§a已导出方案到: .minecraft/config/zgautodrop_export.json"));
                        }
                    } catch (Exception e) {
                        if (this.minecraft.player != null) {
                            this.minecraft.player.sendSystemMessage(Component.literal("§c导出失败: " + e.getMessage()));
                        }
                    }
                }
        ).bounds(cx - 200, y, 130, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("§a导入方案"),
                b -> {
                    try {
                        Path path = FabricLoader.getInstance().getConfigDir().resolve("zgautodrop_export.json");
                        if (!Files.exists(path)) {
                            if (this.minecraft.player != null) {
                                this.minecraft.player.sendSystemMessage(Component.literal("§c找不到导出文件"));
                            }
                            return;
                        }
                        String json = Files.readString(path);
                        ZgAutoDropConfig.Scheme scheme = new Gson().fromJson(json, ZgAutoDropConfig.Scheme.class);
                        if (scheme != null) {
                            ZgAutoDropConfig.currentScheme().dropRules = scheme.dropRules;
                            ZgAutoDropConfig.currentScheme().keepRules = scheme.keepRules;
                            ZgAutoDropConfig.save();
                            if (this.minecraft.player != null) {
                                this.minecraft.player.sendSystemMessage(Component.literal(
                                        "§a已导入方案到: " + ZgAutoDropConfig.activeScheme));
                            }
                            this.rebuildWidgets();
                        }
                    } catch (Exception e) {
                        if (this.minecraft.player != null) {
                            this.minecraft.player.sendSystemMessage(Component.literal("§c导入失败: " + e.getMessage()));
                        }
                    }
                }
        ).bounds(cx + 70, y, 130, 20).build());
        y += 30;

        // ===== 丢物品模式切换 =====
        this.addRenderableWidget(Button.builder(
                Component.literal("丢物品模式: " + (ZgAutoDropConfig.dropWholeStack ? "整组" : "单个")),
                b -> {
                    ZgAutoDropConfig.dropWholeStack = !ZgAutoDropConfig.dropWholeStack;
                    ZgAutoDropConfig.save();
                    b.setMessage(Component.literal("丢物品模式: " + (ZgAutoDropConfig.dropWholeStack ? "整组" : "单个")));
                }
        ).bounds(cx - 200, y, 130, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("匹配: " + (ZgAutoDropConfig.useRegex ? "正则" : "通配符")),
                b -> {
                    ZgAutoDropConfig.useRegex = !ZgAutoDropConfig.useRegex;
                    ZgAutoDropConfig.save();
                    b.setMessage(Component.literal("匹配: " + (ZgAutoDropConfig.useRegex ? "正则" : "通配符")));
                }
        ).bounds(cx + 70, y, 130, 20).build());
        y += 30;

        // ===== 黑名单 =====
        ZgAutoDropConfig.Scheme scheme = ZgAutoDropConfig.currentScheme();
        List<String> dropList = scheme.dropRules;
        List<Integer> dropIndices = new ArrayList<>();
        for (int i = 0; i < dropList.size(); i++) {
            if (searchFilter.isEmpty() || dropList.get(i).toLowerCase().contains(searchFilter.toLowerCase())) {
                dropIndices.add(i);
            }
        }

        this.addRenderableWidget(Button.builder(
                Component.literal("§c黑名单 (" + dropList.size() + ")"),
                b -> {}
        ).bounds(cx - 200, y, 400, 20).build());
        y += 22;

        for (int idx : dropIndices) {
            String ruleText = dropList.get(idx);
            if (ruleText.length() > 35) ruleText = ruleText.substring(0, 35) + "...";
            final int finalIdx = idx;

            this.addRenderableWidget(Button.builder(Component.literal("§7" + ruleText), b -> {})
                    .bounds(cx - 200, y, 340, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("§c删除"), b -> {
                ZgAutoDropConfig.currentScheme().dropRules.remove(finalIdx);
                ZgAutoDropConfig.save();
                this.rebuildWidgets();
            }).bounds(cx + 145, y, 55, 20).build());
            y += 22;
        }

        y += 10;

        // ===== 白名单 =====
        List<String> keepList = scheme.keepRules;
        List<Integer> keepIndices = new ArrayList<>();
        for (int i = 0; i < keepList.size(); i++) {
            if (searchFilter.isEmpty() || keepList.get(i).toLowerCase().contains(searchFilter.toLowerCase())) {
                keepIndices.add(i);
            }
        }

        this.addRenderableWidget(Button.builder(
                Component.literal("§a白名单 (" + keepList.size() + ")"),
                b -> {}
        ).bounds(cx - 200, y, 400, 20).build());
        y += 22;

        for (int idx : keepIndices) {
            String ruleText = keepList.get(idx);
            if (ruleText.length() > 35) ruleText = ruleText.substring(0, 35) + "...";
            final int finalIdx = idx;

            this.addRenderableWidget(Button.builder(Component.literal("§7" + ruleText), b -> {})
                    .bounds(cx - 200, y, 340, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("§c删除"), b -> {
                ZgAutoDropConfig.currentScheme().keepRules.remove(finalIdx);
                ZgAutoDropConfig.save();
                this.rebuildWidgets();
            }).bounds(cx + 145, y, 55, 20).build());
            y += 22;
        }

        // ===== 返回（跟着内容滚） =====
        y += 10;
        this.addRenderableWidget(Button.builder(Component.literal("返回"), b -> {
            this.minecraft.gui.setScreen(parent);//26.2
        }).bounds(cx - 50, y, 100, 20).build());
        y += 25;

        contentHeight = y + scrollY - 40;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int viewHeight = this.height - 60;
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