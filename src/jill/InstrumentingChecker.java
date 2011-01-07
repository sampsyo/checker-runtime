package jill;

import jill.instrument.*;

/**
 * The checker class, which we here abuse to run our instrumentation code at the
 * appropriate time (i.e., after typing the program).
 */
@TypeQualifiers({})
@SupportedLintOptions({InstrumentingChecker.DEBUG})
public class InstrumentingChecker extends BaseTypeChecker {
    public static final boolean DEBUG_DEFAULT = false;
    public static final String DEBUG = "debug";

    public boolean debug() {
        return getLintOption(DEBUG, DEBUG_DEFAULT);
    }
    
    @Override
    public void typeProcess(TypeElement e, TreePath p) {
        JCTree tree = (JCTree) p.getCompilationUnit(); // or maybe p.getLeaf()?
        
        if (debug()) {
            System.out.println("Translating from:");
            System.out.println(tree);
        }

        // Run the checker next and ensure everything worked out.
        super.typeProcess(e, p);

        tree.accept(new InstrumentingTranslator(this, processingEnv, p));
		
        if (debug()) {
            System.out.println("Translated to:");
            System.out.println(tree);
        }
    }
}
