package jadx.core.dex.visitors.blocks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.custom.Predicate;
import java.util.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jadx.api.plugins.utils.Utils;
import jadx.core.Consts;
import jadx.core.dex.attributes.AFlag;
import jadx.core.dex.attributes.AType;
import jadx.core.dex.attributes.nodes.TmpEdgeAttr;
import jadx.core.dex.info.ClassInfo;
import jadx.core.dex.instructions.InsnType;
import jadx.core.dex.instructions.args.ArgType;
import jadx.core.dex.instructions.args.InsnArg;
import jadx.core.dex.instructions.args.NamedArg;
import jadx.core.dex.instructions.args.RegisterArg;
import jadx.core.dex.nodes.BlockNode;
import jadx.core.dex.nodes.InsnNode;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.dex.trycatch.CatchAttr;
import jadx.core.dex.trycatch.ExcHandlerAttr;
import jadx.core.dex.trycatch.ExceptionHandler;
import jadx.core.dex.trycatch.TryCatchBlockAttr;
import jadx.core.dex.visitors.typeinference.TypeCompare;
import jadx.core.utils.BlockUtils;
import jadx.core.utils.InsnRemover;
import jadx.core.utils.ListUtils;
import jadx.core.utils.exceptions.JadxRuntimeException;

public class BlockExceptionHandler {
	/* Traditional Method instead of  Lambda Expression*/
	/* By @developer-krushna*/
	
	private static final Logger LOG = LoggerFactory.getLogger(BlockExceptionHandler.class);
	
	public static boolean process(MethodNode mth) {
		if (mth.isNoExceptionHandlers()) {
			return false;
		}
		BlockProcessor.updateCleanSuccessors(mth);
		DominatorTree.computeDominanceFrontier(mth);
		
		processCatchAttr(mth);
		initExcHandlers(mth);
		
		List<TryCatchBlockAttr> tryBlocks = prepareTryBlocks(mth);
		connectExcHandlers(mth, tryBlocks);
		mth.addAttr(AType.TRY_BLOCKS_LIST, tryBlocks);
		
		for (BlockNode block : mth.getBasicBlocks()) {
			block.updateCleanSuccessors();
		}
		
		for (ExceptionHandler eh : mth.getExceptionHandlers()) {
			removeMonitorExitFromExcHandler(mth, eh);
		}
		BlockProcessor.removeMarkedBlocks(mth);
		return true;
	}
	
	/**
	* Wrap try blocks with top/bottom splitter and connect them to handler block.
	* Sometimes try block can be handler block itself and should be connected before wrapping.
	* Use queue for postpone try blocks not ready for wrap.
	*/
	private static void connectExcHandlers(MethodNode mth, List<TryCatchBlockAttr> tryBlocks) {
		if (tryBlocks.isEmpty()) {
			return;
		}
		int limit = tryBlocks.size() * 3;
		int count = 0;
		Deque<TryCatchBlockAttr> queue = new ArrayDeque<>(tryBlocks);
		while (!queue.isEmpty()) {
			TryCatchBlockAttr tryBlock = queue.removeFirst();
			boolean complete = wrapBlocksWithTryCatch(mth, tryBlock);
			if (!complete) {
				queue.addLast(tryBlock); // return to queue at the end
			}
			if (count++ > limit) {
				throw new JadxRuntimeException("Try blocks wrapping queue limit reached! Please report as an issue!");
			}
		}
	}
	
	private static void processCatchAttr(MethodNode mth) {
		for (BlockNode block : mth.getBasicBlocks()) {
			for (InsnNode insn : block.getInstructions()) {
				if (insn.contains(AType.EXC_CATCH) && !insn.canThrowException()) {
					insn.remove(AType.EXC_CATCH);
				}
			}
		}
		// if all instructions in block have same 'catch' attribute -> add this attribute for whole block.
		for (BlockNode block : mth.getBasicBlocks()) {
			CatchAttr commonCatchAttr = getCommonCatchAttr(block);
			if (commonCatchAttr != null) {
				block.addAttr(commonCatchAttr);
				for (InsnNode insn : block.getInstructions()) {
					if (insn.contains(AFlag.TRY_ENTER)) {
						block.add(AFlag.TRY_ENTER);
					}
					if (insn.contains(AFlag.TRY_LEAVE)) {
						block.add(AFlag.TRY_LEAVE);
					}
				}
			}
		}
	}
	
	@Nullable
	private static CatchAttr getCommonCatchAttr(BlockNode block) {
		CatchAttr commonCatchAttr = null;
		for (InsnNode insn : block.getInstructions()) {
			CatchAttr catchAttr = insn.get(AType.EXC_CATCH);
			if (catchAttr != null) {
				if (commonCatchAttr == null) {
					commonCatchAttr = catchAttr;
					continue;
				}
				if (!commonCatchAttr.equals(catchAttr)) {
					return null;
				}
			}
		}
		return commonCatchAttr;
	}
	
	@SuppressWarnings("ForLoopReplaceableByForEach")
	private static void initExcHandlers(MethodNode mth) {
		List<BlockNode> blocks = mth.getBasicBlocks();
		int blocksCount = blocks.size();
		for (int i = 0; i < blocksCount; i++) { // will add new blocks to list end
			BlockNode block = blocks.get(i);
			InsnNode firstInsn = BlockUtils.getFirstInsn(block);
			if (firstInsn == null) {
				continue;
			}
			ExcHandlerAttr excHandlerAttr = firstInsn.get(AType.EXC_HANDLER);
			if (excHandlerAttr == null) {
				continue;
			}
			firstInsn.remove(AType.EXC_HANDLER);
			removeTmpConnection(block);
			
			ExceptionHandler excHandler = excHandlerAttr.getHandler();
			if (block.getPredecessors().isEmpty()) {
				excHandler.setHandlerBlock(block);
				block.addAttr(excHandlerAttr);
				excHandler.addBlock(block);
				for (BlockNode dominatedBlock : BlockUtils.collectBlocksDominatedByWithExcHandlers(mth, block, block)) {
					excHandler.addBlock(dominatedBlock);
				}
			} else {
				// ignore already connected handlers -> make catch empty
				BlockNode emptyHandlerBlock = BlockSplitter.startNewBlock(mth, block.getStartOffset());
				emptyHandlerBlock.add(AFlag.SYNTHETIC);
				emptyHandlerBlock.addAttr(excHandlerAttr);
				BlockSplitter.connect(emptyHandlerBlock, block);
				excHandler.setHandlerBlock(emptyHandlerBlock);
				excHandler.addBlock(emptyHandlerBlock);
			}
			fixMoveExceptionInsn(block, excHandlerAttr);
		}
	}
	
	private static void removeTmpConnection(BlockNode block) {
		TmpEdgeAttr tmpEdgeAttr = block.get(AType.TMP_EDGE);
		if (tmpEdgeAttr != null) {
			// remove temp connection
			BlockSplitter.removeConnection(tmpEdgeAttr.getBlock(), block);
			block.remove(AType.TMP_EDGE);
		}
	}
	
	private static List<TryCatchBlockAttr> prepareTryBlocks(MethodNode mth) {
		Map<ExceptionHandler, List<BlockNode>> blocksByHandler = new HashMap<>();
		for (BlockNode block : mth.getBasicBlocks()) {
			CatchAttr catchAttr = block.get(AType.EXC_CATCH);
			if (catchAttr != null) {
				for (ExceptionHandler eh : catchAttr.getHandlers()) {
					List<BlockNode> blocks = blocksByHandler.get(eh);
					if (blocks == null) {
						blocks = new ArrayList<>();
						blocksByHandler.put(eh, blocks);
					}
					blocks.add(block);
				}
			}
		}
		if (Consts.DEBUG_EXC_HANDLERS) {
			LOG.debug("Input exception handlers:");
			for (Map.Entry<ExceptionHandler, List<BlockNode>> entry : blocksByHandler.entrySet()) {
				LOG.debug(" {}, throw blocks: {}, handler blocks: {}", entry.getKey(), entry.getValue(), entry.getKey().getBlocks());
			}
		}
		if (blocksByHandler.isEmpty()) {
			// no catch blocks -> remove all handlers
			for (ExceptionHandler eh : mth.getExceptionHandlers()) {
				removeExcHandler(mth, eh);
			}
		} else {
			// remove handlers without blocks in catch attribute
			for (Map.Entry<ExceptionHandler, List<BlockNode>> entry : blocksByHandler.entrySet()) {
				if (entry.getValue().isEmpty()) {
					removeExcHandler(mth, entry.getKey());
				}
			}
		}
		BlockSplitter.detachMarkedBlocks(mth);
		mth.clearExceptionHandlers();
		if (mth.isNoExceptionHandlers()) {
			return Collections.emptyList();
		}
		
		for (Map.Entry<ExceptionHandler, List<BlockNode>> entry : blocksByHandler.entrySet()) {
			// remove catches from same handler
			entry.getValue().removeAll(entry.getKey().getBlocks());
		}
		
		List<TryCatchBlockAttr> tryBlocks = new ArrayList<>();
		for (Map.Entry<ExceptionHandler, List<BlockNode>> entry : blocksByHandler.entrySet()) {
			List<ExceptionHandler> handlers = new ArrayList<>(1);
			handlers.add(entry.getKey());
			tryBlocks.add(new TryCatchBlockAttr(tryBlocks.size(), handlers, entry.getValue()));
		}
		if (tryBlocks.size() > 1) {
			// merge or mark as outer/inner
			while (true) {
				boolean restart = combineTryCatchBlocks(tryBlocks);
				if (!restart) {
					break;
				}
			}
		}
		checkForMultiCatch(mth, tryBlocks);
		clearTryBlocks(mth, tryBlocks);
		sortHandlers(mth, tryBlocks);
		
		if (Consts.DEBUG_EXC_HANDLERS) {
			LOG.debug("Result try-catch blocks:");
			for (TryCatchBlockAttr tryBlock : tryBlocks) {
				LOG.debug(" {}", tryBlock);
			}
		}
		return tryBlocks;
	}
	
	private static void clearTryBlocks(MethodNode mth, List<TryCatchBlockAttr> tryBlocks) {
		Iterator<TryCatchBlockAttr> iterator = tryBlocks.iterator();
		while (iterator.hasNext()) {
			TryCatchBlockAttr tc = iterator.next();
			Iterator<BlockNode> blockIterator = tc.getBlocks().iterator();
			while (blockIterator.hasNext()) {
				BlockNode b = blockIterator.next();
				if (b.contains(AFlag.REMOVE)) {
					blockIterator.remove();
				}
			}
			if (tc.getBlocks().isEmpty() || tc.getHandlers().isEmpty()) {
				iterator.remove();
			}
		}
		
		mth.clearExceptionHandlers();
		BlockSplitter.detachMarkedBlocks(mth);
	}
	
	private static boolean combineTryCatchBlocks(List<TryCatchBlockAttr> tryBlocks) {
		for (TryCatchBlockAttr outerTryBlock : tryBlocks) {
			for (TryCatchBlockAttr innerTryBlock : tryBlocks) {
				if (outerTryBlock == innerTryBlock || innerTryBlock.getOuterTryBlock() != null) {
					continue;
				}
				if (checkTryCatchRelation(tryBlocks, outerTryBlock, innerTryBlock)) {
					return true;
				}
			}
		}
		return false;
	}
	
	private static boolean checkTryCatchRelation(List<TryCatchBlockAttr> tryBlocks,
	TryCatchBlockAttr outerTryBlock, TryCatchBlockAttr innerTryBlock) {
		if (outerTryBlock.getBlocks().equals(innerTryBlock.getBlocks())) {
			// same try blocks -> merge handlers
			List<ExceptionHandler> handlers = new ArrayList<>(outerTryBlock.getHandlers());
			handlers.addAll(innerTryBlock.getHandlers());
			tryBlocks.add(new TryCatchBlockAttr(tryBlocks.size(), handlers, outerTryBlock.getBlocks()));
			tryBlocks.remove(outerTryBlock);
			tryBlocks.remove(innerTryBlock);
			return true;
		}
		
		Set<BlockNode> handlerBlocks = new HashSet<>();
		for (ExceptionHandler eh : innerTryBlock.getHandlers()) {
			handlerBlocks.addAll(eh.getBlocks());
		}
		
		boolean catchInHandler = false;
		for (BlockNode block : handlerBlocks) {
			if (isHandlersIntersects(outerTryBlock).test(block)) {
				catchInHandler = true;
				break;
			}
		}
		
		boolean catchInTry = false;
		for (BlockNode block : innerTryBlock.getBlocks()) {
			if (isHandlersIntersects(outerTryBlock).test(block)) {
				catchInTry = true;
				break;
			}
		}
		
		boolean blocksOutsideHandler = false;
		for (BlockNode block : outerTryBlock.getBlocks()) {
			if (!handlerBlocks.contains(block)) {
				blocksOutsideHandler = true;
				break;
			}
		}
		boolean makeInner = catchInHandler && (catchInTry || blocksOutsideHandler);
		if (makeInner && innerTryBlock.isAllHandler()) {
			// inner try block can't have catch-all handler
			outerTryBlock.setBlocks(Utils.concatDistinct(outerTryBlock.getBlocks(), innerTryBlock.getBlocks()));
			innerTryBlock.clear();
			return false;
		}
		if (makeInner) {
			// convert to inner
			List<BlockNode> mergedBlocks = Utils.concatDistinct(outerTryBlock.getBlocks(), innerTryBlock.getBlocks());
			innerTryBlock.getHandlers().removeAll(outerTryBlock.getHandlers());
			innerTryBlock.setOuterTryBlock(outerTryBlock);
			outerTryBlock.addInnerTryBlock(innerTryBlock);
			outerTryBlock.setBlocks(mergedBlocks);
			return false;
		}
		if (innerTryBlock.getHandlers().containsAll(outerTryBlock.getHandlers())) {
			// merge
			List<BlockNode> mergedBlocks = Utils.concatDistinct(outerTryBlock.getBlocks(), innerTryBlock.getBlocks());
			List<ExceptionHandler> handlers = Utils.concatDistinct(outerTryBlock.getHandlers(), innerTryBlock.getHandlers());
			tryBlocks.add(new TryCatchBlockAttr(tryBlocks.size(), handlers, mergedBlocks));
			tryBlocks.remove(outerTryBlock);
			tryBlocks.remove(innerTryBlock);
			return true;
		}
		return false;
	}
	
	private static class HandlersIntersectsPredicate implements Predicate<BlockNode> {
		private final TryCatchBlockAttr outerTryBlock;
		
		HandlersIntersectsPredicate(TryCatchBlockAttr outerTryBlock) {
			this.outerTryBlock = outerTryBlock;
		}
		
		@Override
		public boolean test(BlockNode block) {
			CatchAttr catchAttr = block.get(AType.EXC_CATCH);
			return catchAttr != null && Objects.equals(catchAttr.getHandlers(), outerTryBlock.getHandlers());
		}
	}
	@NotNull
	private static Predicate<BlockNode> isHandlersIntersects(TryCatchBlockAttr outerTryBlock) {
		return new HandlersIntersectsPredicate(outerTryBlock);
	}
	
	private static void removeExcHandler(MethodNode mth, ExceptionHandler excHandler) {
		excHandler.markForRemove();
		BlockSplitter.removeConnection(mth.getEnterBlock(), excHandler.getHandlerBlock());
	}
	
	private static boolean wrapBlocksWithTryCatch(MethodNode mth, TryCatchBlockAttr tryCatchBlock) {
		List<BlockNode> blocks = tryCatchBlock.getBlocks();
		BlockNode top = searchTopBlock(mth, blocks);
		if (top.getPredecessors().isEmpty() && top != mth.getEnterBlock()) {
			return false;
		}
		BlockNode bottom = searchBottomBlock(mth, blocks);
		if (Consts.DEBUG_EXC_HANDLERS) {
			LOG.debug("TryCatch #{} split: top {}, bottom: {}", tryCatchBlock.id(), top, bottom);
		}
		BlockNode topSplitterBlock = getTopSplitterBlock(mth, top);
		topSplitterBlock.add(AFlag.EXC_TOP_SPLITTER);
		topSplitterBlock.add(AFlag.SYNTHETIC);
		
		int totalHandlerBlocks = 0;
		for (ExceptionHandler eh : tryCatchBlock.getHandlers()) {
			totalHandlerBlocks += eh.getBlocks().size();
		}
		
		BlockNode bottomSplitterBlock;
		if (bottom == null || totalHandlerBlocks == 0) {
			bottomSplitterBlock = null;
		} else {
			BlockNode existBottomSplitter = BlockUtils.getBlockWithFlag(bottom.getSuccessors(), AFlag.EXC_BOTTOM_SPLITTER);
			bottomSplitterBlock = existBottomSplitter != null ? existBottomSplitter : BlockSplitter.startNewBlock(mth, -1);
			bottomSplitterBlock.add(AFlag.EXC_BOTTOM_SPLITTER);
			bottomSplitterBlock.add(AFlag.SYNTHETIC);
			BlockSplitter.connect(bottom, bottomSplitterBlock);
		}
		
		if (Consts.DEBUG_EXC_HANDLERS) {
			LOG.debug("TryCatch #{} result splitters: top {}, bottom: {}",
			tryCatchBlock.id(), topSplitterBlock, bottomSplitterBlock);
		}
		connectSplittersAndHandlers(tryCatchBlock, topSplitterBlock, bottomSplitterBlock);
		
		for (BlockNode block : blocks) {
			TryCatchBlockAttr currentTCBAttr = block.get(AType.TRY_BLOCK);
			if (currentTCBAttr == null || currentTCBAttr.getInnerTryBlocks().contains(tryCatchBlock)) {
				block.addAttr(tryCatchBlock);
			}
		}
		tryCatchBlock.setTopSplitter(topSplitterBlock);
		
		topSplitterBlock.updateCleanSuccessors();
		if (bottomSplitterBlock != null) {
			bottomSplitterBlock.updateCleanSuccessors();
		}
		return true;
	}
	
	private static BlockNode getTopSplitterBlock(MethodNode mth, BlockNode top) {
		if (top == mth.getEnterBlock()) {
			BlockNode fixedTop = mth.getEnterBlock().getSuccessors().get(0);
			return BlockSplitter.blockSplitTop(mth, fixedTop);
		}
		BlockNode existPredTopSplitter = BlockUtils.getBlockWithFlag(top.getPredecessors(), AFlag.EXC_TOP_SPLITTER);
		if (existPredTopSplitter != null) {
			return existPredTopSplitter;
		}
		// try to reuse exists splitter on empty simple path below top block
		if (top.getCleanSuccessors().size() == 1 && top.getInstructions().isEmpty()) {
			BlockNode otherTopSplitter = BlockUtils.getBlockWithFlag(top.getCleanSuccessors(), AFlag.EXC_TOP_SPLITTER);
			if (otherTopSplitter != null && otherTopSplitter.getPredecessors().size() == 1) {
				return otherTopSplitter;
			}
		}
		return BlockSplitter.blockSplitTop(mth, top);
	}
	
	private static BlockNode searchTopBlock(MethodNode mth, List<BlockNode> blocks) {
		BlockNode top = BlockUtils.getTopBlock(blocks);
		if (top != null) {
			return adjustTopBlock(top);
		}
		BlockNode topDom = BlockUtils.getCommonDominator(mth, blocks);
		if (topDom != null) {
			// dominator always return one up block if blocks already contains dominator, use successor instead
			if (topDom.getSuccessors().size() == 1) {
				BlockNode upBlock = topDom.getSuccessors().get(0);
				if (blocks.contains(upBlock)) {
					return upBlock;
				}
			}
			return adjustTopBlock(topDom);
		}
		throw new JadxRuntimeException("Failed to find top block for try-catch from: " + blocks);
	}
	
	private static BlockNode adjustTopBlock(BlockNode topBlock) {
		if (topBlock.getSuccessors().size() == 1 && !topBlock.contains(AType.EXC_CATCH)) {
			// top block can be lifted by other exception handlers included in blocks list, trying to undo that
			return topBlock.getSuccessors().get(0);
		}
		return topBlock;
	}
	
	@Nullable
	private static BlockNode searchBottomBlock(MethodNode mth, List<BlockNode> blocks) {
		BlockNode bottom = BlockUtils.getBottomBlock(blocks);
		if (bottom != null) {
			return bottom;
		}
		
		BlockNode pathCross = BlockUtils.getPathCross(mth, blocks);
		if (pathCross == null) {
			return null;
		}
		
		List<BlockNode> preds = new ArrayList<>(pathCross.getPredecessors());
		preds.removeAll(blocks);
		List<BlockNode> outsidePredecessors = new ArrayList<>();
		for (BlockNode pred : preds) {
			if (!BlockUtils.atLeastOnePathExists(blocks, pred)) {
				outsidePredecessors.add(pred);
			}
		}
		
		if (outsidePredecessors.isEmpty()) {
			return pathCross;
		}
		
		BlockNode splitCross = BlockSplitter.blockSplitTop(mth, pathCross);
		splitCross.add(AFlag.SYNTHETIC);
		for (BlockNode outsidePredecessor : outsidePredecessors) {
			BlockSplitter.replaceConnection(outsidePredecessor, splitCross, pathCross);
		}
		return splitCross;
	}
	
	
	private static void connectSplittersAndHandlers(TryCatchBlockAttr tryCatchBlock, BlockNode topSplitterBlock,
	@Nullable BlockNode bottomSplitterBlock) {
		for (ExceptionHandler handler : tryCatchBlock.getHandlers()) {
			BlockNode handlerBlock = handler.getHandlerBlock();
			BlockSplitter.connect(topSplitterBlock, handlerBlock);
			if (bottomSplitterBlock != null) {
				BlockSplitter.connect(bottomSplitterBlock, handlerBlock);
			}
		}
		TryCatchBlockAttr outerTryBlock = tryCatchBlock.getOuterTryBlock();
		if (outerTryBlock != null) {
			connectSplittersAndHandlers(outerTryBlock, topSplitterBlock, bottomSplitterBlock);
		}
	}
	
	private static void fixMoveExceptionInsn(BlockNode block, ExcHandlerAttr excHandlerAttr) {
		ExceptionHandler excHandler = excHandlerAttr.getHandler();
		ArgType argType = excHandler.getArgType();
		InsnNode me = BlockUtils.getLastInsn(block);
		if (me != null && me.getType() == InsnType.MOVE_EXCEPTION) {
			// set correct type for 'move-exception' operation
			RegisterArg resArg = InsnArg.reg(me.getResult().getRegNum(), argType);
			resArg.copyAttributesFrom(me);
			me.setResult(resArg);
			me.add(AFlag.DONT_INLINE);
			resArg.add(AFlag.CUSTOM_DECLARE);
			excHandler.setArg(resArg);
			me.addAttr(excHandlerAttr);
			return;
		}
		// handler arguments not used
		excHandler.setArg(new NamedArg("unused", argType));
	}
	
	private static void removeMonitorExitFromExcHandler(MethodNode mth, ExceptionHandler excHandler) {
		for (BlockNode excBlock : excHandler.getBlocks()) {
			InsnRemover remover = new InsnRemover(mth, excBlock);
			for (InsnNode insn : excBlock.getInstructions()) {
				if (insn.getType() == InsnType.MONITOR_ENTER) {
					break;
				}
				if (insn.getType() == InsnType.MONITOR_EXIT) {
					remover.addAndUnbind(insn);
				}
			}
			remover.perform();
		}
	}
	
	private static void checkForMultiCatch(MethodNode mth, List<TryCatchBlockAttr> tryBlocks) {
		boolean merged = false;
		for (TryCatchBlockAttr tryBlock : tryBlocks) {
			if (mergeMultiCatch(mth, tryBlock)) {
				merged = true;
			}
		}
		if (merged) {
			BlockSplitter.detachMarkedBlocks(mth);
			mth.clearExceptionHandlers();
		}
	}
	
	private static boolean mergeMultiCatch(MethodNode mth, TryCatchBlockAttr tryCatch) {
		if (tryCatch.getHandlers().size() < 2) {
			return false;
		}
		List<ExceptionHandler> handlersToRemove = new ArrayList<>();
		for (ExceptionHandler handler : tryCatch.getHandlers()) {
			if (handler.getBlocks().size() != 1) {
				return false;
			}
			BlockNode block = handler.getHandlerBlock();
			if (block.getInstructions().size() != 1
			|| !BlockUtils.checkLastInsnType(block, InsnType.MOVE_EXCEPTION)) {
				return false;
			}
			handlersToRemove.add(handler);
		}
		List<BlockNode> handlerBlocks = new ArrayList<>();
		for (ExceptionHandler handler : handlersToRemove) {
			handlerBlocks.add(handler.getHandlerBlock());
		}
		List<BlockNode> successorBlocks = new ArrayList<>();
		for (BlockNode h : handlerBlocks) {
			for (BlockNode successor : h.getSuccessors()) {
				if (!successorBlocks.contains(successor)) {
					successorBlocks.add(successor);
				}
			}
		}
		if (successorBlocks.size() != 1) {
			return false;
		}
		BlockNode successorBlock = successorBlocks.get(0);
		List<BlockNode> predecessors = successorBlock.getPredecessors();
		if (!ListUtils.unorderedEquals(predecessors, handlerBlocks)) {
			return false;
		}
		List<RegisterArg> regs = new ArrayList<>();
		for (ExceptionHandler handler : handlersToRemove) {
			RegisterArg reg = Objects.requireNonNull(BlockUtils.getLastInsn(handler.getHandlerBlock())).getResult();
			if (!regs.contains(reg)) {
				regs.add(reg);
			}
		}
		if (regs.size() != 1) {
			return false;
		}
		
		// merge confirmed, leave only the first handler, remove others
		ExceptionHandler resultHandler = tryCatch.getHandlers().get(0);
		for (ExceptionHandler handler : handlersToRemove) {
			if (handler != resultHandler) {
				resultHandler.addCatchTypes(mth, handler.getCatchTypes());
				handler.markForRemove();
			}
		}
		return true;
	}
	
	private static void sortHandlers(MethodNode mth, List<TryCatchBlockAttr> tryBlocks) {
		TypeCompare typeCompare = mth.root().getTypeCompare();
		Comparator<ArgType> comparator = typeCompare.getReversedComparator();
		
		for (TryCatchBlockAttr tryBlock : tryBlocks) {
			// Custom sorting logic for catch types without using sort method or lambda expression
			for (int i = 0; i < tryBlock.getHandlers().size(); i++) {
				for (int j = i + 1; j < tryBlock.getHandlers().size(); j++) {
					ExceptionHandler handler1 = tryBlock.getHandlers().get(i);
					ExceptionHandler handler2 = tryBlock.getHandlers().get(j);
					
					// Compare catch types using the provided comparator
					int comparisonResult = compareByTypeAndName(comparator,
					ListUtils.first(handler1.getCatchTypes()),
					ListUtils.first(handler2.getCatchTypes()));
					
					if (comparisonResult > 0) {
						// Swap elements in the list
						ExceptionHandler temp = tryBlock.getHandlers().get(i);
						tryBlock.getHandlers().set(i, tryBlock.getHandlers().get(j));
						tryBlock.getHandlers().set(j, temp);
					}
				}
			}
			
			// Custom sorting logic for handlers without using sort method or lambda expression
			for (int i = 0; i < tryBlock.getHandlers().size(); i++) {
				for (int j = i + 1; j < tryBlock.getHandlers().size(); j++) {
					ExceptionHandler handler1 = tryBlock.getHandlers().get(i);
					ExceptionHandler handler2 = tryBlock.getHandlers().get(j);
					
					if (handler1.equals(handler2)) {
						throw new JadxRuntimeException("Same handlers in try block: " + tryBlock);
					}
					
					if (handler1.isCatchAll()) {
						// Move catch-all handler to the end
						ExceptionHandler temp = tryBlock.getHandlers().get(i);
						tryBlock.getHandlers().set(i, tryBlock.getHandlers().get(j));
						tryBlock.getHandlers().set(j, temp);
					} else if (handler2.isCatchAll()) {
						// Move catch-all handler to the end
						ExceptionHandler temp = tryBlock.getHandlers().get(i);
						tryBlock.getHandlers().set(i, tryBlock.getHandlers().get(j));
						tryBlock.getHandlers().set(j, temp);
					}
				}
			}
		}
	}
	
	
	@SuppressWarnings("ComparatorResultComparison")
	private static int compareByTypeAndName(Comparator<ArgType> comparator, ClassInfo first, ClassInfo second) {
		int r = comparator.compare(first.getType(), second.getType());
		if (r == -2) {
			// on conflict sort by name
			return first.compareTo(second);
		}
		return r;
	}
}
