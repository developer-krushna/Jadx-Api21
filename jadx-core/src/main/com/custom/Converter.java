package com.custom;

import jadx.api.plugins.input.data.IMethodData;
import jadx.api.plugins.input.data.IFieldData;
import jadx.api.plugins.input.data.attributes.IJadxAttribute;
// by developer-krushna
public interface Converter<T, R> {
    R convert(T input);
}
