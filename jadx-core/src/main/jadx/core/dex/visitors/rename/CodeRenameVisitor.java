package jadx.core.dex.visitors.rename;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.*;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jadx.api.data.ICodeData;
import jadx.api.data.ICodeRename;
import jadx.api.data.IJavaCodeRef;
import jadx.api.data.IJavaNodeRef;
import jadx.core.dex.instructions.args.RegisterArg;
import jadx.core.dex.instructions.args.SSAVar;
import jadx.core.dex.nodes.ClassNode;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.dex.nodes.ICodeDataUpdateListener;
import jadx.core.dex.nodes.RootNode;
import jadx.core.dex.visitors.AbstractVisitor;
import jadx.core.dex.visitors.InitCodeVariables;
import jadx.core.dex.visitors.JadxVisitor;
import jadx.core.dex.visitors.debuginfo.DebugInfoApplyVisitor;
import jadx.core.utils.exceptions.JadxException;

@JadxVisitor(
name = "ApplyCodeRename",
desc = "Rename variables and other entities in methods",
runAfter = {
	InitCodeVariables.class,
	DebugInfoApplyVisitor.class
}
)

/* Traditional Method instead of  Lambda Expression*/
/* By @developer-krushna*/

public class CodeRenameVisitor extends AbstractVisitor {
	
	private static final Logger LOG = LoggerFactory.getLogger(CodeRenameVisitor.class);
	
	private Map<String, List<ICodeRename>> clsRenamesMap;
	
	@Override
	public void init(final RootNode root) throws JadxException {
		updateRenamesMap(root.getArgs().getCodeData());
		root.registerCodeDataUpdateListener(new ICodeDataUpdateListener() {
			@Override
			public void updated(ICodeData data) {
				updateRenamesMap(data);
			}
		});
	}
	
	@Override
	public boolean visit(ClassNode cls) {
		List<ICodeRename> renames = getRenames(cls);
		if (!renames.isEmpty()) {
			applyRenames(cls, renames);
		}
		for (ClassNode innerClass : cls.getInnerClasses()) {
			visit(innerClass);
		}
		return false;
	}
	
	private static void applyRenames(ClassNode cls, List<ICodeRename> renames) {
		for (ICodeRename rename : renames) {
			IJavaNodeRef nodeRef = rename.getNodeRef();
			if (nodeRef.getType() == IJavaNodeRef.RefType.METHOD) {
				MethodNode methodNode = cls.searchMethodByShortId(nodeRef.getShortId());
				if (methodNode == null) {
					LOG.warn("Method reference not found: {}", nodeRef);
				} else {
					IJavaCodeRef codeRef = rename.getCodeRef();
					if (codeRef != null) {
						processRename(methodNode, codeRef, rename);
					}
				}
			}
		}
	}
	
	private static void processRename(MethodNode mth, IJavaCodeRef codeRef, ICodeRename rename) {
		switch (codeRef.getAttachType()) {
			case MTH_ARG: {
				List<RegisterArg> argRegs = mth.getArgRegs();
				int argNum = codeRef.getIndex();
				if (argNum < argRegs.size()) {
					argRegs.get(argNum).getSVar().getCodeVar().setName(rename.getNewName());
				} else {
					LOG.warn("Incorrect method arg ref {}, should be less than {}", argNum, argRegs.size());
				}
				break;
			}
			case VAR: {
				int regNum = codeRef.getIndex() >> 16;
				int ssaVer = codeRef.getIndex() & 0xFFFF;
				for (SSAVar ssaVar : mth.getSVars()) {
					if (ssaVar.getRegNum() == regNum && ssaVar.getVersion() == ssaVer) {
						ssaVar.getCodeVar().setName(rename.getNewName());
						return;
					}
				}
				LOG.warn("Can't find variable ref by {}_{}", regNum, ssaVer);
				break;
			}
			
			default:
			LOG.warn("Rename code ref type {} not yet supported", codeRef.getAttachType());
			break;
		}
	}
	
	private List<ICodeRename> getRenames(ClassNode cls) {
		if (clsRenamesMap == null) {
			return Collections.emptyList();
		}
		List<ICodeRename> clsComments = clsRenamesMap.get(cls.getClassInfo().getFullName());
		if (clsComments == null) {
			return Collections.emptyList();
		}
		return clsComments;
	}
	private void updateRenamesMap(@Nullable ICodeData data) {
		if (data == null) {
			this.clsRenamesMap = Collections.emptyMap();
		} else {
			Map<String, List<ICodeRename>> renamesMap = new HashMap<>();
			List<ICodeRename> renames = data.getRenames();
			for (ICodeRename rename : renames) {
				if (rename.getCodeRef() != null) {
					String declaringClass = rename.getNodeRef().getDeclaringClass();
					if (!renamesMap.containsKey(declaringClass)) {
						renamesMap.put(declaringClass, new ArrayList<>());
					}
					renamesMap.get(declaringClass).add(rename);
				}
			}
			this.clsRenamesMap = renamesMap;
		}
	}
}
