package checkers.runtime.instrument;

import java.util.Set;
import java.util.HashSet;

import javax.annotation.processing.ProcessingEnvironment;

import checkers.runtime.InstrumentingChecker;
import checkers.runtime.Instrumentor;

import com.sun.source.util.TreePath;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.code.TypeTags;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.util.List;


public class InstrumentingTranslator<Checker extends InstrumentingChecker> extends ReferencingTranslator<Checker> {
    // Keeps track of expressions that should *not* be instrumented as rvalues
    // (i.e., loads).
    private Set<JCTree.JCExpression> lvalues =
        new HashSet<JCTree.JCExpression>();
    private final Instrumentor instrumentor;

    public InstrumentingTranslator(Checker checker,
                                   ProcessingEnvironment env,
                                   TreePath p,
                                   Instrumentor instrumentor) {
        super(checker, env, p);
        this.instrumentor = instrumentor;
    }

    @Override
    public void visitCase(JCTree.JCCase node) {
        // This is a little bit hacky, but mark "case" patterns as lvalues.
        // They are not, of course, actually lvalues, but they should not be
        // instrumented as loads because they must be constants at the source
        // level (and, also, they're not really loads at the JVM level, are
        // they?).
        lvalues.add(node.pat);

        super.visitCase(node);
    }

    private JCTree.JCExpression boxedTypeExp(Type type, boolean abort) {
        String className;
        if (type.tag == TypeTags.BYTE)
            className = "Byte";
        else if (type.tag == TypeTags.CHAR)
            className = "Character";
        else if (type.tag == TypeTags.SHORT)
            className = "Short";
        else if (type.tag == TypeTags.INT)
            className = "Integer";
        else if (type.tag == TypeTags.LONG)
            className = "Long";
        else if (type.tag == TypeTags.FLOAT)
            className = "Float";
        else if (type.tag == TypeTags.DOUBLE)
            className = "Double";
        else if (type.tag == TypeTags.BOOLEAN)
            className = "Boolean";
        else
        	if (abort)
        		return null;
        	else
        		return maker.Type(type);
        return dotsExp("java.lang." + className);
    }

    private JCTree.JCExpression boxedTypeExp(Type type) {
    	return boxedTypeExp(type, false);
    }

    private JCTree.JCExpression box(JCTree.JCExpression unboxed) {
        JCTree.JCExpression castType = boxedTypeExp(unboxed.type, true);
        if (castType == null) {
            // Don't do anything to non-primitive types.
            return unboxed;
        }
        // Perform cast.
        return maker.TypeCast(castType, unboxed);
    }

    private JCTree.JCExpression unbox(JCTree.JCExpression boxed, Type type) {
        String methName;
        if (type.tag == TypeTags.BYTE)
            methName = "byteValue";
        else if (type.tag == TypeTags.CHAR)
            methName = "charValue";
        else if (type.tag == TypeTags.SHORT)
            methName = "shortValue";
        else if (type.tag == TypeTags.INT)
            methName = "intValue";
        else if (type.tag == TypeTags.LONG)
            methName = "longValue";
        else if (type.tag == TypeTags.FLOAT)
            methName = "floatValue";
        else if (type.tag == TypeTags.DOUBLE)
            methName = "doubleValue";
        else if (type.tag == TypeTags.BOOLEAN)
            methName = "booleanValue";
        else
            return boxed;

        return maker.Apply(
            null,
            maker.Select(boxed, names.fromString(methName)),
            List.<JCTree.JCExpression>nil()
        );
    }

    // Possibly add a cast to ensure that a replacement expression has the
    // same type as the expression it replaces.
    protected JCTree.JCExpression explicitType(JCTree.JCExpression expr,
            JCTree.JCExpression oldExpr) {
        Type type = oldExpr.type;
        if (type == null) // Likely to be problematic...
            return expr;
        else if (type != expr.type)
            return maker.TypeCast(type, expr);
        else
            return expr;
    }

    @Override
    public void visitAssign(JCTree.JCAssign node) {
        lvalues.add(node.lhs);
        super.visitAssign(node);
    }

    @Override
    public void visitAssignop(JCTree.JCAssignOp node) {
        lvalues.add(node.lhs);
        super.visitAssignop(node);
    }

    @Override
    public void visitTypeTest(JCTree.JCInstanceOf node) {
        JCTree.JCExpression out = instrumentor.instInstanceOf(node);
        out = explicitType(out, node);
        attribute(out, node);
        result = out;
    }
    @Override
    public void visitTypeCast(JCTree.JCTypeCast node) {
        JCTree.JCExpression out = instrumentor.instCast(node);
        out = explicitType(out, node);
        attribute(out, node);
        result = out;
    }
}
