package com.zgmod.autodrop;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ZgAutoDropConfig {

    // ===== 开关（默认关） =====
    public static boolean autoDropEnabled = false;

    // ===== 方案系统 =====
    public static Map<String, Scheme> schemes = new LinkedHashMap<>();
    public static String activeScheme = "default";

    public static class Scheme {
        public List<String> dropRules = new ArrayList<>();
        public List<String> keepRules = new ArrayList<>();
    }

    public static Scheme currentScheme() {
        return schemes.computeIfAbsent(activeScheme, k -> new Scheme());
    }

    // ===== 持久化 =====
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("zgautodrop.json");

    public static void load() {
        schemes.clear();
        schemes.put("default", new Scheme());

        if (!Files.exists(CONFIG_PATH)) return;
        try {
            String json = Files.readString(CONFIG_PATH);
            Data data = GSON.fromJson(json, Data.class);
            if (data != null) {
                autoDropEnabled = false;   // 强制默认关
                if (data.schemes != null && !data.schemes.isEmpty()) {
                    schemes = data.schemes;
                }
                if (data.activeScheme != null) {
                    activeScheme = data.activeScheme;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            Data data = new Data();
            data.schemes = schemes;
            data.activeScheme = activeScheme;
            Files.writeString(CONFIG_PATH, GSON.toJson(data));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class Data {
        Map<String, Scheme> schemes = new LinkedHashMap<>();
        String activeScheme = "default";
    }
}