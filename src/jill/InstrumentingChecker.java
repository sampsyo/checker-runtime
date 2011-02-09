package jill;

import jill.instrument.*;
import checkers.basetype.BaseTypeChecker;
import checkers.source.SupportedLintOptions;
import checkers.quals.TypeQualifiers;
import checkers.quals.TypeQualifier;
import checkers.quals.SubtypeOf;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.annotation.RetentionPolicy;
import javax.lang.model.element.TypeElement;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.tree.JCTree;
import java.lang.annotation.ElementType;

// We're using a Checker class to insert our instrumentation pass, but we don't
// really have any qualifiers we want to provide. Unfortunately, the Checker
// Framework requires us to provide at least two annotations.
@TypeQualifier
@SubtypeOf({})
@interface DummyQual1 { }
@TypeQualifier
@SubtypeOf({})
@interface DummyQual2 { }

/**
 * The checker class, which we here abuse to run our instrumentation code at the
 * appropriate time (i.e., after typing the program).
 */
@TypeQualifiers({DummyQual1.class, DummyQual2.class})
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

