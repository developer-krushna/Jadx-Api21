package jadx.core.dex.visitors.regions;

import java.util.List;
import java.util.*;

import jadx.core.dex.attributes.AFlag;
import jadx.core.dex.nodes.BlockNode;
import jadx.core.dex.nodes.IContainer;
import jadx.core.dex.nodes.IRegion;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.dex.regions.Region;
import jadx.core.dex.regions.loops.LoopRegion;
import jadx.core.dex.visitors.AbstractVisitor;

public class CleanRegions extends AbstractVisitor {
	private static final IRegionVisitor REMOVE_REGION_VISITOR = new RemoveRegionVisitor();

	@Override
	public void visit(MethodNode mth) {
		process(mth);
	}

	public static void process(MethodNode mth) {
		if (mth.isNoCode() || mth.getBasicBlocks().isEmpty()) {
			return;
		}
		DepthRegionTraversal.traverse(mth, REMOVE_REGION_VISITOR);
	}

	private static class RemoveRegionVisitor extends AbstractRegionVisitor {
		@Override
		public boolean enterRegion(MethodNode mth, IRegion region) {
        if (region instanceof Region) {
            List<IContainer> subBlocks = region.getSubBlocks();
            Iterator<IContainer> iterator = subBlocks.iterator();
            while (iterator.hasNext()) {
                IContainer subBlock = iterator.next();
                if (canRemoveRegion(subBlock)) {
                    iterator.remove();
                }
            }
        }
        return true;
    }

		private static boolean canRemoveRegion(IContainer container) {
			if (container.contains(AFlag.DONT_GENERATE)) {
				return true;
			}
			if (container instanceof BlockNode) {
				BlockNode block = (BlockNode) container;
				return block.getInstructions().isEmpty();
			}
			if (container instanceof LoopRegion) {
				LoopRegion loopRegion = (LoopRegion) container;
				if (loopRegion.isEndless()) {
					// keep empty endless loops
					return false;
				}
			}
			if (container instanceof IRegion) {
				List<IContainer> subBlocks = ((IRegion) container).getSubBlocks();
				for (IContainer subBlock : subBlocks) {
					if (!canRemoveRegion(subBlock)) {
						return false;
					}
				}
				return true;
			}
			return false;
		}
	}
}
