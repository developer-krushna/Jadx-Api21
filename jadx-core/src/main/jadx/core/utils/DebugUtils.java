package jadx.core.utils;

import java.io.File;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.custom.Function;
import com.custom.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jadx.api.ICodeWriter;
import jadx.api.impl.SimpleCodeWriter;
import jadx.core.codegen.ConditionGen;
import jadx.core.codegen.InsnGen;
import jadx.core.codegen.MethodGen;
import jadx.core.dex.attributes.AType;
import jadx.core.dex.attributes.IAttributeNode;
import jadx.core.dex.attributes.nodes.MethodOverrideAttr;
import jadx.core.dex.nodes.BlockNode;
import jadx.core.dex.nodes.IBlock;
import jadx.core.dex.nodes.IContainer;
import jadx.core.dex.nodes.IRegion;
import jadx.core.dex.nodes.InsnNode;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.dex.nodes.RootNode;
import jadx.core.dex.regions.Region;
import jadx.core.dex.regions.conditions.IfCondition;
import jadx.core.dex.regions.loops.LoopRegion;
import jadx.core.dex.visitors.AbstractVisitor;
import jadx.core.dex.visitors.DotGraphVisitor;
import jadx.core.dex.visitors.IDexTreeVisitor;
import jadx.core.dex.visitors.regions.DepthRegionTraversal;
import jadx.core.dex.visitors.regions.TracedRegionVisitor;
import jadx.core.utils.exceptions.CodegenException;
import jadx.core.utils.exceptions.JadxException;

/**
 * Use these methods only for debug purpose.
 * CheckStyle will reject usage of this class.
 */
public class DebugUtils {
	private static final Logger LOG = LoggerFactory.getLogger(DebugUtils.class);

	private DebugUtils() {
	}

	public static void dump(MethodNode mth) {
		dump(mth, "dump");
	}

	public static void dumpRaw(MethodNode mth, String desc, Predicate<MethodNode> dumpCondition) {
		if (dumpCondition.test(mth)) {
			dumpRaw(mth, desc);
		}
	}

	public static void dumpRawTest(MethodNode mth, String desc) {
		dumpRaw(mth, desc, method -> method.getName().equals("test"));
	}

	public static void dumpRaw(MethodNode mth, String desc) {
		File out = new File("test-graph-" + desc + "-tmp");
		DotGraphVisitor.dumpRaw().save(out, mth);
	}

	public static IDexTreeVisitor dumpRawVisitor(String desc) {
		return new AbstractVisitor() {
			@Override
			public void visit(MethodNode mth) throws JadxException {
				dumpRaw(mth, desc);
			}
		};
	}

	public static IDexTreeVisitor dumpRawVisitor(String desc, Predicate<MethodNode> filter) {
		return new AbstractVisitor() {
			@Override
			public void visit(MethodNode mth) {
				if (filter.test(mth)) {
					dumpRaw(mth, desc);
				}
			}
		};
	}

	public static void dump(MethodNode mth, String desc) {
		File out = new File("test-graph-" + desc + "-tmp");
		DotGraphVisitor.dump().save(out, mth);
		DotGraphVisitor.dumpRaw().save(out, mth);
		DotGraphVisitor.dumpRegions().save(out, mth);
	}

	public static void printRegionsWithBlock(MethodNode mth, BlockNode block) {
		Set<IRegion> regions = new LinkedHashSet<>();
		DepthRegionTraversal.traverse(mth, new TracedRegionVisitor() {
			@Override
			public void processBlockTraced(MethodNode mth, IBlock container, IRegion currentRegion) {
				if (block.equals(container)) {
					regions.add(currentRegion);
				}
			}
		});
		LOG.debug(" Found block: {} in regions: {}", block, regions);
	}

	public static IDexTreeVisitor printRegionsVisitor() {
		return new AbstractVisitor() {
			@Override
			public void visit(MethodNode mth) throws JadxException {
				printRegions(mth, true);
			}
		};
	}

	public static void printRegions(MethodNode mth) {
		printRegions(mth, false);
	}

	public static void printRegions(MethodNode mth, boolean printInsns) {
		Region mthRegion = mth.getRegion();
		if (mthRegion == null) {
			return;
		}
		printRegion(mth, mthRegion, printInsns);
	}

	public static void printRegion(MethodNode mth, IRegion region, boolean printInsns) {
		ICodeWriter cw = new SimpleCodeWriter();
		cw.startLine('|').add(mth.toString());
		printRegion(mth, region, cw, "|  ", printInsns);
		LOG.debug("{}{}", ICodeWriter.NL, cw.finish().getCodeStr());
	}

	private static void printRegion(MethodNode mth, IRegion region, ICodeWriter cw, String indent, boolean printInsns) {
		printWithAttributes(cw, indent, region.toString(), region);
		indent += "|  ";
		printRegionSpecificInfo(cw, indent, mth, region, printInsns);
		for (IContainer container : region.getSubBlocks()) {
			if (container instanceof IRegion) {
				printRegion(mth, (IRegion) container, cw, indent, printInsns);
			} else {
				printWithAttributes(cw, indent, container.toString(), container);
				if (printInsns && container instanceof IBlock) {
					IBlock block = (IBlock) container;
					printInsns(mth, cw, indent, block);
				}
			}
		}
	}

	private static void printRegionSpecificInfo(ICodeWriter cw, String indent,
			MethodNode mth, IRegion region, boolean printInsns) {
		if (region instanceof LoopRegion) {
			LoopRegion loop = (LoopRegion) region;
			IfCondition condition = loop.getCondition();
			if (printInsns && condition != null) {
				ConditionGen conditionGen = new ConditionGen(new InsnGen(MethodGen.getFallbackMethodGen(mth), true));
				cw.startLine(indent).add("|> ");
				try {
					conditionGen.add(cw, condition);
				} catch (Exception e) {
					cw.startLine(indent).add(">!! ").add(condition.toString());
				}
			}
		}
	}

	private static void printInsns(MethodNode mth, ICodeWriter cw, String indent, IBlock block) {
    for (InsnNode insn : block.getInstructions()) {
        try {
            MethodGen mg = MethodGen.getFallbackMethodGen(mth);
            InsnGen ig = new InsnGen(mg, true);
            ICodeWriter code = new SimpleCodeWriter();
            ig.makeInsn(insn, code);
            String codeStr = code.getCodeStr();

            String[] insnStrings = codeStr.split(ICodeWriter.NL);
            List<String> filteredInsns = new ArrayList<>();
            for (String s : insnStrings) {
                if (StringUtils.notBlank(s)) {
                    filteredInsns.add("|> " + s);
                }
            }
            Iterator<String> it = filteredInsns.iterator();
            while (true) {
                String insnStr = it.next();
                if (it.hasNext()) {
                    cw.startLine(indent).add(insnStr);
                } else {
                    printWithAttributes(cw, indent, insnStr, insn);
                    break;
                }
            }
        } catch (CodegenException e) {
            cw.startLine(indent).add(">!! ").add(insn.toString());
        }
    }
}

private static void printWithAttributes(ICodeWriter cw, String indent, String codeStr, IAttributeNode attrNode) {
    String str = attrNode.isAttrStorageEmpty() ? codeStr : codeStr + ' ' + attrNode.getAttributesString();
    String[] attrStrings = str.split(ICodeWriter.NL);
    List<String> filteredAttrs = new ArrayList<>();
    for (String s : attrStrings) {
        if (StringUtils.notBlank(s)) {
            filteredAttrs.add(s);
        }
    }
    Iterator<String> it = filteredAttrs.iterator();
    if (!it.hasNext()) {
        return;
    }
    cw.startLine(indent).add(it.next());
    while (it.hasNext()) {
        cw.startLine(indent).add("|+  ").add(it.next());
    }
}

	public static void printMap(Map<?, ?> map, String desc) {
		LOG.debug("Map {} (size = {}):", desc, map.size());
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			LOG.debug("  {}: {}", entry.getKey(), entry.getValue());
		}
	}

	public static void printStackTrace(String label) {
		LOG.debug("StackTrace: {}\n{}", label, Utils.getStackTrace(new Exception()));
	}

	
	private static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
		Set<Object> seen = ConcurrentHashMap.newKeySet();
		return t -> seen.add(keyExtractor.apply(t));
	}

	private static Map<String, Long> execTimes;

	public static void initExecTimes() {
		execTimes = new ConcurrentHashMap<>();
	}

	public static void mergeExecTimeFromStart(String tag, long startTimeMillis) {
		mergeExecTime(tag, System.currentTimeMillis() - startTimeMillis);
	}

	public static void mergeExecTime(String tag, long execTimeMillis) {
    Long existingValue = execTimes.get(tag);
    if (existingValue != null) {
        execTimes.put(tag, existingValue + execTimeMillis);
    } else {
        execTimes.put(tag, execTimeMillis);
    }
}

	public static void printExecTimes() {
    System.out.println("Exec times:");
    for (Map.Entry<String, Long> entry : execTimes.entrySet()) {
        String tag = entry.getKey();
        Long time = entry.getValue();
        System.out.println(" " + tag + ": " + time + "ms");
    }
}

public static void printExecTimesWithTotal(long totalMillis) {
    System.out.println("Exec times: total " + totalMillis + "ms");
    for (Map.Entry<String, Long> entry : execTimes.entrySet()) {
        String tag = entry.getKey();
        Long time = entry.getValue();
        double percentage = (time * 100.0) / (double) totalMillis;
        System.out.println(" " + tag + ": " + time + "ms" + String.format(" (%.2f%%)", percentage));
    }
  }
}
