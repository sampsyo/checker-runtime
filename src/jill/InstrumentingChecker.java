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
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.TypeElement;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.tree.JCTree;
import java.lang.annotation.ElementType;
import javax.annotation.processing.ProcessingEnvironment;
import java.util.Map;

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
@SupportedOptions({InstrumentingChecker.DEBUG_FLAG})
public class InstrumentingChecker extends BaseTypeChecker {
    public static final String DEBUG_FLAG = "jilldbg";
    public boolean debug = false;
    public Instrumentor instrumentor;

    // The -Ajilldbg flag prints out debugging information during source
    // translation.
    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        Map<String, String> opts = env.getOptions();
        debug = opts.containsKey(DEBUG_FLAG);
        instrumentor = getInstrumentor();
    }
    
    @Override
    public void typeProcess(TypeElement e, TreePath p) {
        JCTree tree = (JCTree) p.getCompilationUnit(); // or maybe p.getLeaf()?
        
        if (debug) {
            System.out.println("Translating from:");
            System.out.println(tree);
        }

        // Run the checker next and ensure everything worked out.
        super.typeProcess(e, p);

        InstrumentingTranslator translator = getTranslator(p);
        instrumentor.beginInstrumentation(translator);
        tree.accept(translator);
		
        if (debug) {
            System.out.println("Translated to:");
            System.out.println(tree);
        }
    }

    public InstrumentingTranslator getTranslator(TreePath path) {
        return new InstrumentingTranslator(this, processingEnv, path,
                                           instrumentor);
    }
    public Instrumentor getInstrumentor() {
        return new Instrumentor(debug);
    }
}

