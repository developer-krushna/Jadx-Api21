package jadx.api.plugins.input.data.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.custom.Converter;
import jadx.plugins.input.dex.sections.DexClassData;
import jadx.api.plugins.input.data.IFieldData;
import jadx.api.plugins.input.data.ISeqConsumer;

public class ListConsumer<T, R> implements ISeqConsumer<T> {
    private final Converter<T, R> converter;
    private List<R> list;

    public ListConsumer(Converter<T, R> converter) {
        this.converter = converter;
    }

    @Override
    public void init(int count) {
        list = count == 0 ? Collections.emptyList() : new ArrayList<>(count);
    }

    @Override
    public void accept(T t) {
        list.add(converter.convert(t));
    }

    public List<R> getResult() {
        if (list == null) {
            // init not called
            return Collections.emptyList();
        }
        return list;
    }
}