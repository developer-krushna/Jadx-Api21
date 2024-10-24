package jadx.api.plugins.input.data;

import java.io.Closeable;
import com.custom.Consumer;
public interface ILoadResult extends Closeable {

    
	void visitClasses(com.custom.Consumer<IClassData> consumer);

    void visitResources(com.custom.Consumer<IResourceData> consumer);

	boolean isEmpty();
}
