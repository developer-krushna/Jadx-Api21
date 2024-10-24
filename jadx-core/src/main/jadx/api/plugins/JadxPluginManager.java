package jadx.api.plugins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.*;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jadx.api.plugins.input.JadxInputPlugin;
import jadx.api.plugins.options.JadxPluginOptions;
import jadx.api.plugins.options.OptionDescription;

public class JadxPluginManager {
	private static final Logger LOG = LoggerFactory.getLogger(JadxPluginManager.class);

	private final Set<PluginData> allPlugins = new TreeSet<>();
	private final Map<String, String> provideSuggestions = new TreeMap<>();

	private List<JadxPlugin> resolvedPlugins = Collections.emptyList();

	public JadxPluginManager() {
	}

	/**
	 * Add suggestion how to resolve conflicting plugins
	 */
	public void providesSuggestion(String provides, String pluginId) {
		provideSuggestions.put(provides, pluginId);
	}

	public void load() {
		allPlugins.clear();
		ServiceLoader<JadxPlugin> jadxPlugins = ServiceLoader.load(JadxPlugin.class);
		for (JadxPlugin plugin : jadxPlugins) {
			addPlugin(plugin);
			LOG.debug("Loading plugin: {}", plugin.getPluginInfo().getPluginId());
		}
		resolve();
	}

	public void register(JadxPlugin plugin) {
		Objects.requireNonNull(plugin);
		PluginData addedPlugin = addPlugin(plugin);
		LOG.debug("Register plugin: {}", addedPlugin.getPluginId());
		resolve();
	}

	private PluginData addPlugin(JadxPlugin plugin) {
		PluginData pluginData = new PluginData(plugin, plugin.getPluginInfo());
		if (!allPlugins.add(pluginData)) {
			throw new IllegalArgumentException("Duplicate plugin id: " + pluginData + ", class " + plugin.getClass());
		}
		if (plugin instanceof JadxPluginOptions) {
			verifyOptions(((JadxPluginOptions) plugin), pluginData.getPluginId());
		}
		return pluginData;
	}

	private void verifyOptions(JadxPluginOptions plugin, String pluginId) {
    List<OptionDescription> descriptions = plugin.getOptionsDescriptions();
    if (descriptions == null) {
        throw new IllegalArgumentException("Null option descriptions in plugin id: " + pluginId);
    }
    String prefix = pluginId + '.';
    for (OptionDescription descObj : descriptions) {
        String optName = descObj.name();
        if (optName == null || !optName.startsWith(prefix)) {
            throw new IllegalArgumentException("Plugin option name should start with plugin id: '" + prefix + "', option: " + optName);
        }
        String desc = descObj.description();
        if (desc == null || desc.isEmpty()) {
            throw new IllegalArgumentException("Plugin option description not set, plugin: " + pluginId);
        }
        List<String> values = descObj.values();
        if (values == null) {
            throw new IllegalArgumentException("Plugin option values is null, option: " + optName + ", plugin: " + pluginId);
        }
    }
}

public boolean unload(String pluginId) {
    Iterator<PluginData> iterator = allPlugins.iterator();
    boolean result = false;
    while (iterator.hasNext()) {
        PluginData pd = iterator.next();
        String id = pd.getPluginId();
        if (id.equals(pluginId)) {
            LOG.debug("Unload plugin: {}", id);
            iterator.remove();
            result = true;
        }
    }
    resolve();
    return result;
}

public List<JadxPlugin> getAllPlugins() {
    if (allPlugins.isEmpty()) {
        load();
    }
    List<JadxPlugin> plugins = new ArrayList<>();
    for (PluginData pd : allPlugins) {
        plugins.add(pd.getPlugin());
    }
    return plugins;
}

public List<JadxPlugin> getResolvedPlugins() {
    return Collections.unmodifiableList(resolvedPlugins);
}

public List<JadxInputPlugin> getInputPlugins() {
    List<JadxInputPlugin> inputPlugins = new ArrayList<>();
    for (JadxPlugin plugin : resolvedPlugins) {
        if (plugin instanceof JadxInputPlugin) {
            inputPlugins.add((JadxInputPlugin) plugin);
        }
    }
    return inputPlugins;
}

public List<JadxPluginOptions> getPluginsWithOptions() {
    List<JadxPluginOptions> pluginsWithOptions = new ArrayList<>();
    for (JadxPlugin plugin : resolvedPlugins) {
        if (plugin instanceof JadxPluginOptions) {
            pluginsWithOptions.add((JadxPluginOptions) plugin);
        }
    }
    return pluginsWithOptions;
}

private synchronized void resolve() {
    Map<String, List<PluginData>> provides = new HashMap<>();
    for (PluginData pd : allPlugins) {
        String provide = pd.getInfo().getProvides();
        if (!provides.containsKey(provide)) {
            provides.put(provide, new ArrayList<>());
        }
        provides.get(provide).add(pd);
    }

    List<PluginData> result = new ArrayList<>(provides.size());
    for (List<PluginData> list : provides.values()) {
        if (list.size() == 1) {
            result.add(list.get(0));
        } else {
            String suggestion = provideSuggestions.get(list.get(0).getInfo().getProvides());
            if (suggestion != null) {
                for (PluginData p : list) {
                    if (p.getPluginId().equals(suggestion)) {
                        result.add(p);
                        break;
                    }
                }
            } else {
                PluginData selected = list.get(0);
                result.add(selected);
                LOG.debug("Select providing '{}' plugin '{}', candidates: {}", selected.getInfo().getProvides(), selected, list);
            }
        }
    }

    Collections.sort(result);
    resolvedPlugins = new ArrayList<>();
    for (PluginData pd : result) {
        resolvedPlugins.add(pd.getPlugin());
    }
}

	private static final class PluginData implements Comparable<PluginData> {
		private final JadxPlugin plugin;
		private final JadxPluginInfo info;

		private PluginData(JadxPlugin plugin, JadxPluginInfo info) {
			this.plugin = plugin;
			this.info = info;
		}

		public JadxPlugin getPlugin() {
			return plugin;
		}

		public JadxPluginInfo getInfo() {
			return info;
		}

		public String getPluginId() {
			return info.getPluginId();
		}

		@Override
		public int compareTo(@NotNull JadxPluginManager.PluginData o) {
			return this.info.getPluginId().compareTo(o.info.getPluginId());
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof PluginData)) {
				return false;
			}
			PluginData that = (PluginData) o;
			return getInfo().getPluginId().equals(that.getInfo().getPluginId());
		}

		@Override
		public int hashCode() {
			return info.getPluginId().hashCode();
		}

		@Override
		public String toString() {
			return info.getPluginId();
		}
	}
}
