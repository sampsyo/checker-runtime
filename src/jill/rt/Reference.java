package jill.rt;

import java.lang.ref.PhantomReference;
import jill.InstrumentingChecker;

public class Reference<T> {
    public T value;
    public boolean approx;
    public boolean primitive; // Did we box a primitive type?
    public PhantomReference phantom;
    public Reference(T value, boolean approx, boolean primitive) {
        this.value = value;
        this.approx = approx;
        this.primitive = primitive;
        int[] sizes = PrecisionChecker.referenceSizes(this);
        phantom = PrecisionRuntimeRoot.impl.setApproximate(
            this, approx, false, sizes[0], sizes[1]
        );
    }
    public void destroy() {
        PrecisionRuntimeRoot.impl.endLifetime(phantom);
    }
}
