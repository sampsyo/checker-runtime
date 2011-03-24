package checkers.runtime.rt;

import java.lang.ref.PhantomReference;

// TODO: there is still precise/approx stuff in here!
public interface Runtime {
    /**
     * This method is called immediately before an object creation.
     * The runtime keeps a stack of (creator, approx) pairs, per thread ID.
     *
     * @param creator The object that is instantiating the new object.
     * @param approx True, iff the new object should be approximate.
     * @param preciseSize The precise memory (in bytes) used by the object.
     * @param approxSize The approximate memory used by the object.
     */
    boolean beforeCreation(Object creator, boolean approx,
                           int preciseSize, int approxSize);

    /**
     * Insert the newly created object into the runtime system.
     * Use the top of the stack of the current tread ID to determine, what
     * precision to use.
     *
     * TODO: How do we detect an instantiation of an EnerJ class, by non-EnerJ code?
     * The stack will not contain the precision information and by just taking the
     * top of the stack we mess up the order.
     *
     * @param created The newly created object.
     */
    boolean enterConstructor(Object created);

    /**
     * If we instantiated a non-EnerJ class, the top of the stack will be unchanged.
     * We can detect this through the matching creator reference.
     *
     * @param creator The object that instantiated the new object.
     * @param created The newly created object.
     */
    boolean afterCreation(Object creator, Object created);

    /**
     * Wrap an object instantiation with the runtime system calls.
     * RuntimePrecisionTranslator.visitNewClass describes the motivation for this method.
     * TODO: This is a rather low-level method that feels a bit out-of-place in this
     * interface.
     *
     * @param <T> Make the method usable for any object instantiation.
     * @param before The result of the corresponding beforeCreation call.
     * @param created The object that was instantiated.
     * @param creator The "this" object at the point of instantiation.
     * @return The object that was instantiated, i.e. parameter created.
     */
    <T> T wrappedNew(boolean before, T created, Object creator);

    /**
     * Wrap an array initialization.
     *
     * @param <T> The array type (not element type, which may be primitive).
     * @param created The array.
     * @param dims The number of dimensions in the new array.
     * @param approx Whether the component type is, in fact, approximate.
     * @param preciseElSize The precise size of the component type.
     * @param approxElSize The approximate size of the component type.
     */
    <T> T newArray(T created, int dims, boolean approx,
                   int preciseElSize, int approxElSize);

    /**
     * Signal that the object associated with the phantom reference (returned
     * by setApproximate) has been destroyed. Can be used to provide more
     * precise deallocation time information than can be provided by the GC.
     */
    void endLifetime(PhantomReference<Object> ref);

}