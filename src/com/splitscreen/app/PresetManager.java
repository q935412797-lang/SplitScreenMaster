package com.splitscreen.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class PresetManager {

    private static final String PREF_NAME = "split_screen_presets";
    private static final String KEY_PRESETS = "presets";

    public static class Preset {
        public String name;
        public String[] packages = new String[3];
        public float[] fractions = {1f/3f, 1f/3f, 1f/3f};

        public Preset(String name) {
            this.name = name;
        }

        public JSONObject toJson() {
            try {
                JSONObject json = new JSONObject();
                json.put("name", name);
                JSONArray pkgs = new JSONArray();
                for (int i = 0; i < 3; i++) pkgs.put(packages[i] != null ? packages[i] : "");
                json.put("packages", pkgs);
                JSONArray fracs = new JSONArray();
                for (int i = 0; i < 3; i++) fracs.put(fractions[i]);
                json.put("fractions", fracs);
                return json;
            } catch (Exception e) {
                return null;
            }
        }

        public static Preset fromJson(JSONObject json) {
            try {
                Preset p = new Preset(json.getString("name"));
                JSONArray pkgs = json.getJSONArray("packages");
                for (int i = 0; i < 3 && i < pkgs.length(); i++) {
                    String pkg = pkgs.getString(i);
                    p.packages[i] = pkg.isEmpty() ? null : pkg;
                }
                JSONArray fracs = json.getJSONArray("fractions");
                for (int i = 0; i < 3 && i < fracs.length(); i++) {
                    p.fractions[i] = (float) fracs.getDouble(i);
                }
                return p;
            } catch (Exception e) {
                return null;
            }
        }
    }

    public static List<Preset> loadPresets(Context context) {
        List<Preset> presets = new ArrayList<>();
        try {
            SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String jsonStr = sp.getString(KEY_PRESETS, "[]");
            JSONArray array = new JSONArray(jsonStr);
            for (int i = 0; i < array.length(); i++) {
                Preset p = Preset.fromJson(array.getJSONObject(i));
                if (p != null) presets.add(p);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return presets;
    }

    public static void savePresets(Context context, List<Preset> presets) {
        try {
            JSONArray array = new JSONArray();
            for (Preset p : presets) {
                JSONObject json = p.toJson();
                if (json != null) array.put(json);
            }
            SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            sp.edit().putString(KEY_PRESETS, array.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void addPreset(Context context, Preset preset) {
        List<Preset> presets = loadPresets(context);
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).name.equals(preset.name)) {
                presets.set(i, preset);
                savePresets(context, presets);
                return;
            }
        }
        presets.add(preset);
        savePresets(context, presets);
    }

    public static void deletePreset(Context context, String name) {
        List<Preset> presets = loadPresets(context);
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).name.equals(name)) {
                presets.remove(i);
                savePresets(context, presets);
                return;
            }
        }
    }
}
