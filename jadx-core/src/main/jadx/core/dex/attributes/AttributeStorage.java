package jadx.core.dex.attributes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.*;
import jadx.api.plugins.input.data.annotations.IAnnotation;
import jadx.api.plugins.input.data.attributes.IJadxAttrType;
import jadx.api.plugins.input.data.attributes.IJadxAttribute;
import jadx.api.plugins.input.data.attributes.JadxAttrType;
import jadx.api.plugins.input.data.attributes.types.AnnotationsAttr;
import jadx.core.utils.Utils;
import jadx.core.utils.exceptions.JadxRuntimeException;

/**
* Storage for different attribute types:
* 1. flags - boolean attribute (set or not)
* 2. attribute - class instance associated with attribute type.
*/
public class AttributeStorage {
	
	static {
		int flagsCount = AFlag.values().length;
		if (flagsCount >= 64) {
			throw new JadxRuntimeException("Try to reduce flags count to 64 for use one long in EnumSet, now " + flagsCount);
		}
	}
	
	private final Set<AFlag> flags;
	private Map<IJadxAttrType<?>, IJadxAttribute> attributes;
	
	public AttributeStorage() {
		flags = EnumSet.noneOf(AFlag.class);
		attributes = Collections.emptyMap();
	}
	
	public AttributeStorage(List<IJadxAttribute> attributesList) {
		this();
		add(attributesList);
	}
	
	public void add(AFlag flag) {
		flags.add(flag);
	}
	
	public void add(IJadxAttribute attr) {
		writeAttributes().put(attr.getAttrType(), attr);
	}
	
	public void add(List<IJadxAttribute> list) {
		Map<IJadxAttrType<?>, IJadxAttribute> map = writeAttributes();
		for (IJadxAttribute attr : list) {
			map.put(attr.getAttrType(), attr);
		}
	}
	
	public <T> void add(IJadxAttrType<AttrList<T>> type, T obj) {
		AttrList<T> list = get(type);
		if (list == null) {
			list = new AttrList<>(type);
			add(list);
		}
		list.getList().add(obj);
	}
	
	public void addAll(AttributeStorage otherList) {
		flags.addAll(otherList.flags);
		writeAttributes().putAll(otherList.attributes);
	}
	
	public boolean contains(AFlag flag) {
		return flags.contains(flag);
	}
	
	public <T extends IJadxAttribute> boolean contains(IJadxAttrType<T> type) {
		return attributes.containsKey(type);
	}
	
	@SuppressWarnings("unchecked")
	public <T extends IJadxAttribute> T get(IJadxAttrType<T> type) {
		return (T) attributes.get(type);
	}
	
	public IAnnotation getAnnotation(String cls) {
		AnnotationsAttr aList = get(JadxAttrType.ANNOTATION_LIST);
		return aList == null ? null : aList.get(cls);
	}
	
	public <T> List<T> getAll(IJadxAttrType<AttrList<T>> type) {
		AttrList<T> attrList = get(type);
		if (attrList == null) {
			return Collections.emptyList();
		}
		return Collections.unmodifiableList(attrList.getList());
	}
	
	public void remove(AFlag flag) {
		flags.remove(flag);
	}
	
	public <T extends IJadxAttribute> void remove(IJadxAttrType<T> type) {
		if (!attributes.isEmpty()) {
			Iterator<Map.Entry<IJadxAttrType<?>, IJadxAttribute>> iterator = attributes.entrySet().iterator();
			while (iterator.hasNext()) {
				Map.Entry<IJadxAttrType<?>, IJadxAttribute> entry = iterator.next();
				if (entry.getKey() == type) {
					iterator.remove();
					break;
				}
			}
		}
	}
	
	public void remove(IJadxAttribute attr) {
		if (!attributes.isEmpty()) {
			IJadxAttrType<? extends IJadxAttribute> type = attr.getAttrType();
			IJadxAttribute a = attributes.get(type);
			if (a == attr) {
				Iterator<Map.Entry<IJadxAttrType<?>, IJadxAttribute>> iterator = attributes.entrySet().iterator();
				while (iterator.hasNext()) {
					Map.Entry<IJadxAttrType<?>, IJadxAttribute> entry = iterator.next();
					if (entry.getKey() == type && entry.getValue() == attr) {
						iterator.remove();
						break;
					}
				}
			}
		}
	}
	
	private Map<IJadxAttrType<?>, IJadxAttribute> writeAttributes() {
		if (attributes.isEmpty()) {
			attributes = new IdentityHashMap<>(5);
		}
		return attributes;
	}
	
	public void clear() {
		flags.clear();
		if (!attributes.isEmpty()) {
			attributes.clear();
		}
	}
	
	public synchronized void unloadAttributes() {
		Iterator<Map.Entry<IJadxAttrType<?>, IJadxAttribute>> iterator = attributes.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<IJadxAttrType<?>, IJadxAttribute> entry = iterator.next();
			if (!entry.getValue().keepLoaded()) {
				iterator.remove();
			}
		}
	}
	public List<String> getAttributeStrings() {
		int size = flags.size() + attributes.size() + attributes.size();
		if (size == 0) {
			return Collections.emptyList();
		}
		List<String> list = new ArrayList<>(size);
		for (AFlag a : flags) {
			list.add(a.toString());
		}
		for (IJadxAttribute a : attributes.values()) {
			list.add(a.toAttrString());
		}
		return list;
	}
	
	public boolean isEmpty() {
		return flags.isEmpty() && attributes.isEmpty();
	}
	
	/* Traditional Method instead of  Lambda Expression*/
	/* By @developer-krushna*/
	
	@Override
	public String toString() {
		List<String> list = getAttributeStrings();
		if (list.isEmpty()) {
			return "";
		}
		
		// Sort the list using Collections.sort() and a custom Comparator
		Collections.sort(list, new Comparator<String>() {
			@Override
			public int compare(String s1, String s2) {
				return s1.compareTo(s2);
			}
		});
		
		// Convert the sorted list to a string
		return "A[" + Utils.listToString(list) + ']';
	}
}
