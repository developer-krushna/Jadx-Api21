package jadx.api.plugins.input.data;

import com.custom.Consumer;

/**
 * "Sequence consumer" allows getting count of elements available
 */
public interface ISeqConsumer<T> extends Consumer<T> {

	default void init(int count) {
		// no-op implementation
	}
}
