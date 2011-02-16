package checkers.runtime;

import checkers.basetype.BaseTypeChecker;
import checkers.runtime.instrument.*;

import javax.lang.model.element.TypeElement;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.tree.JCTree;
import javax.annotation.processing.ProcessingEnvironment;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * The checker class, which we here abuse to run our instrumentation code at the
 * appropriate time (i.e., after typing the program).
 */
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
        return newOptions;
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
        return new Instrumentor();
    }
}

