package jillexample;

import jill.Instrumentor;
import com.sun.tools.javac.tree.JCTree;

public class ExampleInstrumentor extends Instrumentor {
    @Override
    public JCTree.JCExpression instCast(JCTree.JCTypeCast cast) {
        return super.instCast(cast);
    }

    @Override
    public JCTree.JCExpression instInstanceOf(JCTree.JCInstanceOf expr) {
        return super.instInstanceOf(expr);
    }
}
