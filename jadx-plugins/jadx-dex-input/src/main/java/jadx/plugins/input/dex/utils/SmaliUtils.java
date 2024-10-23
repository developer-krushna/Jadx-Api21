package jadx.plugins.input.dex.utils;

import java.io.PrintWriter;
import java.io.StringWriter;

import mt.jf.baksmali.Adaptors.ClassDefinition;
import mt.jf.baksmali.BaksmaliOptions;
import mt.jf.baksmali.formatter.BaksmaliWriter;
import mt.jf.dexlib2.dexbacked.DexBackedClassDef;
import mt.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmaliUtils {
	private static final Logger LOG = LoggerFactory.getLogger(SmaliUtils.class);

	public static String getSmaliCode(byte[] dexBuf, int clsDefOffset) {
		StringWriter stringWriter = new StringWriter();
		try {
			DexBackedDexFile dexFile = new DexBackedDexFile(null, dexBuf);
			DexBackedClassDef dexBackedClassDef = new DexBackedClassDef(dexFile, clsDefOffset, 0);
			ClassDefinition classDefinition = new ClassDefinition(new BaksmaliOptions(), dexBackedClassDef);
			classDefinition.writeTo(new BaksmaliWriter(stringWriter));
		} catch (Exception e) {
			LOG.error("Error generating smali", e);
			stringWriter.append("Error generating smali code: ");
			stringWriter.append(e.getMessage());
			stringWriter.append(System.lineSeparator());
			e.printStackTrace(new PrintWriter(stringWriter, true));
		}
		return stringWriter.toString();
	}
}
