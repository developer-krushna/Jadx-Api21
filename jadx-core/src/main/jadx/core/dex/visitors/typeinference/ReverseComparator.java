package jadx.core.dex.visitors.typeinference;

import java.util.Comparator;

public class ReverseComparator<T> implements Comparator<T> {
	private final Comparator<T> comparator;
	
	public ReverseComparator(Comparator<T> comparator) {
		this.comparator = comparator;
	}
	
	@Override
	public int compare(T o1, T o2) {
		// Reversed comparison logic
		return comparator.compare(o2, o1);
	}
}
