package com.zgmod.autodrop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class ZgAutoDropDrop {

    public static boolean shouldDrop(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ZgAutoDropConfig.Scheme scheme = ZgAutoDropConfig.currentScheme();

        for (String rule : scheme.keepRules) {
            if (matchesRule(stack, rule)) return false;
        }
        for (String rule : scheme.dropRules) {
            if (matchesRule(stack, rule)) return true;
        }
        return false;
    }

    private static boolean matchesRule(ItemStack stack, String rule) {
        if (rule == null || rule.isEmpty()) return false;

        String path;
        String expected;

        int eq = rule.indexOf('=');
        if (eq < 0) {
            path = "display.Name";
            expected = rule.trim();
        } else {
            path = rule.substring(0, eq).trim();
            expected = rule.substring(eq + 1).trim();
        }

        String actual = getValueByPath(stack, path);
        if (actual == null) return false;

        // 正则模式
        if (ZgAutoDropConfig.useRegex) {
            try {
                return Pattern.compile(expected).matcher(actual).find();
            } catch (PatternSyntaxException e) {
                return false;
            }
        }

        // 通配符模式
        return wildcardMatch(actual, expected);
    }

    private static String getValueByPath(ItemStack stack, String path) {
        if (path.equals("type")) {
            return stack.getItem().toString();
        }
        if (path.equals("display.Name") || path.equals("display.name")) {
            Component name = stack.get(DataComponents.CUSTOM_NAME);
            return name != null ? name.getString() : stack.getHoverName().getString();
        }
        if (path.equals("lore")) {
            var lore = stack.get(DataComponents.LORE);
            if (lore == null) return "";
            StringBuilder sb = new StringBuilder();
            for (Component line : lore.lines()) {
                sb.append(line.getString()).append("\n");
            }
            return sb.toString();
        }

        if (path.startsWith("custom_data.")) {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData == null) return null;
            CompoundTag nbt = customData.copyTag();
            String[] parts = path.substring("custom_data.".length()).split("\\.");
            Tag current = nbt;
            for (String part : parts) {
                if (!(current instanceof CompoundTag compound)) return null;
                current = compound.get(part);
                if (current == null) return null;
            }
            return current.asString().orElse("");
        }

        return null;
    }

    private static boolean wildcardMatch(String actual, String pattern) {
        if (pattern.equals("*")) return true;
        if (!pattern.contains("*")) return actual.equals(pattern);

        String[] parts = pattern.split("\\*", -1);
        int idx = 0;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;
            int found = actual.indexOf(part, idx);
            if (found < 0) return false;
            if (i == 0 && !pattern.startsWith("*") && found != 0) return false;
            idx = found + part.length();
        }
        if (!pattern.endsWith("*") && idx != actual.length()) return false;
        return true;
    }

    public static boolean tryDrop(Minecraft client, ItemStack stack) {
        if (client.player == null || client.gameMode == null) return false;
        LocalPlayer player = client.player;

        for (int i = 0; i < player.containerMenu.slots.size(); i++) {
            var slot = player.containerMenu.slots.get(i);
            if (!slot.hasItem()) continue;
            ItemStack slotStack = slot.getItem();
            if (!ItemStack.isSameItemSameComponents(slotStack, stack)) continue;

            try {
                if (!ZgAutoDropConfig.dropWholeStack && slotStack.getCount() > 1) {
                    client.gameMode.handleContainerInput(
                            player.containerMenu.containerId,
                            slot.index,
                            0,
                            ContainerInput.PICKUP,
                            player
                    );
                    client.gameMode.handleContainerInput(
                            player.containerMenu.containerId,
                            -999,
                            0,
                            ContainerInput.THROW,
                            player
                    );
                } else {
                    client.gameMode.handleContainerInput(
                            player.containerMenu.containerId,
                            slot.index,
                            1,
                            ContainerInput.THROW,
                            player
                    );
                }
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
            return true;
        }
        return false;
    }

    public static void doAutoDrop(Minecraft client) {
        if (!ZgAutoDropConfig.autoDropEnabled) return;
        LocalPlayer player = client.player;
        if (player == null) return;
        if (client.gameMode == null) return;

        for (int i = 0; i < player.containerMenu.slots.size(); i++) {
            var slot = player.containerMenu.slots.get(i);
            if (!slot.hasItem()) continue;
            ItemStack stack = slot.getItem();
            if (shouldDrop(stack)) {
                tryDrop(client, stack);
                return;
            }
        }
    }
}