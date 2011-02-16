package checkers.runtime;

import checkers.runtime.instrument.*;

import com.sun.tools.javac.tree.JCTree;

public class Instrumentor {
    protected InstrumentingTranslator translator;
    protected boolean debug;

    public void beginInstrumentation(InstrumentingTranslator translator) {
        this.translator = translator;
    }

    public JCTree.JCExpression instCast(JCTree.JCTypeCast cast) {
        if (debug)
            System.err.println("instrumenting cast: " + cast);
        return cast;
    }

    public JCTree.JCExpression instInstanceOf(JCTree.JCInstanceOf expr) {
        if (debug)
            System.err.println("instrumenting instanceof: " + expr);
        return expr;
    }
}
