package jadx.core.deobf;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.Charset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jadx.api.JadxArgs;
import jadx.api.args.DeobfuscationMapFileMode;
import jadx.core.dex.info.ClassInfo;
import jadx.core.dex.info.FieldInfo;
import jadx.core.dex.info.MethodInfo;
import jadx.core.dex.nodes.RootNode;
import jadx.core.utils.files.FileUtils;

import static java.nio.charset.StandardCharsets.UTF_8;

public class DeobfPresets {
	private static final Logger LOG = LoggerFactory.getLogger(DeobfPresets.class);

	private static final Charset MAP_FILE_CHARSET = UTF_8;

	private final File deobfMapFile;

	private final Map<String, String> pkgPresetMap = new HashMap<>();
	private final Map<String, String> clsPresetMap = new HashMap<>();
	private final Map<String, String> fldPresetMap = new HashMap<>();
	private final Map<String, String> mthPresetMap = new HashMap<>();

	public static DeobfPresets build(RootNode root) {
        File deobfMapFile = getPathDeobfMapFile(root);
        if (root.getArgs().getDeobfuscationMapFileMode() != DeobfuscationMapFileMode.IGNORE) {
            LOG.debug("Deobfuscation map file set to: {}", deobfMapFile);
        }
        return new DeobfPresets(deobfMapFile);
    }

	private static File getPathDeobfMapFile(RootNode root) {
        JadxArgs jadxArgs = root.getArgs();
        File deobfMapFile = jadxArgs.getDeobfuscationMapFile();
        if (deobfMapFile != null) {
            return deobfMapFile;
        }
        File inputFilePath = new File(jadxArgs.getInputFiles().get(0).getAbsolutePath());
        String baseName = FileUtils.getPathBaseName(inputFilePath);
        return new File(inputFilePath.getParent(), baseName + ".jobf");
    }

	private DeobfPresets(File deobfMapFile) {
        this.deobfMapFile = deobfMapFile;
    }
	/**
	 * Loads deobfuscator presets
	 */
	public boolean load() {
        if (!deobfMapFile.exists()) {
            return false;
        }
        LOG.info("Loading obfuscation map from: {}", deobfMapFile.getAbsolutePath());
        try (BufferedReader reader = new BufferedReader(new FileReader(deobfMapFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] va = splitAndTrim(line);
                if (va.length != 2) {
                    continue;
                }
                String origName = va[0];
                String alias = va[1];
                switch (line.charAt(0)) {
                    case 'p':
                        pkgPresetMap.put(origName, alias);
                        break;
                    case 'c':
                        clsPresetMap.put(origName, alias);
                        break;
                    case 'f':
                        fldPresetMap.put(origName, alias);
                        break;
                    case 'm':
                        mthPresetMap.put(origName, alias);
                        break;
                    case 'v':
                        // deprecated
                        break;
                }
            }
            return true;
        } catch (Exception e) {
            LOG.error("Failed to load deobfuscation map file '{}'", deobfMapFile.getAbsolutePath(), e);
            return false;
        }
    }


	private static String[] splitAndTrim(String str) {
		String[] v = str.substring(2).split("=");
		for (int i = 0; i < v.length; i++) {
			v[i] = v[i].trim();
		}
		return v;
	}

public void save() throws IOException {
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, String> pkgEntry : pkgPresetMap.entrySet()) {
            list.add(String.format("p %s = %s", pkgEntry.getKey(), pkgEntry.getValue()));
        }
        for (Map.Entry<String, String> clsEntry : clsPresetMap.entrySet()) {
            list.add(String.format("c %s = %s", clsEntry.getKey(), clsEntry.getValue()));
        }
        for (Map.Entry<String, String> fldEntry : fldPresetMap.entrySet()) {
            list.add(String.format("f %s = %s", fldEntry.getKey(), fldEntry.getValue()));
        }
        for (Map.Entry<String, String> mthEntry : mthPresetMap.entrySet()) {
            list.add(String.format("m %s = %s", mthEntry.getKey(), mthEntry.getValue()));
        }
        Collections.sort(list);
        if (list.isEmpty()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Deobfuscation map is empty, not saving it");
            }
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(deobfMapFile))) {
            for (String line : list) {
                writer.write(line);
                writer.newLine();
            }
        }
        LOG.info("Deobfuscation map file saved as: {}", deobfMapFile.getAbsolutePath());
    }

	public String getForCls(ClassInfo cls) {
		if (clsPresetMap.isEmpty()) {
			return null;
		}
		return clsPresetMap.get(cls.makeRawFullName());
	}

	public String getForFld(FieldInfo fld) {
		if (fldPresetMap.isEmpty()) {
			return null;
		}
		return fldPresetMap.get(fld.getRawFullId());
	}

	public String getForMth(MethodInfo mth) {
		if (mthPresetMap.isEmpty()) {
			return null;
		}
		return mthPresetMap.get(mth.getRawFullId());
	}

	public void clear() {
		clsPresetMap.clear();
		fldPresetMap.clear();
		mthPresetMap.clear();
	}

	public File getDeobfMapFile() {
		return deobfMapFile;
	}

	public Map<String, String> getPkgPresetMap() {
		return pkgPresetMap;
	}

	public Map<String, String> getClsPresetMap() {
		return clsPresetMap;
	}

	public Map<String, String> getFldPresetMap() {
		return fldPresetMap;
	}

	public Map<String, String> getMthPresetMap() {
		return mthPresetMap;
	}
}
