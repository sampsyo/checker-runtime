package checkers.runtime.jillexample;

import checkers.runtime.Instrumentor;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;

public class ExampleInstrumentor extends Instrumentor {
    final static String rtclass = checkers.runtime.jillexample.ExampleRuntime.class.getName();

    @Override
    public JCTree.JCExpression instCast(JCTree.JCTypeCast cast) {
        JCTree.JCExpression call =
            translator.maker.Apply(
                null,
                translator.dotsExp(rtclass + ".didCast"),
                List.<JCTree.JCExpression>of(cast)
            );
        return call;
    }

    @Override
    public JCTree.JCExpression instInstanceOf(JCTree.JCInstanceOf expr) {
        return super.instInstanceOf(expr);
    }
}
