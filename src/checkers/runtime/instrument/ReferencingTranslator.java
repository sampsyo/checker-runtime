package checkers.runtime.instrument;

import checkers.runtime.InstrumentingChecker;

import com.sun.source.util.TreePath;
import javax.annotation.processing.ProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Flags;

import java.util.Set;
import java.util.HashSet;


// Tightly coupled with the SimulationTranslator, this pass replaces all local
// variable references (and method parameters) with reference objects. This
// allows us to simulate pass-by-reference for instrumentation of local variable
// reads and writes.
public class ReferencingTranslator<Checker extends InstrumentingChecker> extends HelpfulTreeTranslator<Checker> {
    public ReferencingTranslator(Checker checker,
                                 ProcessingEnvironment env,
                                 TreePath p) {
        super(checker, env, p);
    }

    protected static String REFERENCE_CLASS = checkers.runtime.rt.Reference.class.getName();
    protected static final String VALUE_FIELD = "value";
    protected static final String SYM_SUFFIX = "__REF__";

    protected Set<JCTree.JCVariableDecl> replDecls = new
        HashSet<JCTree.JCVariableDecl>();
    protected Set<JCTree.JCExpression> instrumented = new
        HashSet<JCTree.JCExpression>();
    protected Set<JCTree.JCStatement> constructorCalls = new
        HashSet<JCTree.JCStatement>();
    protected boolean skipIdents = false;

    // Generate a replacement variable definition for a given variable
    // declaration. If 'inited' is true, then the reference is given an
    // in initial value (i.e., it it set to the initial value of the
    // original variable).
    private JCTree.JCVariableDecl replVarDef(JCTree.JCVariableDecl tree,
                                             boolean inited) {
        // Skip the previously-added parameter replacement declarations.
        if (replDecls.contains(tree))
            return null;

        // Only work on local variables.
        if (!(tree.sym.owner instanceof Symbol.MethodSymbol))
            return null;

        // Instead of declaring "T x", we declare "Reference<T> x__REF__".
        JCTree.JCExpression boxedOldType = tree.vartype;
        boolean primitive = false;
        if (tree.sym.type.isPrimitive()) {
            boxedOldType = dotsExp(jctypes.boxedClass(tree.type).toString());
            primitive = true;
        }
        JCTree.JCExpression newType =
            maker.TypeApply(dotsExp(REFERENCE_CLASS),
                            List.of(boxedOldType));

        // Make a new variable declaration.
        JCTree.JCVariableDecl decl = maker.VarDef(
            tree.mods,
            names.fromString(tree.name + SYM_SUFFIX),
            newType,
            tree.init
        );

        // Initialize with the value of the old variable.
        JCTree.JCExpression init;
        if (tree.init != null || inited)
            init = maker.Ident(tree.name);
        else
            init = nullExp();
        JCTree.JCExpression newInit = createNewInitializer(tree, boxedOldType, newType, init, primitive);
        decl.init = newInit;

        //attribute(decl, tree); // Ensure that we enter the new variable.
        return decl;
    }

    public JCTree.JCExpression createNewInitializer(JCTree.JCVariableDecl tree, JCTree.JCExpression boxedOldType,
            JCTree.JCExpression newType, JCTree.JCExpression init, boolean primitive) {

        JCTree.JCExpression newInit = maker.NewClass(
                null,
                List.of(boxedOldType),
                newType,
                List.<JCTree.JCExpression>of(
                    init,
                    boolExp(primitive)
                ),
                null
            );
        return newInit;
    }


    private JCTree.JCVariableDecl replVarDef(JCTree.JCVariableDecl tree) {
        return replVarDef(tree, false);
    }

    @Override
    public void visitBlock(JCTree.JCBlock tree) {
        // Duplicate every variable definition with an instrumented reference
        // version.

        List<JCTree.JCStatement> stats;
        for (stats = tree.stats; stats.tail != null; stats = stats.tail) {
            JCTree.JCStatement stat = stats.head;
            if (stat instanceof JCTree.JCVariableDecl) {
                JCTree.JCVariableDecl newStat =
                    replVarDef((JCTree.JCVariableDecl)stat);
                if (newStat != null) {
                    replDecls.add(newStat);

                    // Splice in new statement after current one.
                    List<JCTree.JCStatement> newList =
                        List.<JCTree.JCStatement>of(newStat);
                    newList.tail = stats.tail;
                    stats.tail = newList;

                    attributeInBlock(stat, tree);
                    attributeInBlock(newStat, tree);

                    stats = stats.tail; // Skip ahead.
                }
            }
        }

        super.visitBlock(tree);
    }

    @Override
    public void visitCase(JCTree.JCCase tree) {
        List<JCTree.JCStatement> stats;
        for (stats = tree.stats; stats.tail != null; stats = stats.tail) {
            JCTree.JCStatement stat = stats.head;
            if (stat instanceof JCTree.JCVariableDecl) {
                JCTree.JCVariableDecl newStat =
                    replVarDef((JCTree.JCVariableDecl)stat);
                if (newStat != null) {
                    replDecls.add(newStat);

                    // Splice in new statement after current one.
                    List<JCTree.JCStatement> newList =
                        List.<JCTree.JCStatement>of(newStat);
                    newList.tail = stats.tail;
                    stats.tail = newList;

                    attribute(stat, stat);
                    attribute(newStat, newStat);

                    stats = stats.tail; // Skip ahead.
                }
            }
        }

        super.visitCase(tree);
    }

    @Override
    public void visitForLoop(JCTree.JCForLoop tree) {
        List<JCTree.JCStatement> stats;
        for (stats = tree.init; stats.head != null; stats = stats.tail) {
            JCTree.JCStatement stat = stats.head;
            if (stat instanceof JCTree.JCVariableDecl) {
                JCTree.JCStatement newStat =
                    replVarDef((JCTree.JCVariableDecl)stat);
                if (newStat != null) {
                    replDecls.add((JCTree.JCVariableDecl)newStat);

                    List<JCTree.JCStatement> newList = List.of(newStat);
                    newList.tail = stats.tail;
                    stats.tail = newList;
                    stats = stats.tail; // Skip ahead.

                    attribute(newStat, stat);
                }
            }
        }

        super.visitForLoop(tree);
    }

    @Override
    public void visitForeachLoop(JCTree.JCEnhancedForLoop tree) {
        JCTree.JCStatement newStat = replVarDef(tree.var, true);
        if (newStat != null) {
            replDecls.add((JCTree.JCVariableDecl)newStat);

            if (tree.body instanceof JCTree.JCBlock) {
                JCTree.JCBlock block = (JCTree.JCBlock)tree.body;
                block.stats = block.stats.prepend(newStat);
            }

            attribute(newStat, tree.var);
        }

        super.visitForeachLoop(tree);
    }

    @Override
    public void visitCatch(JCTree.JCCatch tree) {
        JCTree.JCVariableDecl newStat = replVarDef(tree.param, true);
        if (newStat != null) {
            replDecls.add(newStat);
            tree.body.stats = tree.body.stats.prepend(newStat);
            attribute(newStat, tree.param);
        }

        super.visitCatch(tree);
    }

    @Override
    public void visitIdent(JCTree.JCIdent tree) {
        super.visitIdent(tree);

        if (skipIdents)
            return;

        // Append '.value' to variable accesses.
        if (tree.sym instanceof Symbol.VarSymbol) {

            // Only instrument local variables.
            if (!(tree.sym.owner instanceof Symbol.MethodSymbol))
                return;

            // Avoid instrumenting "super" and "this" keywords.
            if (tree.sym.toString().equals("this") ||
                        tree.sym.toString().equals("super"))
                return;

            // Refer to the value field.
            String newName = tree.name.toString() + SYM_SUFFIX;
            JCTree.JCExpression expr = maker.Select(
                maker.Ident(names.fromString(newName)),
                names.fromString(VALUE_FIELD)
            );

            attribute(expr, tree, tree.type);
            instrumented.add(expr);
            result = expr;
        }
    }

    @Override
    public void visitAssign(JCTree.JCAssign tree) {
        super.visitAssign(tree);

        // Magic: reattribute assignments to avoid problems in code generation.
        attribute(tree, tree);
    }

    @Override
    public void visitVarDef(JCTree.JCVariableDecl tree) {
        if (replDecls.contains(tree)) {
            // Don't descend.
            result = tree;
            return;
        }

        // Remove "final" modifiers -- they were causing attribution problems
        // and are irrelevant for __REF__ replacements anyway.
        tree.mods.flags &= ~Flags.FINAL;
        tree.sym.flags_field &= ~Flags.FINAL;

        super.visitVarDef(tree);
    }

    @Override
    public void visitMethodDef(JCTree.JCMethodDecl tree) {
        // Skip further instrumentation on abstract methods.
        if (tree.body == null || tree.body.stats == null ||
                    tree.body.stats.head == null) {
            super.visitMethodDef(tree);
            return;
        }

        // Insert replacement variable declaration *after* calls to super() or
        // this() at the top of constructors.
        List<JCTree.JCStatement> insertLink = null;
        if (tree.sym.isConstructor() && tree.body != null &&
                tree.body.stats != null && tree.body.stats.head != null &&
                tree.body.stats.head instanceof JCTree.JCExpressionStatement) {
            JCTree.JCExpressionStatement firstStat =
                (JCTree.JCExpressionStatement)tree.body.stats.head;
            if (firstStat.expr instanceof JCTree.JCMethodInvocation) {
                JCTree.JCMethodInvocation expr =
                    (JCTree.JCMethodInvocation)firstStat.expr;
                if (expr.meth instanceof JCTree.JCIdent) {
                    JCTree.JCIdent ident = (JCTree.JCIdent)expr.meth;
                    if (ident.name.toString().equals("this") ||
                            ident.name.toString().equals("super")) {
                        // After all that, we know that this method is a
                        // constructor whose first line is a call to this or
                        // super. (This may be required at this step, but
                        // better safe than sorry.)
                        insertLink = tree.body.stats;
                        constructorCalls.add(firstStat);
                    }
                }
            }
        }

        // Assign every parameter name into a (mangled) local variable.
        for (JCTree.JCVariableDecl param : tree.params) {
            // Construct replacement declaration, which we'll insert at the top
            // of the method body.
            JCTree.JCVariableDecl decl = replVarDef(param, true);

            // Mark this on a blacklist against later instrumentation.
            replDecls.add(decl);

            // Prepend to the body and attribute.
            if (insertLink == null) {
                tree.body.stats = tree.body.stats.prepend(decl);
            } else {
                insertLink.tail = insertLink.tail.prepend(decl);
            }
            attributeInMethod(decl, tree, tree.body);
        }

        super.visitMethodDef(tree);
    }

    @Override
    public void visitSelect(JCTree.JCFieldAccess tree) {
        // As a convenience for subclasses, don't continue visiting
        // instrumented identifiers.
        if (!instrumented.contains(tree))
            super.visitSelect(tree);
    }

    @Override
    public void visitExec(JCTree.JCExpressionStatement tree) {
        // Don't instrument identifiers in super() and this() calls in
        // constructors.
        if (constructorCalls.contains(tree))
            skipIdents = true;
        super.visitExec(tree);
        if (constructorCalls.contains(tree))
            skipIdents = false;
    }
}
