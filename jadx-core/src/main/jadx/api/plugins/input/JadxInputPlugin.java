package jadx.api.plugins.input;

import java.nio.file.Path;
import java.util.List;
import java.io.*;
import jadx.api.plugins.JadxPlugin;
import jadx.api.plugins.input.data.ILoadResult;

public interface JadxInputPlugin extends JadxPlugin {
	ILoadResult loadFiles(List<File> input);
}
