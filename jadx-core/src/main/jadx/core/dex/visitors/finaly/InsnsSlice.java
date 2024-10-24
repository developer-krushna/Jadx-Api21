package jadx.core.dex.visitors.finaly;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.*;

import org.jetbrains.annotations.Nullable;

import jadx.core.dex.nodes.BlockNode;
import jadx.core.dex.nodes.InsnNode;

public class InsnsSlice {
	private final List<InsnNode> insnsList = new ArrayList<>();
	private final Map<InsnNode, BlockNode> insnMap = new IdentityHashMap<>();
	private boolean complete;
	
	public void addInsn(InsnNode insn, BlockNode block) {
		insnsList.add(insn);
		insnMap.put(insn, block);
	}
	
	public void addBlock(BlockNode block) {
		for (InsnNode insn : block.getInstructions()) {
			addInsn(insn, block);
		}
	}
	
	public void addInsns(BlockNode block, int startIndex, int endIndex) {
		List<InsnNode> insns = block.getInstructions();
		for (int i = startIndex; i < endIndex; i++) {
			addInsn(insns.get(i), block);
		}
	}
	
	@Nullable
	public BlockNode getBlock(InsnNode insn) {
		return insnMap.get(insn);
	}
	
	public List<InsnNode> getInsnsList() {
		return insnsList;
	}
	
	public Set<BlockNode> getBlocks() {
		Set<BlockNode> set = new LinkedHashSet<>();
		for (InsnNode insn : insnsList) {
			set.add(insnMap.get(insn));
		}
		return set;
	}
	
	public void resetIncomplete() {
		if (!complete) {
			insnsList.clear();
			insnMap.clear();
		}
	}
	
	public boolean isComplete() {
		return complete;
	}
	
	public void setComplete(boolean complete) {
		this.complete = complete;
	}
	/* Traditional Method instead of  Lambda Expression*/
	/* By @developer-krushna*/
	@Override
	public String toString() {
		StringBuilder stringBuilder = new StringBuilder("{[");
		Iterator<InsnNode> iterator = insnsList.iterator();
		while (iterator.hasNext()) {
			InsnNode insn = iterator.next();
			stringBuilder.append(insn.getType().toString());
			if (iterator.hasNext()) {
				stringBuilder.append(", ");
			}
		}
		stringBuilder.append(']')
		.append(complete ? " complete" : "")
		.append('}');
		return stringBuilder.toString();
	}
}
