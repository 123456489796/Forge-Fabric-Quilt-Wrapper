
package com.yourname.multiloaderforge;

import net.minecraftforge.fml.common.Mod;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import com.google.gson.*;

@Mod("multiloaderforge")
public class MultiLoaderForge {
    private final ModRegistry registry = new ModRegistry();

    public MultiLoaderForge() {
        System.out.println("[MultiLoaderForge] Booting...");

        MixinBootstrap.init();
        System.out.println("[MultiLoaderForge] Mixin environment ready");

        loadAll("fabric_mods", new FabricModLoader(registry));
        loadAll("quilt_mods", new QuiltModLoader(registry));

        System.out.println("[MultiLoaderForge] Loaded mods:");
        registry.printMods();
    }

    private void loadAll(String folder, ModLoader loader) {
        File dir = new File(folder);
        if (!dir.exists()) dir.mkdirs();
        loader.loadMods(dir);
    }
}

interface ModLoader {
    void loadMods(File dir);
}

class ModRegistry {
    private final Map<String, String> mods = new LinkedHashMap<>();

    public void register(String id, String origin) {
        mods.put(id, origin);
    }

    public void printMods() {
        mods.forEach((id, from) -> System.out.println(" - " + id + " from " + from));
    }
}

class FabricModLoader implements ModLoader {
    private final ModRegistry registry;

    public FabricModLoader(ModRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void loadMods(File dir) {
        File[] mods = dir.listFiles((f, name) -> name.endsWith(".jar"));
        if (mods == null) return;

        for (File jarFile : mods) {
            try (JarFile jar = new JarFile(jarFile)) {
                ZipEntry config = jar.getEntry("fabric.mod.json");
                if (config == null) continue;

                JsonObject json = JsonParser.parseReader(new InputStreamReader(jar.getInputStream(config))).getAsJsonObject();
                String id = json.get("id").getAsString();

                registry.register(id, "Fabric");
                System.out.println("[Fabric] Loading: " + id);

                URLClassLoader loader = new ModClassLoader(jarFile);
                loadMixins(json, "[Fabric]");
                runEntrypoints(json, loader, "[Fabric]");

            } catch (Throwable t) {
                System.err.println("[Fabric] Failed: " + jarFile.getName());
                t.printStackTrace();
            }
        }
    }

    private void loadMixins(JsonObject json, String prefix) {
        if (!json.has("mixins")) return;
        for (JsonElement el : json.getAsJsonArray("mixins")) {
            String path = el.getAsString();
            Mixins.addConfiguration(path);
            System.out.println(prefix + " Mixin: " + path);
        }
    }

    private void runEntrypoints(JsonObject json, ClassLoader cl, String prefix) throws Exception {
        if (!json.has("entrypoints")) return;
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("entrypoints").entrySet()) {
            for (JsonElement element : entry.getValue().getAsJsonArray()) {
                String className = element.getAsJsonObject().get("value").getAsString();
                try {
                    Class<?> clazz = cl.loadClass(className);
                    Object inst = clazz.getDeclaredConstructor().newInstance();
                    if (inst instanceof Runnable r) {
                        r.run();
                    } else {
                        clazz.getMethod("onInitialize").invoke(inst);
                    }
                    System.out.println(prefix + " Entrypoint OK: " + className);
                } catch (Throwable t) {
                    System.err.println(prefix + " Entrypoint failed: " + className);
                    t.printStackTrace();
                }
            }
        }
    }
}

class QuiltModLoader implements ModLoader {
    private final ModRegistry registry;

    public QuiltModLoader(ModRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void loadMods(File dir) {
        File[] mods = dir.listFiles((f, name) -> name.endsWith(".jar"));
        if (mods == null) return;

        for (File jarFile : mods) {
            try (JarFile jar = new JarFile(jarFile)) {
                ZipEntry config = jar.getEntry("quilt.mod.json");
                if (config == null) continue;

                JsonObject json = JsonParser.parseReader(new InputStreamReader(jar.getInputStream(config))).getAsJsonObject();
                String id = json.get("quilt_loader").getAsJsonObject().get("id").getAsString();

                registry.register(id, "Quilt");
                System.out.println("[Quilt] Loading: " + id);

                URLClassLoader loader = new ModClassLoader(jarFile);
                loadMixins(json, "[Quilt]");
                runEntrypoints(json, loader, "[Quilt]");

            } catch (Throwable t) {
                System.err.println("[Quilt] Failed: " + jarFile.getName());
                t.printStackTrace();
            }
        }
    }

    private void loadMixins(JsonObject json, String prefix) {
        if (!json.has("mixins")) return;
        for (JsonElement el : json.getAsJsonArray("mixins")) {
            String path = el.getAsString();
            Mixins.addConfiguration(path);
            System.out.println(prefix + " Mixin: " + path);
        }
    }

    private void runEntrypoints(JsonObject json, ClassLoader cl, String prefix) throws Exception {
        if (!json.has("entrypoints")) return;
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("entrypoints").entrySet()) {
            for (JsonElement element : entry.getValue().getAsJsonArray()) {
                String className = element.getAsJsonObject().get("value").getAsString();
                try {
                    Class<?> clazz = cl.loadClass(className);
                    Object inst = clazz.getDeclaredConstructor().newInstance();
                    if (inst instanceof Runnable r) {
                        r.run();
                    } else {
                        clazz.getMethod("onInitialize").invoke(inst);
                    }
                    System.out.println(prefix + " Entrypoint OK: " + className);
                } catch (Throwable t) {
                    System.err.println(prefix + " Entrypoint failed: " + className);
                    t.printStackTrace();
                }
            }
        }
    }
}

class ModClassLoader extends URLClassLoader {
    public ModClassLoader(File jar) throws IOException {
        super(new URL[] { jar.toURI().toURL() }, ModClassLoader.class.getClassLoader());
    }
}
