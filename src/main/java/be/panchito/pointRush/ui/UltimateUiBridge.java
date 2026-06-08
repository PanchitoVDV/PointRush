package be.panchito.pointRush.ui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;

/**
 * Reflection bridge naar Ultimate UI (Xqedii {@code UltimateUIAPI}) — optionele soft dependency.
 */
public final class UltimateUiBridge {

    private static final String[] KNOWN_API_CLASSES = {
            "dev.xqedii.ultimateui.api.UltimateUIAPI",
            "dev.xqedii.ultimateui.UltimateUIAPI",
            "dev.xqedii.ultimateui.api.UltimateUiApi",
            "com.xqedii.ultimateui.api.UltimateUIAPI",
    };

    private final JavaPlugin plugin;
    private String configuredPluginName;
    private Object api;
    private Method openBulkHud;
    private Method openSingleHud;
    private Method openHudOverlay;
    private Method setElementText;
    private Method setElementColor;
    private Method closeGuiMethod;
    private Method isGuiOpenMethod;
    private boolean missingWarningLogged;

    public UltimateUiBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void setConfiguredPluginName(String configuredPluginName) {
        this.configuredPluginName = configuredPluginName == null || configuredPluginName.isBlank()
                ? null
                : configuredPluginName.trim();
    }

    public void init() {
        api = null;
        openBulkHud = null;
        openSingleHud = null;
        openHudOverlay = null;
        setElementText = null;
        setElementColor = null;
        closeGuiMethod = null;
        isGuiOpenMethod = null;
        missingWarningLogged = false;
        resolveApi();
    }

    public boolean isAvailable() {
        return api != null;
    }

    public int openHudOverlay(Iterable<? extends Player> players, String pageId, boolean autoClose) {
        if (!isAvailable()) {
            resolveApi();
        }
        if (api == null) {
            warnIfMissing();
            return 0;
        }

        List<Player> online = new ArrayList<>();
        for (Player player : players) {
            if (player != null && player.isOnline()) {
                online.add(player);
            }
        }
        if (online.isEmpty()) {
            return 0;
        }

        if (openBulkHud != null) {
            try {
                Object result = openBulkHud.invoke(api, online, pageId, true, autoClose);
                return countResult(result, online.size());
            } catch (ReflectiveOperationException ex) {
                plugin.getLogger().log(Level.WARNING, "Ultimate UI bulk open mislukt voor '" + pageId + "'.", ex);
            }
        }

        int opened = 0;
        for (Player player : online) {
            try {
                Object result = invokeSingleOpen(player, pageId, autoClose);
                if (!(result instanceof Boolean ok) || ok) {
                    opened++;
                }
            } catch (ReflectiveOperationException ex) {
                plugin.getLogger().log(Level.FINE, "Ultimate UI open mislukt voor " + player.getName(), ex);
            }
        }
        return opened;
    }

    private Object invokeSingleOpen(Player player, String pageId, boolean autoClose)
            throws ReflectiveOperationException {
        if (autoClose && openSingleHud != null) {
            return openSingleHud.invoke(api, player, pageId, true, autoClose);
        }
        if (openHudOverlay != null) {
            return openHudOverlay.invoke(api, player, pageId);
        }
        if (openSingleHud != null) {
            return openSingleHud.invoke(api, player, pageId, true, autoClose);
        }
        throw new IllegalStateException("Geen Ultimate UI open-methode beschikbaar");
    }

    /**
     * Opent een persistente HUD-overlay (geen auto-close) voor één speler.
     */
    public boolean openPersistentHud(Player player, String pageId) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (!isAvailable()) {
            resolveApi();
        }
        if (api == null) {
            warnIfMissing();
            return false;
        }
        try {
            if (openHudOverlay != null) {
                Object result = openHudOverlay.invoke(api, player, pageId);
                return !(result instanceof Boolean ok) || ok;
            }
            if (openSingleHud != null) {
                Object result = openSingleHud.invoke(api, player, pageId, true, false);
                return !(result instanceof Boolean ok) || ok;
            }
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().log(Level.FINE, "Ultimate UI HUD open mislukt voor " + player.getName(), ex);
        }
        return false;
    }

    /**
     * Werkt de tekst van een element (op {@code name}) bij voor één speler.
     */
    public void setText(Player player, String elementId, String value) {
        if (api == null || setElementText == null || player == null || !player.isOnline()) {
            return;
        }
        try {
            setElementText.invoke(api, player, elementId, value == null ? "" : value);
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().log(Level.FINE, "Ultimate UI setElementText mislukt voor " + elementId, ex);
        }
    }

    /**
     * Werkt de kleur (hex, bv. {@code #95e553}) van een element bij voor één speler.
     */
    public void setColor(Player player, String elementId, String hex) {
        if (api == null || setElementColor == null || player == null || !player.isOnline() || hex == null) {
            return;
        }
        try {
            setElementColor.invoke(api, player, elementId, hex);
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().log(Level.FINE, "Ultimate UI setElementColor mislukt voor " + elementId, ex);
        }
    }

    /**
     * Sluit de open Ultimate UI overlay voor een speler.
     */
    public void close(Player player) {
        if (api == null || closeGuiMethod == null || player == null) {
            return;
        }
        try {
            closeGuiMethod.invoke(api, player);
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().log(Level.FINE, "Ultimate UI closeGui mislukt voor " + player.getName(), ex);
        }
    }

    /**
     * Geeft terug of de opgegeven page bij de speler open staat (best effort).
     */
    public boolean isHudOpen(Player player, String pageId) {
        if (api == null || isGuiOpenMethod == null || player == null) {
            return false;
        }
        try {
            Object result = isGuiOpenMethod.invoke(api, player, pageId);
            return result instanceof Boolean ok && ok;
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }

    private static int countResult(Object result, int fallback) {
        if (result instanceof Integer count) {
            return count;
        }
        if (result instanceof Number number) {
            return number.intValue();
        }
        if (result instanceof Boolean ok) {
            return ok ? fallback : 0;
        }
        return fallback;
    }

    private void resolveApi() {
        Plugin uiPlugin = findUltimateUiPlugin();
        if (uiPlugin != null) {
            for (String className : discoverApiClassNames(uiPlugin)) {
                if (tryBindApiClass(className, uiPlugin.getClass().getClassLoader(),
                        uiPlugin.getName() + " -> " + className)) {
                    return;
                }
            }
        }

        for (String className : discoverApiClassNamesInPluginsFolder()) {
            ClassLoader loader = uiPlugin != null
                    ? uiPlugin.getClass().getClassLoader()
                    : plugin.getClass().getClassLoader();
            if (tryBindApiClass(className, loader, "plugins/ -> " + className)) {
                return;
            }
        }

        for (String className : KNOWN_API_CLASSES) {
            for (ClassLoader loader : collectClassLoaders()) {
                if (tryBindApiClass(className, loader, className)) {
                    return;
                }
            }
        }

        tryBindFromServices();
        scanAllPluginsForApiInJars();
    }

    private boolean tryBindApiClass(String className, ClassLoader loader, String source) {
        try {
            Class<?> apiClass = Class.forName(className, true, loader);
            Object instance = resolveApiSingleton(apiClass);
            if (instance != null && bindApi(instance, source)) {
                return true;
            }
        } catch (ClassNotFoundException ignored) {
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().log(Level.FINE, "Ultimate UI bind mislukt voor " + className, ex);
        }
        return false;
    }

    private static Object resolveApiSingleton(Class<?> apiClass) throws ReflectiveOperationException {
        Boolean available = invokeStaticBoolean(apiClass, "isAvailable");
        if (Boolean.FALSE.equals(available)) {
            return null;
        }

        for (String methodName : new String[]{"get", "getInstance", "getAPI", "getApi"}) {
            try {
                Method method = apiClass.getMethod(methodName);
                if (!Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                Object instance = method.invoke(null);
                if (instance != null) {
                    return instance;
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private void tryBindFromServices() {
        ServicesManager services = Bukkit.getServicesManager();
        for (Class<?> serviceClass : services.getKnownServices()) {
            if (!looksLikeUiApi(serviceClass)) {
                continue;
            }
            RegisteredServiceProvider<?> provider = services.getRegistration(serviceClass);
            if (provider != null && bindApi(provider.getProvider(),
                    "ServicesManager -> " + serviceClass.getName())) {
                return;
            }
        }
    }

    private void scanAllPluginsForApiInJars() {
        for (Plugin candidate : Bukkit.getPluginManager().getPlugins()) {
            if (!candidate.isEnabled() || candidate == plugin) {
                continue;
            }
            for (String className : discoverApiClassNames(candidate)) {
                if (tryBindApiClass(className, candidate.getClass().getClassLoader(),
                        candidate.getName() + " -> " + className)) {
                    return;
                }
            }
        }
    }

    private List<String> discoverApiClassNames(Plugin uiPlugin) {
        Set<String> names = new LinkedHashSet<>();
        for (String known : KNOWN_API_CLASSES) {
            names.add(known);
        }
        names.addAll(scanJarForApiClasses(pluginJarFile(uiPlugin)));
        if (uiPlugin.getDataFolder() != null) {
            File libDir = uiPlugin.getDataFolder();
            if (libDir.isDirectory()) {
                File[] nested = libDir.listFiles((dir, name) -> name.endsWith(".jar"));
                if (nested != null) {
                    for (File jar : nested) {
                        names.addAll(scanJarForApiClasses(jar));
                    }
                }
            }
        }
        return new ArrayList<>(names);
    }

    private List<String> discoverApiClassNamesInPluginsFolder() {
        Set<String> names = new LinkedHashSet<>();
        File pluginsDir = plugin.getDataFolder().getParentFile();
        if (pluginsDir == null || !pluginsDir.isDirectory()) {
            return List.of();
        }
        File[] jars = pluginsDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            return name.endsWith(".jar") && lower.contains("ultimate");
        });
        if (jars == null) {
            return List.of();
        }
        for (File jar : jars) {
            names.addAll(scanJarForApiClasses(jar));
        }
        return new ArrayList<>(names);
    }

    private static File pluginJarFile(Plugin uiPlugin) {
        try {
            if (uiPlugin.getClass().getProtectionDomain().getCodeSource() == null) {
                return null;
            }
            URI location = uiPlugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
            return Path.of(location).toFile();
        } catch (Exception ex) {
            return null;
        }
    }

    private static List<String> scanJarForApiClasses(File jarFile) {
        List<String> found = new ArrayList<>();
        if (jarFile == null || !jarFile.isFile()) {
            return found;
        }
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.endsWith(".class") || name.contains("$")) {
                    continue;
                }
                String className = name.replace('/', '.').substring(0, name.length() - 6);
                String simple = className.substring(className.lastIndexOf('.') + 1);
                if (simple.equalsIgnoreCase("UltimateUIAPI") || simple.equalsIgnoreCase("UltimateUiApi")) {
                    found.add(className);
                    continue;
                }
                if (name.contains("/api/") && (simple.endsWith("API") || simple.endsWith("Api"))) {
                    found.add(className);
                }
            }
        } catch (Exception ignored) {
        }
        return found;
    }

    private boolean bindApi(Object candidate, String source) {
        if (candidate == null) {
            return false;
        }

        Method bulk = findOpenGui(candidate.getClass(), Iterable.class);
        if (bulk == null) {
            bulk = findOpenGui(candidate.getClass(), Collection.class);
        }
        Method singleHud = findOpenGui(candidate.getClass(), Player.class);
        Method hudOverlay = findMethod(candidate.getClass(), "openGuiHud", Player.class, String.class);
        if (bulk == null && singleHud == null && hudOverlay == null) {
            return false;
        }

        this.api = candidate;
        this.openBulkHud = bulk;
        this.openSingleHud = singleHud;
        this.openHudOverlay = hudOverlay;
        this.setElementText = findMethod(candidate.getClass(), "setElementText",
                Player.class, String.class, String.class);
        this.setElementColor = findMethod(candidate.getClass(), "setElementColor",
                Player.class, String.class, String.class);
        this.closeGuiMethod = findMethod(candidate.getClass(), "closeGui", Player.class);
        this.isGuiOpenMethod = findMethod(candidate.getClass(), "isGuiOpen", Player.class, String.class);
        plugin.getLogger().info("Ultimate UI gekoppeld via " + source + ".");
        return true;
    }

    private static Method findOpenGui(Class<?> apiClass, Class<?> firstParamType) {
        for (Method method : apiClass.getMethods()) {
            if (!method.getName().equals("openGui") || method.getParameterCount() != 4) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (firstParamType.isAssignableFrom(params[0])
                    && params[1] == String.class
                    && params[2] == boolean.class
                    && params[3] == boolean.class) {
                return method;
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> apiClass, String name, Class<?>... paramTypes) {
        try {
            return apiClass.getMethod(name, paramTypes);
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    private static boolean looksLikeUiApi(Class<?> type) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals("openGuiHud") || method.getName().equals("openGui")) {
                return true;
            }
        }
        return type.getSimpleName().contains("UltimateUI");
    }

    private static Boolean invokeStaticBoolean(Class<?> apiClass, String methodName) {
        try {
            Method method = apiClass.getMethod(methodName);
            if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != boolean.class) {
                return null;
            }
            return (Boolean) method.invoke(null);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    private Plugin findUltimateUiPlugin() {
        if (configuredPluginName != null) {
            Plugin configured = Bukkit.getPluginManager().getPlugin(configuredPluginName);
            if (configured != null && configured.isEnabled()) {
                return configured;
            }
        }

        for (String name : new String[]{"UltimateUI", "ultimate-ui", "Ultimate UI"}) {
            Plugin found = Bukkit.getPluginManager().getPlugin(name);
            if (found != null && found.isEnabled()) {
                return found;
            }
        }

        for (Plugin loaded : Bukkit.getPluginManager().getPlugins()) {
            if (!loaded.isEnabled()) {
                continue;
            }
            String normalized = loaded.getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (normalized.contains("ultimateui")) {
                return loaded;
            }
        }
        return null;
    }

    private List<ClassLoader> collectClassLoaders() {
        Set<ClassLoader> loaders = new LinkedHashSet<>();
        Plugin uiPlugin = findUltimateUiPlugin();
        if (uiPlugin != null) {
            loaders.add(uiPlugin.getClass().getClassLoader());
        }
        for (Plugin loaded : Bukkit.getPluginManager().getPlugins()) {
            if (loaded.isEnabled()) {
                loaders.add(loaded.getClass().getClassLoader());
            }
        }
        return new ArrayList<>(loaders);
    }

    private void warnIfMissing() {
        if (missingWarningLogged) {
            return;
        }
        missingWarningLogged = true;

        Plugin uiPlugin = findUltimateUiPlugin();
        if (uiPlugin == null) {
            plugin.getLogger().warning(
                    "Ultimate UI plugin niet gevonden — minigame-transitie wordt overgeslagen. "
                            + "Installeer UltimateUI Beta en zet eventueel minigame-transition.plugin-name in settings.yml.");
            return;
        }

        List<String> discovered = discoverApiClassNames(uiPlugin);
        discovered.addAll(discoverApiClassNamesInPluginsFolder());
        File jarFile = pluginJarFile(uiPlugin);
        if (discovered.isEmpty()) {
            plugin.getLogger().warning(
                    "Ultimate UI API niet gekoppeld — plugin '" + uiPlugin.getName() + "' is wel geladen"
                            + (jarFile != null ? " (" + jarFile.getName() + ")" : "")
                            + " maar bevat geen UltimateUIAPI class. "
                            + "Beta 1.1 vereist mogelijk een aparte API-jar van Xqedii — "
                            + "download die en plaats hem in plugins/ naast UltimateUI.");
            return;
        }

        plugin.getLogger().warning(
                "Ultimate UI API niet gekoppeld — plugin '" + uiPlugin.getName() + "' is wel geladen"
                        + (jarFile != null ? " (" + jarFile.getName() + ")" : "")
                        + ". Geprobeerde API-klassen: " + String.join(", ", discovered)
                        + ". UltimateUIAPI.isAvailable() is waarschijnlijk false — wacht tot Ultimate UI volledig geladen is.");
    }
}
