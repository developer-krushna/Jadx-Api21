package jadx.core.dex.visitors.usage;
import java.util.*;

class MultiMap<T, U> {
	private final Map<T, Set<U>> map = new HashMap<>();
	/* Traditional Method instead of  Lambda Expression*/
	/* By @developer-krushna*/
	public void add(T key, U value) {
		Set<U> values = map.get(key);
		if (values == null) {
			values = new HashSet<>();
			map.put(key, values);
		}
		values.add(value);
	}
	
	public Set<U> get(T key) {
		Set<U> values = map.get(key);
		return values != null ? values : Collections.emptySet();
	}
}
