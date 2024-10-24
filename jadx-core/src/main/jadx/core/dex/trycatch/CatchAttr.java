package jadx.core.dex.trycatch;

import java.util.Comparator;
import java.util.List;
import java.util.*;

import jadx.api.plugins.input.data.attributes.IJadxAttribute;
import jadx.core.dex.attributes.AType;
import jadx.core.utils.Utils;

public class CatchAttr implements IJadxAttribute {
	
	public static CatchAttr build(List<ExceptionHandler> handlers) {
		// Sort the list using Collections.sort() and a custom Comparator
		Collections.sort(handlers, new Comparator<ExceptionHandler>() {
			@Override
			public int compare(ExceptionHandler handler1, ExceptionHandler handler2) {
				return Integer.compare(handler1.getHandlerOffset(), handler2.getHandlerOffset());
			}
		});
		
		// Return a new CatchAttr object with the sorted list
		return new CatchAttr(handlers);
	}
	private final List<ExceptionHandler> handlers;
	
	private CatchAttr(List<ExceptionHandler> handlers) {
		this.handlers = handlers;
	}
	
	public List<ExceptionHandler> getHandlers() {
		return handlers;
	}
	
	@Override
	public AType<CatchAttr> getAttrType() {
		return AType.EXC_CATCH;
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof CatchAttr)) {
			return false;
		}
		CatchAttr catchAttr = (CatchAttr) o;
		return getHandlers().equals(catchAttr.getHandlers());
	}
	
	@Override
	public int hashCode() {
		return getHandlers().hashCode();
	}
	
	@Override
	public String toString() {
		return "Catch: " + Utils.listToString(getHandlers());
	}
}
