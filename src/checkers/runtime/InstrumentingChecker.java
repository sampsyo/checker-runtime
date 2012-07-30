package checkers.runtime;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;

import checkers.basetype.BaseTypeChecker;
import checkers.runtime.instrument.InstrumentingTranslator;

import com.sun.source.util.TreePath;
import com.sun.tools.javac.tree.JCTree;

/**
 * The checker class, which we here abuse to run our instrumentation code at the
 * appropriate time (i.e., after typing the program).
 */
public class InstrumentingChecker extends BaseTypeChecker {
    public static final String DEBUG_FLAG = "jilldbg";
    public static final String VERBOSE_FLAG = "jillverb";

    private boolean debug = false;
    public boolean debug() { return debug; }

    private boolean verbose = false;
    public boolean verbose() { return verbose; }

    public Instrumentor instrumentor;

    // The -Ajilldbg flag prints out debugging information during source
    // translation.
    @Override
    public void initChecker(ProcessingEnvironment env) {
        super.initChecker(env);
        Map<String, String> opts = env.getOptions();
        debug = opts.containsKey(DEBUG_FLAG);
        verbose = opts.containsKey(VERBOSE_FLAG);

        instrumentor = getInstrumentor();
        instrumentor.debug = debug;
    }

    // We manually add the debug flag command-line option rather than using the
    // @SupportedOptions annotation because the annotation isn't inherited.
    @Override
    public Set<String> getSupportedOptions() {
        Set<String> oldOptions = super.getSupportedOptions();
        Set<String> newOptions = new HashSet<String>();
        newOptions.addAll(oldOptions);
        newOptions.add(DEBUG_FLAG);
        newOptions.add(VERBOSE_FLAG);
        return newOptions;
    }

    @Override
    public void typeProcess(TypeElement e, TreePath p) {
        // Run the SourceChecker base behavior *first* to set currentPath,
        // which must be set before BasicAnnotatedTypeFactory is instantiated.
        super.typeProcess(e, p);

        JCTree tree = (JCTree) p.getCompilationUnit(); // or maybe p.getLeaf()?

        InstrumentingTranslator translator = getTranslator(p);
        if (translator == null) {
            return;
        }

        if (debug) {
            System.out.println("Translating from:");
            System.out.println(tree);
        }

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
        return new Instrumentor();
    }
}

