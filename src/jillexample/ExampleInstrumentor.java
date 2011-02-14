package jillexample;

import jill.Instrumentor;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;

public class ExampleInstrumentor extends Instrumentor {
    @Override
    public JCTree.JCExpression instCast(JCTree.JCTypeCast cast) {
        JCTree.JCExpression call =
            translator.maker.Apply(
                null,
                translator.dotsExp("jillexample.ExampleRuntime.didCast"),
                List.<JCTree.JCExpression>of(cast)
            );
        return call;
    }

    @Override
    public JCTree.JCExpression instInstanceOf(JCTree.JCInstanceOf expr) {
        return super.instInstanceOf(expr);
    }
}
