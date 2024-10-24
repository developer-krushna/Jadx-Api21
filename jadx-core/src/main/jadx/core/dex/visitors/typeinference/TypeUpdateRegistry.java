package jadx.core.dex.visitors.typeinference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import jadx.core.dex.instructions.InsnType;

public class TypeUpdateRegistry {
	
	private final Map<InsnType, List<ITypeListener>> listenersMap = new EnumMap<>(InsnType.class);
	/* Traditional Method instead of  Lambda Expression*/
	/* By @developer-krushna*/
	public void add(InsnType insnType, ITypeListener listener) {
		List<ITypeListener> listeners = listenersMap.get(insnType);
		if (listeners == null) {
			listeners = new ArrayList<>(3);
			listenersMap.put(insnType, listeners);
		}
		listeners.add(listener);
	}
	@NotNull
	public List<ITypeListener> getListenersForInsn(InsnType insnType) {
		List<ITypeListener> list = listenersMap.get(insnType);
		if (list == null) {
			return Collections.emptyList();
		}
		return list;
	}
}
