package jill.rt;

public class RuntimeRoot {

	public static final Runtime impl;

	static {
		System.out.println("Loading PrecisionRuntimeRoot");
		
		String runtimeClass = System.getProperty("PrecisionRuntime");
		Runtime newimpl;
		if (runtimeClass != null) {
			/* try to create an instance of this class */
			try {
				newimpl = (Runtime)
					Class.forName(runtimeClass).newInstance();
			} catch (Exception e) {
				System.err.println("WARNING: the specified Precision Runtime Implementation class ("
								+ runtimeClass
								+ ") could not be instantiated, using the default instead.");
				// System.err.println(e);
				newimpl = new RuntimeDefault();
			}
		} else {
			newimpl = new RuntimeDefault();
		}
		impl = newimpl;
	}
	
	/*
	static dynCall(Object receiver, String name, Object[] args) {
		look at precision of receiver, then either call name_PREC or name_APPROX
	}
	
	static initObject(Object o, boolean precision) {
		get class, iterate through fields, set precision of fields
	}
	*/
}
