package jadx.core.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jadx.api.IDecompileScheduler;
import jadx.api.JavaClass;
import jadx.core.utils.exceptions.JadxRuntimeException;

public class DecompilerScheduler implements IDecompileScheduler {
	private static final Logger LOG = LoggerFactory.getLogger(DecompilerScheduler.class);
	
	private static final int MERGED_BATCH_SIZE = 16;
	private static final boolean DEBUG_BATCHES = false;
	
	@Override
	public List<List<JavaClass>> buildBatches(List<JavaClass> classes) {
		try {
			long start = System.currentTimeMillis();
			List<List<JavaClass>> result = internalBatches(classes);
			if (LOG.isDebugEnabled()) {
				LOG.debug("Build decompilation batches in {}ms", System.currentTimeMillis() - start);
			}
			if (DEBUG_BATCHES) {
				check(result, classes);
			}
			return result;
		} catch (Throwable e) {
			LOG.warn("Build batches failed (continue with fallback)", e);
			return buildFallback(classes);
		}
	}
	
	/**
	* Put classes with many dependencies at the end.
	* Build batches for dependencies of single class to avoid locking from another thread.
	*/
	public class SortedHashSet<T> extends HashSet<T> {
		private final Comparator<? super T> comparator;
		
		public SortedHashSet(int initialCapacity, Comparator<? super T> comparator) {
			super(initialCapacity);
			this.comparator = comparator;
		}
		
		@Override
		public boolean add(T element) {
			boolean added = super.add(element);
			if (added) {
				List<T> list = new ArrayList<>(this);
				list.sort(comparator);
				clear();
				super.addAll(list);
			}
			return added;
		}
	}
	
	public List<List<JavaClass>> internalBatches(List<JavaClass> classes) {
		List<DepInfo> deps = sumDependencies(classes);
		Set<JavaClass> added = new SortedHashSet<>(classes.size(), new Comparator<JavaClass>() {
			@Override
			public int compare(JavaClass o1, JavaClass o2) {
				return Integer.compare(o1.getTotalDepsCount(), o2.getTotalDepsCount());
			}
		});
		
		Comparator<JavaClass> cmpDepSize = new Comparator<JavaClass>() {
			@Override
			public int compare(JavaClass o1, JavaClass o2) {
				return Integer.compare(o1.getTotalDepsCount(), o2.getTotalDepsCount());
			}
		};
		List<List<JavaClass>> result = new ArrayList<>();
		List<JavaClass> mergedBatch = new ArrayList<>(MERGED_BATCH_SIZE);
		
		for (DepInfo depInfo : deps) {
			JavaClass cls = depInfo.getCls();
			if (!added.add(cls)) {
				continue;
			}
			int depsSize = cls.getTotalDepsCount();
			
			if (depsSize == 0) {
				// add classes without dependencies in merged batch
				mergedBatch.add(cls);
				if (mergedBatch.size() >= MERGED_BATCH_SIZE) {
					result.add(new ArrayList<>(mergedBatch));
					mergedBatch.clear();
				}
			} else {
				/* Traditional Method instead of  Lambda Expression*/
				/* By @developer-krushna*/
				List<JavaClass> batch = new ArrayList<>(depsSize + 1);
				for (JavaClass dep : cls.getDependencies()) {
					JavaClass topDep = dep.getTopParentClass();
					if (!added.contains(topDep)) {
						batch.add(topDep);
						added.add(topDep);
					}
				}
				
				// Custom sorting logic without using sort method or lambda expression
				for (int i = 0; i < batch.size(); i++) {
					for (int j = i + 1; j < batch.size(); j++) {
						JavaClass dep1 = batch.get(i);
						JavaClass dep2 = batch.get(j);
						if (cmpDepSize.compare(dep1, dep2) > 0) {
							// Swap elements in the list
							JavaClass temp = batch.get(i);
							batch.set(i, batch.get(j));
							batch.set(j, temp);
						}
					}
				}
				
				batch.add(cls);
				result.add(new ArrayList<>(batch));
			}
		}
		
		if (!mergedBatch.isEmpty()) {
			result.add(new ArrayList<>(mergedBatch));
		}
		
		if (DEBUG_BATCHES) {
			dumpBatchesStats(classes, result, deps);
		}
		
		return result;
	}
	
	private static List<DepInfo> sumDependencies(List<JavaClass> classes) {
		List<DepInfo> deps = new ArrayList<>(classes.size());
		for (JavaClass cls : classes) {
			int count = 0;
			for (JavaClass dep : cls.getDependencies()) {
				count += 1 + dep.getTotalDepsCount();
			}
			deps.add(new DepInfo(cls, count));
		}
		Collections.sort(deps);
		return deps;
	}
	
	private static final class DepInfo implements Comparable<DepInfo> {
		private final JavaClass cls;
		private final int depsCount;
		
		private DepInfo(JavaClass cls, int depsCount) {
			this.cls = cls;
			this.depsCount = depsCount;
		}
		
		public JavaClass getCls() {
			return cls;
		}
		
		public int getDepsCount() {
			return depsCount;
		}
		
		@Override
		public int compareTo(@NotNull DecompilerScheduler.DepInfo o) {
			int deps = Integer.compare(depsCount, o.depsCount);
			if (deps == 0) {
				return cls.getClassNode().compareTo(o.cls.getClassNode());
			}
			return deps;
		}
		
		@Override
		public String toString() {
			return cls + ":" + depsCount;
		}
	}
	
	private static List<List<JavaClass>> buildFallback(List<JavaClass> classes) {
		// Custom sorting logic without using sort method or lambda expression
		for (int i = 0; i < classes.size(); i++) {
			for (int j = i + 1; j < classes.size(); j++) {
				JavaClass c1 = classes.get(i);
				JavaClass c2 = classes.get(j);
				if (Integer.compare(c1.getClassNode().getTotalDepsCount(), c2.getClassNode().getTotalDepsCount()) > 0) {
					// Swap elements in the list
					JavaClass temp = classes.get(i);
					classes.set(i, classes.get(j));
					classes.set(j, temp);
				}
			}
		}
		
		List<List<JavaClass>> result = new ArrayList<>();
		for (JavaClass javaClass : classes) {
			List<JavaClass> innerList = Collections.singletonList(javaClass);
			result.add(innerList);
		}
		
		return result;
	}
	private void dumpBatchesStats(List<JavaClass> classes, List<List<JavaClass>> result, List<DepInfo> deps) {
		int clsInBatches = 0;
		int totalBatchSize = result.size();
		double totalBatchSizeDouble = totalBatchSize;
		
		for (List<JavaClass> batch : result) {
			clsInBatches += batch.size();
		}
		
		double avg = totalBatchSize > 0 ? clsInBatches / totalBatchSizeDouble : -1;
		int maxSingleDeps = 0;
		int maxSubDeps = 0;
		
		for (JavaClass javaClass : classes) {
			maxSingleDeps = Math.max(maxSingleDeps, javaClass.getTotalDepsCount());
		}
		
		for (DepInfo depInfo : deps) {
			maxSubDeps = Math.max(maxSubDeps, depInfo.getDepsCount());
		}
		
		LOG.info("Batches stats:"
		+ "\n input classes: " + classes.size()
		+ ",\n classes in batches: " + clsInBatches
		+ ",\n batches: " + totalBatchSize
		+ ",\n average batch size: " + String.format("%.2f", avg)
		+ ",\n max single deps count: " + maxSingleDeps
		+ ",\n max sub deps count: " + maxSubDeps);
	}
	
	private static void check(List<List<JavaClass>> result, List<JavaClass> classes) {
		int classInBatches = 0;
		for (List<JavaClass> batch : result) {
			classInBatches += batch.size();
		}
		
		if (classes.size() != classInBatches) {
			throw new JadxRuntimeException(
			"Incorrect number of classes in result batch: " + classInBatches + ", expected: " + classes.size());
		}
	}
}
