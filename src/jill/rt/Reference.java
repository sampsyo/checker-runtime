package jill.rt;

import java.lang.ref.PhantomReference;
import jill.InstrumentingChecker;

public class Reference<T> {
    public T value;
    public boolean primitive; // Did we box a primitive type?
    public PhantomReference phantom;
    public Reference(T value, boolean primitive) {
        this.value = value;
        this.primitive = primitive;
    }
}
