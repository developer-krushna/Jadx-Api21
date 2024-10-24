package jadx.core.dex.visitors;

import jadx.core.dex.attributes.AType;
import jadx.core.dex.nodes.ClassNode;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.utils.DebugChecks;

import java.util.*;

public class DepthTraversal {

	public static void visit(IDexTreeVisitor visitor, ClassNode cls) {
    try {
        if (visitor.visit(cls)) {
            List<ClassNode> innerClasses = cls.getInnerClasses();
            for (ClassNode inCls : innerClasses) {
                visit(visitor, inCls);
            }

            List<MethodNode> methods = cls.getMethods();
            for (MethodNode mth : methods) {
                visit(visitor, mth);
            }
        }
    } catch (StackOverflowError e) {
        cls.addError("StackOverflowError in pass: " + visitor.getClass().getSimpleName(), e);
    } catch (Exception e) {
        cls.addError(e.getClass().getSimpleName() + " in pass: " + visitor.getClass().getSimpleName(), e);
    }
}

	public static void visit(IDexTreeVisitor visitor, MethodNode mth) {
		try {
			if (mth.contains(AType.JADX_ERROR)) {
				return;
			}
			visitor.visit(mth);
			if (DebugChecks.checksEnabled) {
				DebugChecks.runChecksAfterVisitor(mth, visitor);
			}
		} catch (StackOverflowError | Exception e) {
			mth.addError(e.getClass().getSimpleName() + " in pass: " + visitor.getClass().getSimpleName(), e);
		}
	}

	private DepthTraversal() {
	}
}
