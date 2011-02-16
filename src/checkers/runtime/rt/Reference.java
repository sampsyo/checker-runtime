package checkers.runtime.rt;

import java.lang.ref.PhantomReference;

public class Reference<T> {
    public T value;
    public boolean primitive; // Did we box a primitive type?
    public PhantomReference<T> phantom;
    public Reference(T value, boolean primitive) {
        this.value = value;
        this.primitive = primitive;
    }
}
