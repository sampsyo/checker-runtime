package jill.rt;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.Stack;
import java.util.Map;
import java.util.HashMap;
import java.util.WeakHashMap;
import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;

import java.util.List;

import java.lang.reflect.Array;
import java.lang.reflect.Field;

import java.io.FileWriter;
import java.io.IOException;

class CreationInfo {
	public Object creator;
	public String type;
	public int preciseSize;
	public int approxSize;
}


class ApproximationInformation {
	public long created;
	public List<Long> read;
	public List<Long> write;
	public long collected;
	public String type;
	public boolean heap;
	
	// to safe a bit of space we only store the creation time.
	ApproximationInformation(long t, String type, boolean heap,
	                         int preciseSize, int approxSize) {
		created = t;
		read = new LinkedList<Long>();
		write= new LinkedList<Long>();
		this.type = type;
		this.heap = heap;
		this.preciseSize = preciseSize;
		this.approxSize = approxSize;
	}
}

class RuntimeDefault implements Runtime {
	
	// This map *only* contains approximate objects. That is,
	// info.get(???).approx == true
	private Map<Object, ApproximationInformation> info = new WeakHashMap<Object, ApproximationInformation>();
	
	// This "parallel" map is used just to receive object finalization events.
	// Phantom references can't be dereferenced, so they can't be used to look
	// up information. But there are the only way to truly know exactly when
	// an object is about to be deallocated. This map contains *all* objects,
	// even precise ones.
	private Map<PhantomReference, ApproximationInformation> phantomInfo =
	    new HashMap<PhantomReference, ApproximationInformation>();
	private ReferenceQueue referenceQueue = new ReferenceQueue();
	
	long startup;
	
	@Override
	public PhantomReference setType(
	    Object o, String type
	) {
		if (debug) {
			System.out.println("EPAj: Add object " + System.identityHashCode(o) + " to system.");
		}
		long time = System.currentTimeMillis();
		ApproximationInformation infoObj =
		    new ApproximationInformation(time, type, heap,
		                                 preciseSize, approxSize);
		PhantomReference phantomRef = new PhantomReference(o, referenceQueue);
		    
		// Add to bookkeeping maps.
	    synchronized (this) {
            info.put(o, infoObj);
    		phantomInfo.put(phantomRef, infoObj);
    	}
    	
    	return phantomRef;
	}

	@Override
	public boolean getType(Object o) {
        String type;
		synchronized (this) {
            type = info.get(o).type;
		}
		return approx;
	}

    public PrecisionRuntimeDefault() {
        super();
        
        startup = System.currentTimeMillis();
        
        final Thread deallocPollThread = new Thread(new Runnable() {
            public void run() {
                deallocPoll();
            }
        });
        deallocPollThread.setDaemon(true); // Automatically shut down.
        deallocPollThread.start();
        
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                cleanUpObjects();
                dumpCounts();
            }
        }));
    }

    /**
     * Mapping Thread IDs to stacks of CreationInfo objects.
     */
    Map<Long, Stack<CreationInfo>> creations = new HashMap<Long, Stack<CreationInfo>>();
    
	@Override
	public boolean beforeCreation(Object creator, String type) {
		CreationInfo c = new CreationInfo();
		c.creator = creator;
		c.approx = approx;
		c.preciseSize = preciseSize;
		c.approxSize = approxSize;
		
		long tid = Thread.currentThread().getId();
		
		Stack<CreationInfo> stack = creations.get(tid);
		if (stack==null) {
			stack = new Stack<CreationInfo>();
		}
		stack.push(c);
		creations.put(tid, stack);
		
		return true;
	}

	@Override
	public boolean enterConstructor(Object created) {
		Stack<CreationInfo> stack = creations.get(Thread.currentThread().getId());
		
		if (stack==null) {
			// probably instantiated from non-EPAj code
			return false;
		}
		
		if (stack.size()<=0) {
			// probably instantiaded from non-EPAj code
			return false;
		}
		
		CreationInfo c = stack.pop();

		// we cannot compare c.creator; we assume that there is no thread interleaving
		// between the call of beforeCreation and enterConstructor.
		// TODO: some methods should probably be synchronized, but I think this
		// wouldn't help against this particular problem.
		this.setApproximate(created, c.approx, true,
		                    c.preciseSize, c.approxSize);
		
		return true;
	}

	@Override
	public boolean afterCreation(Object creator, Object created) {
		Stack<CreationInfo> stack = creations.get(Thread.currentThread().getId());
		// Could stack ever be null? I guess not, b/c "afterC" is only called, if "beforeC" was called earlier.
		
		if (stack.size()<=0) {
			// no worries, the stack must have been emptied in enterConstructor
			return false;
		}
		
		CreationInfo c = stack.peek();
		
		if (c.creator == creator) {
			this.setType(created, c.approx, null);
            stack.pop();
		} else {
			// if the creators do not match, the entry was already removed.
		}
		
		return true;
	}
	
	@Override
	public <T> T wrappedNew(boolean before, T created, Object creator) {
		afterCreation(creator, created);
		return created;
	}
	
	@Override
	public <T> T newArray(T created, int dims, String elType) {
	    int elems = 1;
	    Object arr = created;
	    for (int i = 0; i < dims; ++i) {
	        elems *= Array.getLength(arr);
	        if (Array.getLength(arr) == 0)
	            break;
	        if (i < dims - 1)
	            arr = Array.get(arr, 0);
	    }
	    
	    if (!approx) {
	        preciseElSize += approxElSize;
	        approxElSize = 0;
	    }
	    
	    this.setApproximate(created, false, true,
	                        preciseElSize*elems, approxElSize*elems);
	    
	    return created;
	}

	// Object finalization calls.
	@Override
	public synchronized void endLifetime(PhantomReference ref) {
	    ApproximationInformation infoObj;
	    if (phantomInfo.containsKey(ref)) {
	        infoObj = phantomInfo.get(ref);
	    } else {
	        // Already collected! Do nothing.
	        return;
	    }
	    infoObj.collected = System.currentTimeMillis();
        
        // Log this lifetime at an object granularity.
        String memPart = infoObj.heap ? "heap" : "stack";
        long duration = infoObj.collected - infoObj.created;
        countFootprint(memPart + "-objects", infoObj.approx, duration);
        
        // Log memory usage in byte-seconds.
        countFootprint(memPart + "-bytes", false,
            infoObj.preciseSize * duration);
        countFootprint(memPart + "-bytes", true,
            infoObj.approxSize  * duration);
        
        if (debug) {
            System.out.println("EPAj: object collected after " + duration +
                        " (" + (infoObj.approx ? "A" : "P") + ", " +
                        memPart + ")");
        }
	}
	// A thread that waits for finalizations.
	private void deallocPoll() {
	    while (true) {
	        PhantomReference ref = null;
	        try {
	            ref = (PhantomReference)(referenceQueue.remove());
	        } catch (InterruptedException exc) {
	            // Thread shut down.
	            if (debug)
    	            System.out.println("EPAj: dealloc thread interrupted!");
	            return;
	        }
	        
	        endLifetime(ref);
	    }
	}
	// Called on shutdown to collect all remaining objects.
	private synchronized void cleanUpObjects() {
	    if (debug)
	        System.out.println("EPAj: objects remaining at shutdown: " +
	                           phantomInfo.size());
	    for (Map.Entry<PhantomReference, ApproximationInformation> kv :
	                                        phantomInfo.entrySet()) {
	        endLifetime(kv.getKey());
	    }
	}
	
	
	protected String opSymbol(ArithOperator op) {
	    switch (op) {
	    case PLUS: return "+";
	    case MINUS: return "-";
	    case MULTIPLY: return "*";
	    case DIVIDE: return "/";
	    case BITXOR: return "^";
	    }
	    return "(unknown)";
	}
	
	// Simulated operations.
	
    
    // This is a little incongruous, but this just counts some integer
    // operations that we don't want to instrument but are always done
    // precisely.
    @Override
    public <T> T countLogicalOp(T value) {
    	countOperation("INTlogic", false);
    	return value;
    }
	
	@SuppressWarnings("unchecked")
	public Number binaryOp(Number lhs, Number rhs, ArithOperator op, NumberKind nk, boolean approx) {
	    countOperation(nk + opSymbol(op), approx);
        
        // Prevent divide-by-zero on approximate data.
        if (approx && op == ArithOperator.DIVIDE && rhs.equals(0)) {
            switch (nk) {
            case DOUBLE:
                return Double.NaN;
            case FLOAT:
                return Float.NaN;
            case LONG:
                return new Long(0);
            case INT:
            case BYTE:
            case SHORT:
                return new Integer(0);
            }
        }

	    switch (nk) {
	    case DOUBLE:
	        switch (op) {
	        case PLUS:
	            return (Double)(lhs.doubleValue() + rhs.doubleValue());
	        case MINUS:
	            return (Double)(lhs.doubleValue() - rhs.doubleValue());
	        case MULTIPLY:
	            return (Double)(lhs.doubleValue() * rhs.doubleValue());
	        case DIVIDE:
	            return (Double)(lhs.doubleValue() / rhs.doubleValue());
	        }
	    case FLOAT:
	        switch (op) {
	        case PLUS:
	            return (Float)(lhs.floatValue() + rhs.floatValue());
	        case MINUS:
	            return (Float)(lhs.floatValue() - rhs.floatValue());
	        case MULTIPLY:
	            return (Float)(lhs.floatValue() * rhs.floatValue());
	        case DIVIDE:
	            return (Float)(lhs.floatValue() / rhs.floatValue());
	        }
	    case LONG:
	        switch (op) {
	        case PLUS:
	            return (Long)(lhs.longValue() + rhs.longValue());
	        case MINUS:
	            return (Long)(lhs.longValue() - rhs.longValue());
	        case MULTIPLY:
	            return (Long)(lhs.longValue() * rhs.longValue());
	        case DIVIDE:
	            return (Long)(lhs.longValue() / rhs.longValue());
	        }
	    case INT:
	    case BYTE:
	    case SHORT:
	        switch (op) {
	        case PLUS:
	            return (Integer)(lhs.intValue() + rhs.intValue());
	        case MINUS:
	            return (Integer)(lhs.intValue() - rhs.intValue());
	        case MULTIPLY:
	            return (Integer)(lhs.intValue() * rhs.intValue());
	        case DIVIDE:
	            return (Integer)(lhs.intValue() / rhs.intValue());
	        case BITXOR:
	            return (Integer)(lhs.intValue() ^ rhs.intValue());
	        }
	    }
	    System.out.println("binary operation failed!");
	    return null;
	}
	
	
	// Look for a field in a class hierarchy.
	protected Field getField(Class class_, String name) {
		while (class_ != null) {
			try {
				return class_.getDeclaredField(name);
			} catch (NoSuchFieldException x) {
				class_ = class_.getSuperclass();
			}
		}
		System.out.println("reflection error! field not found: " + name);
		return null;
	}
	
	// Simulated accesses.
	public <T> T storeValue(T value, boolean approx, MemKind kind) {
	    countOperation("store" + kind, approx);
	    return value;
	}
	
	public <T> T loadValue(T value, boolean approx, MemKind kind) {
	    countOperation("load" + kind, approx);
	    return value;
	}
	
	public <T> T loadLocal(Reference<T> ref, boolean approx) {
		return loadValue(ref.value, approx, MemKind.VARIABLE);
	}
	public <T> T loadArray(Object array, int index, boolean approx) {
		return loadValue((T)Array.get(array, index), approx, MemKind.ARRAYEL);
	}
	public <T> T loadField(Object obj, String fieldname, boolean approx) {
		try {
	        
			// In static context, allow client to call this method with a Class
	        // object instead of an instance.
	        Class class_;
	        if (obj instanceof Class) {
	            class_ = (Class)obj;
	            obj = null;
	        } else {
	            class_ = obj.getClass();
	        }
	        Field field = getField(class_, fieldname);
	        field.setAccessible(true);
	        
	        // in = obj.fieldname;
	        T val = (T)field.get(obj);
	        
			return loadValue(val, approx, MemKind.FIELD);
	    
	    } catch (IllegalArgumentException x) {
	        System.out.println("reflection error!");
	        return null;
	    } catch (IllegalAccessException x) {
	        System.out.println("reflection error!");
	        return null;
	    }
	}
	public <T> T storeLocal(Reference<T> ref, boolean approx, T rhs) {
		ref.value = storeValue(rhs, approx, MemKind.VARIABLE);
		return ref.value;
	}
	public <T> T storeArray(Object array, int index, boolean approx, T rhs) {
		T val = storeValue(rhs, approx, MemKind.ARRAYEL);
		Array.set(array, index, val);
		return val;
	}
	public <T> T storeField(Object obj, String fieldname, boolean approx, T rhs) {
		T val = storeValue(rhs, approx, MemKind.FIELD);
		
		try {
	        
			// In static context, allow client to call this method with a Class
	        // object instead of an instance.
	        Class class_;
	        if (obj instanceof Class) {
	            class_ = (Class)obj;
	            obj = null;
	        } else {
	            class_ = obj.getClass();
	        }
	        Field field = getField(class_, fieldname);
	        field.setAccessible(true);
	        
	        // obj.fieldname = val;
    	    field.set(obj, val);
    	    
	    } catch (IllegalArgumentException x) {
	        System.out.println("reflection error: illegal argument");
	        return null;
	    } catch (IllegalAccessException x) {
	        System.out.println("reflection error: illegal access");
	        return null;
	    }
		
		return val;
	}
	
	// Fancier assignments.
	public <T extends Number> T assignopLocal(
	    Reference<T> var,
	    ArithOperator op,
	    Number rhs,
	    boolean returnOld, 
	    NumberKind nk,
	    boolean approx
	) {
		Number tmp = loadLocal(var, approx);
	    Number res = binaryOp(var.value, rhs, op, nk, approx);
	    storeLocal(var, approx, (T)res);
	    if (returnOld)
    	    return (T)tmp;
    	else
    	    return (T)res;
	}
	
	private Number makeKind(Number num, NumberKind nk) {
	    Number converted = null;
	    switch (nk) {
	    case DOUBLE:
	    	converted = num.doubleValue();
	    	break;
	    case FLOAT:
	    	converted = num.floatValue();
	    	break;
	    case LONG:
	    	converted = num.longValue();
	    	break;
	    case INT:
	    	converted = num.intValue();
	    	break;
	    case SHORT:
	    	converted = num.shortValue();
	    	break;
	    case BYTE:
	    	converted = num.byteValue();
	    	break;
	    default:
	    	assert false;
	    }
	    return converted;
	}
	
	public <T extends Number> T assignopArray(
	    Object array,
	    int index,
	    ArithOperator op,
	    Number rhs,
	    boolean returnOld, 
	    NumberKind nk,
	    boolean approx
	) {
	    Number tmp = (Number)loadArray(array, index, approx);
	    Number res = binaryOp(tmp, rhs, op, nk, approx);
	    storeArray(array, index, approx, (T)makeKind(res, nk));
	    if (returnOld)
    	    return (T)tmp;
    	else
    	    return (T)res;
	}
	
	public <T extends Number> T assignopField(
	    Object obj,
	    String fieldname,
	    ArithOperator op,
	    Number rhs,
	    boolean returnOld,
	    NumberKind nk,
	    boolean approx
	) {
		Number tmp = (Number)loadField(obj, fieldname, approx);
	    Number res = binaryOp(tmp, rhs, op, nk, approx);	    
	    storeField(obj, fieldname, approx, (T)makeKind(res, nk));
	    if (returnOld)
    	    return (T)tmp;
    	else
    	    return (T)res;
	}
}
