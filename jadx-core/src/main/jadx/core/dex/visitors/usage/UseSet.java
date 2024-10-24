package jadx.core.dex.visitors.usage;
import java.util.*;

class UseSet<T, U> implements Iterable<T> {
    private final Set<T> keys = new HashSet<>();
    private final MultiMap<T, U> map = new MultiMap<>();

    public void add(T key, U value) {
        keys.add(key);
        map.add(key, value);
    }

    public Set<U> get(T key) {
        return map.get(key);
    }

    @Override
    public Iterator<T> iterator() {
        return keys.iterator();
    }
}
