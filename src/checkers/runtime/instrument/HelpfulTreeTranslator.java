package checkers.runtime.instrument;

import checkers.runtime.InstrumentingChecker;

import javax.annotation.processing.ProcessingEnvironment;

import checkers.types.AnnotatedTypeFactory;
import checkers.util.TreeUtils;

import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.TypeTags;
import com.sun.tools.javac.comp.Attr;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Enter;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.MemberEnter;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Names;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.tree.TreeCopier;
import com.sun.tools.javac.code.Flags;
import com.sun.source.tree.Tree.Kind;
import com.sun.tools.javac.util.List;

import java.util.Stack;
import java.util.Map;
import java.util.HashMap;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;


// A "helper" base class with convenient methods for AST generation
// and manipulation. Its most important role is helping with "attribution,"
// which is the process of assigning symbols and types to nodes in the
// generated AST. This is necessary because our translators (importantly) run
// after the automatic attribution occurs. The javac APIs for attribution are
// limited and finnicky, though, so the process is quite fragile.
public class HelpfulTreeTranslator<Checker extends InstrumentingChecker> extends TreeTranslator {
    protected Context context;
    public TreeMaker maker;
    protected Names names;
    protected Enter enter;
    protected MemberEnter memberEnter;
    protected TreePath path;
    protected Attr attr;
    protected javax.lang.model.util.Types typeutils;
    protected com.sun.tools.javac.code.Types jctypes;
    protected Checker checker;
    protected AnnotatedTypeFactory atypeFactory;
    protected Symtab symtab;
    protected Log log;

    // For getting attribution context.
    protected Stack<JCTree> visitingScopes = new Stack<JCTree>();
    private static final String STATIC_INIT_METH = "__htt_staticInitializerMethod";

    protected HelpfulTreeTranslator(Checker c,
                                    ProcessingEnvironment env,
                                    TreePath p) {
        super();
        context = ((JavacProcessingEnvironment)env).getContext();
        maker = TreeMaker.instance(context);
        names = Names.instance(context);
        enter = Enter.instance(context);
        memberEnter = MemberEnter.instance(context);
        attr = Attr.instance(context);
        path = p;
        checker = c;
        atypeFactory = c.createFactory(p.getCompilationUnit());
        typeutils = ((JavacProcessingEnvironment)env).getTypeUtils();
        jctypes = com.sun.tools.javac.code.Types.instance(context);
        log = Log.instance(context);
        symtab = Symtab.instance(context);
    }


    // AST generation helpers.

    // Create an expression from a string consisting of "dot" accessess --
    // package/subpackage accesses, field accesses, etc.
    // For example: dotsExp("java.util.List")
    public JCTree.JCExpression dotsExp(String chain) {
        String[] symbols = chain.split("\\.");
        JCTree.JCExpression node = maker.Ident(names.fromString(symbols[0]));
        for (int i = 1; i < symbols.length; i++) {
            Name nextName = names.fromString(symbols[i]);
            node = maker.Select(node, nextName);
        }
        return node;
    }

    // Creates a "null" expression.
    protected JCTree.JCExpression nullExp() {
        return maker.Literal(TypeTags.BOT, null);
    }

    // Creates an expression referencing "this" in the current class.
    protected JCTree.JCExpression thisExp() {
        return maker.This(
		    ((JCTree)(TreeUtils.enclosingClass(path))).type
		);
    }

    // Boolean literals.
    protected JCTree.JCLiteral boolExp(boolean val) {
        return maker.Literal(TypeTags.BOOLEAN, val ? 1 : 0);
    }


    // Attribution.

    // Work around our inability to attribute anonymous classes.
    // The central issue is that attributing an anonymous class creates
    // a new class (it does not reuse the last one that was created
    // when the same statement was last attributed).
    private class AnonymousClassRemover extends TreeTranslator {
        private boolean reverse = false;
        private Map<JCTree.JCTypeCast, JCTree.JCNewClass>replacements =
            new HashMap<JCTree.JCTypeCast, JCTree.JCNewClass>();
        private JCTree inLeaf = null;
        public JCTree outLeaf = null;

        @Override
        public void visitNewClass(JCTree.JCNewClass node) {
            super.visitNewClass(node);
            if (!reverse && node.def != null) {
                JCTree.JCTypeCast rep = maker.TypeCast(node.type, nullExp());
                replacements.put(rep, node);
                result = rep;
                if (node == inLeaf) {
                    outLeaf = result;
                }
            }
        }

        @Override
        public void visitTypeCast(JCTree.JCTypeCast node) {
            super.visitTypeCast(node);
            if (reverse && replacements.containsKey(node)) {
                result = replacements.get(node);
            }
        }

        public JCTree remove(JCTree tree, JCTree leaf) {
            reverse = false;
            inLeaf = leaf;
            outLeaf = leaf;
            tree.accept(this);
            return result;
        }

        public JCTree replace(JCTree tree) {
            reverse = true;
            tree.accept(this);
            return result;
        }
    }

    // Gets the environment for attributing statements and expressions that are
    // generated. Pass this as an argument to one of the methods on the "attr"
    // object. The algorithm requires as an argument the new tree that will be
    // attributed.
    // Inspired by com.sun.tools.javac.api.JavacTrees.getAttrContext
    protected Env<AttrContext> getAttrEnv(JCTree leaf,
            JCTree.JCMethodDecl exMeth, JCTree.JCBlock exBlock) {
        JCTree.JCCompilationUnit compunit = null;
        JCTree.JCClassDecl class_ = null;
        JCTree.JCMethodDecl method = null;
        JCTree.JCBlock block = null;
        JCTree.JCForLoop loop = null;

        // java.lang.Thread.dumpStack();
        // System.out.println("env for " + leaf.getClass() + " " + leaf);

        for (JCTree tree : visitingScopes) {
            switch (tree.getKind()) {
            case COMPILATION_UNIT:
                compunit = (JCTree.JCCompilationUnit)tree;
                class_ = null;
                method = null;
                block = null;
                loop = null;
                break;
            case ENUM:
            case CLASS:
                class_ = (JCTree.JCClassDecl)tree;
                method = null;
                block = null;
                loop = null;
                break;
            case METHOD:
                method = (JCTree.JCMethodDecl)tree;
                block = null;
                loop = null;
                break;
            case FOR_LOOP:
                loop = (JCTree.JCForLoop)tree;
                break;
            case BLOCK:
                // Take only the *outermost* block.
                if (block == null)
                    block = (JCTree.JCBlock)tree;
                break;
            }
        }

        Env<AttrContext> env;
        if (class_ != null) {
            env = enter.getClassEnv(class_.sym);
        } else {
            // Code outside of any class (i.e., an initializer for a static
            // field of an interface).
            env = enter.getTopLevelEnv((JCTree.JCCompilationUnit) path.getCompilationUnit());
        }

        if (exMeth != null)
            method = exMeth;
        if (method != null)
            env = memberEnter.getMethodEnv(method, env);

        if (loop != null) {
            // EXPERIMENTAL (6/4/2012): Turning off this additional attribution
            // step. Was causing problems with inner classes that are in the
            // same scope as a for-loop; I think this was *mutating* the
            // environment rather than just extending it locally.
            // env = env.dup(class_, env.info.dup());
            // System.out.println(env.info);
            // env = attr.attribStatToTree(block, env, loop.init.last());
            // System.out.println(env.info);
        }

        if (exBlock != null && block != null) {
            AnonymousClassRemover remover = new AnonymousClassRemover();
            remover.remove(block, exBlock);

            env = attr.attribStatToTree(block, env, remover.outLeaf);

            remover.replace(block);
        }

        if (exBlock != null)
            block = exBlock;
        if (block != null) {
            // This is a very tricky way to silence compiler errors when
            // generating the environment. This allows us to get the environment
            // even when *other* parts of the code are not yet valid.
            int oldErrors = log.nerrors;
            log.nerrors = 100; // MaxErrors now has protected access.

            AnonymousClassRemover remover = new AnonymousClassRemover();
            remover.remove(block, leaf);

            env = attr.attribStatToTree(block, env, remover.outLeaf);
            // System.out.println(env.info);

            remover.replace(block);

            log.nerrors = oldErrors;
        }

        return env;
    }
    protected Env<AttrContext> getAttrEnv(JCTree leaf) {
        return getAttrEnv(leaf, null, null);
    }

    // Uses reflection trickery to enter a new class member into the
    // symbol table.
    public void enterClassMember(JCTree.JCClassDecl class_, JCTree member) {
        Method meth = null;
        try {
            meth = MemberEnter.class.getDeclaredMethod("memberEnter", JCTree.class, Env.class);
        } catch (NoSuchMethodException e) {
            System.out.println("*** reflection error!");
        }
        meth.setAccessible(true);
        Object[] args = {member, enter.getClassEnv(class_.sym)};
        try {
            meth.invoke(memberEnter, args);
        } catch (IllegalAccessException e) {
            System.out.println("*** reflection error!");
        } catch (InvocationTargetException e) {
            System.out.println("*** reflection error!");
        }
    }

    // More reflection trickery: get the type of an expression.
    public Type typeForExpr(JCTree.JCExpression expr,
                            Env<AttrContext> env) {
        Method meth = null;
        try {
            meth = Attr.class.getDeclaredMethod("attribExpr", JCTree.class, Env.class);
        } catch (NoSuchMethodException e) {
            System.out.println("*** reflection error!");
        }
        meth.setAccessible(true);
        Object[] args = {expr, env};
        Object ret = null;
        try {
            ret = meth.invoke(attr, args);
        } catch (IllegalAccessException e) {
            System.out.println("*** reflection error!");
        } catch (InvocationTargetException e) {
            System.out.println("*** reflection error!");
        }
        return (Type)ret;
    }


    // Succinctly attribute expressions and statements.
    public void attribute(JCTree.JCExpression expr, JCTree.JCExpression repl, Type type) {
        AnonymousClassRemover remover = new AnonymousClassRemover();
        remover.remove(expr, null);

        if (checker.verbose())
            System.out.println("attributing: " + expr);
        Type outType = attr.attribExpr(expr, getAttrEnv(repl), type);
        if (checker.verbose())
            System.out.println("   type: " + outType);

        remover.replace(expr);
    }
    public void attribute(JCTree.JCExpression expr, JCTree.JCExpression repl) {
        attribute(expr, repl, repl.type);
    }
    public void attribute(JCTree.JCStatement stat, JCTree.JCStatement repl) {
        AnonymousClassRemover remover = new AnonymousClassRemover();
        remover.remove(stat, null);

        if (checker.verbose())
            System.out.println("attributing: " + stat);
        attr.attribStat(stat, getAttrEnv(repl));
        if (checker.verbose())
            System.out.println("    attribution done.");

        remover.replace(stat);
    }

    public void attributeInBlock(JCTree.JCStatement stat,
                                 JCTree.JCBlock block) {
        attributeInMethod(stat, null, block);
    }
    public void attributeInMethod(JCTree.JCStatement stat,
                                  JCTree.JCMethodDecl meth,
                                  JCTree.JCBlock block) {
        AnonymousClassRemover remover = new AnonymousClassRemover();
        remover.remove(block, null);

        if (checker.verbose())
            System.out.println("attributing: " + stat);
        attr.attribStat(stat, getAttrEnv(stat, meth, block));
        if (checker.verbose())
            System.out.println("    attribution done.");

        remover.replace(block);
    }

    // This is a huge, hacky workaround for dealing with attribution within static
    // initializers. Long story short, I've tried a *lot* of approaches to attributing
    // statements inside <clinit>s, but I haven't yet been able to come up with the
    // right magic incantation to make local variables work. Therefore, this process
    // moves every static initializer into a static method and then invokes that
    // method from a static initializer. Same semantics, but attribution of local
    // variable references is possible.
    private void replaceStaticInitializer(JCTree.JCClassDecl class_) {
        Boolean hasReplacementMethod = false;
        Boolean hasStaticInitializer = false;
        // First check whether we've already replaced the static initializer.
        for (JCTree def : class_.defs) {
            if (def.getKind() == Kind.METHOD) {
                JCTree.JCMethodDecl meth = (JCTree.JCMethodDecl)def;
                if (meth.name.toString().equals(STATIC_INIT_METH)) {
                    hasReplacementMethod = true;
                    break;
                }
            } else if (def.getKind() == Kind.BLOCK &&
            		((JCTree.JCBlock)def).isStatic()) {
                hasStaticInitializer = true;
            }
        }
        if (!hasReplacementMethod && hasStaticInitializer) {

            // Look for static initializer; move to static method.
            List<JCTree> outDefs = List.<JCTree>nil();
            for (JCTree def : class_.defs) {
                if (def.getKind() == Kind.BLOCK) {
                    JCTree.JCBlock block = (JCTree.JCBlock)def;

                    if (!block.isStatic()) {
                    	continue;
                    }

                    // Create a new static method with the block as its body.
                    TreeCopier<Void> copier = new TreeCopier<Void>(maker);
                    block = copier.<JCTree.JCBlock>copy(block);
                    block.flags = block.flags & ~Flags.STATIC;
                    JCTree.JCMethodDecl initMeth = maker.MethodDef(
                        maker.Modifiers(Flags.PUBLIC | Flags.STATIC),
                        names.fromString(STATIC_INIT_METH),
                        maker.Type(symtab.voidType),
                        List.<JCTree.JCTypeParameter>nil(),
                        List.<JCTree.JCVariableDecl>nil(),
                        List.<JCTree.JCExpression>nil(),
                        block,
                        null
                    );
                    enterClassMember(class_, initMeth);
                    attr.attribStat(initMeth, enter.getClassEnv(class_.sym));

                    outDefs = outDefs.append(initMeth);

                } else {
                    outDefs = outDefs.append(def);
                }
            }

            // Add a new static initializer that calls the method.
            JCTree.JCExpression invokeExpr = maker.Apply(
                List.<JCTree.JCExpression>nil(),
                maker.Ident(names.fromString(STATIC_INIT_METH)),
                List.<JCTree.JCExpression>nil()
            );
            JCTree.JCStatement invokeStat = maker.Exec(invokeExpr);
            attr.attribStat(invokeStat, enter.getClassEnv(class_.sym));
            JCTree.JCBlock newIniter = maker.Block(
                Flags.STATIC,
                List.of(invokeStat)
            );
            enterClassMember(class_, newIniter);
            outDefs = outDefs.append(newIniter);

            class_.defs = outDefs;

        }
    }

    // Keeps track of the last visited method & class for the above
    // attribution helpers.
    @Override
    public void visitMethodDef(JCTree.JCMethodDecl node) {
        visitingScopes.push(node);
        super.visitMethodDef(node);
        visitingScopes.pop();
    }
    @Override
    public void visitClassDef(JCTree.JCClassDecl node) {
        // Get rid of static initializers with local variables.
        replaceStaticInitializer(node);

        visitingScopes.push(node);
        super.visitClassDef(node);
        visitingScopes.pop();
    }
    @Override
    public void visitBlock(JCTree.JCBlock node) {
        visitingScopes.push(node);
        super.visitBlock(node);
        visitingScopes.pop();
    }
    @Override
    public void visitForLoop(JCTree.JCForLoop node) {
        visitingScopes.push(node);
        super.visitForLoop(node);
        visitingScopes.pop();
    }
    @Override
    public void visitTopLevel(JCTree.JCCompilationUnit node) {
        visitingScopes.push(node);
        super.visitTopLevel(node);
        visitingScopes.pop();
    }
}
