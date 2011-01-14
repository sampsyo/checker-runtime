package jill.rt;

import java.lang.ref.PhantomReference;
import checkers.types.AnnotatedTypeMirror;

public interface Runtime {
	/**
	 * Set whether the referenced object is approximate.
	 * 
	 * @param o Object that should be catalogged.
	 * @param approx Whether the object is approximate.
	 * @param heap Whether the object is on the heap (as opposed to the stack).
  	 * @param preciseSize The precise memory (in bytes) used by the object.
  	 * @param approxSize The approximate memory used by the object.
	 */
	PhantomReference setType(
	    Object o, TypeInfo type, boolean heap,
	    int preciseSize, int approxSize
	);

	/**
	 * Query whether the referenced object is approximate.
	 * 
	 * @param o The object to test.
	 * @return True, iff the referenced object is approximate.
	 */
    AnnotatedTypeMirror getType(Object o);

	/**
	 * This method is called immediately before an object creation.
	 * The runtime keeps a stack of (creator, approx) pairs, per thread ID.
	 * 
	 * @param creator The object that is instantiating the new object.
	 * @param approx True, iff the new object should be approximate.
 	 * @param preciseSize The precise memory (in bytes) used by the object.
 	 * @param approxSize The approximate memory used by the object.
	 */
	boolean beforeCreation(Object creator, AnnotatedTypeMirror newType,
	                       int preciseSize, int approxSize);
	
	/**
	 * Insert the newly created object into the runtime system.
	 * Use the top of the stack of the current tread ID to determine, what
	 * precision to use.
	 * 
	 * TODO: How do we detect an instantiation of an EPAj class, by non-EPAj code?
	 * The stack will not contain the precision information and by just taking the
	 * top of the stack we mess up the order.
	 * 
	 * @param created The newly created object.
	 */
	boolean enterConstructor(Object created);
	
	/**
	 * If we instantiated a non-EPAj class, the top of the stack will be unchanged.
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
	void endLifetime(PhantomReference ref);


	// void setApproximate(Object o, String field);
	// boolean isApproximate(Object o, String field);
	
	// Simulated operations.
	public enum NumberKind { INT, BYTE, DOUBLE, FLOAT, LONG, SHORT }
	public enum ArithOperator { PLUS, MINUS, MULTIPLY, DIVIDE, BITXOR }
	public Number binaryOp(Number lhs, Number rhs, ArithOperator op, NumberKind nk, boolean approx);
	public <T> T countLogicalOp(T value);
	
	// Instrumented memory accesses.
	public enum MemKind { VARIABLE, FIELD, ARRAYEL }
	public <T> T storeValue(T value, boolean approx, MemKind kind);
	public <T> T loadValue(T value, boolean approx, MemKind kind);
	public <T> T loadLocal(Reference<T> ref, boolean approx);
	public <T> T loadArray(Object array, int index, boolean approx);
	public <T> T loadField(Object obj, String fieldname, boolean approx);
	public <T> T storeLocal(Reference<T> ref, boolean approx, T rhs);
	public <T> T storeArray(Object array, int index, boolean approx, T rhs);
	public <T> T storeField(Object obj, String fieldname, boolean approx, T rhs);
	
	// Fancier assignments.
	public <T extends Number> T assignopLocal(Reference<T> var, ArithOperator op, Number rhs, boolean returnOld, NumberKind nk, boolean approx);
	public <T extends Number> T assignopArray(Object array, int index, ArithOperator op, Number rhs, boolean returnOld, NumberKind nk, boolean approx);
	public <T extends Number> T assignopField(Object obj, String fieldname, ArithOperator op, Number rhs, boolean returnOld, NumberKind nk, boolean approx);
}
