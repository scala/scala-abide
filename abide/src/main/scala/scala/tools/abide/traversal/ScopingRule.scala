package scala.tools.abide.traversal

import scala.reflect.internal.traversal._
import scala.collection.{ mutable, immutable }
import scala.annotation.tailrec

/**
 * ScopingRule
 *
 * TraversalRule subtrait that provides helper methods to manage symbol scoping during traversal. The [[enterScope]] method
 * will open a new scoping block and [[scope]] will push the current symbol to the scope generated in [[enterScope]].
 * Once the traversal leaves the current scoping block, it is popped to ensure scoping equivalence with scala.
 *
 * As in [[WarningRule]], warnings are determined given local (and scoping) context in a single pass (no
 * validation/invalidation mechanism).
 */
trait ScopingRule extends ScopingRuleTraversal with WarningRuleTraversal {
  import context.universe._

  def emptyState = State(NoContext, Nil)
  case class State(context: Context, warnings: List[Warning]) extends ScopingState with WarningState {
    def withContext(newContext: Context): State = State(newContext, warnings)
    def nok(warning: Warning): State = State(context, warning :: warnings)
  }
}

trait PathScopingRule extends ScopingRuleTraversal with PathRuleTraversal with WarningRuleTraversal {
  import context.universe._

  def emptyState = State(NoContext, Nil, Nil)
  case class State(context: Context, path: List[Element], warnings: List[Warning]) extends ScopingState with PathState with WarningState {
    def withContext(newContext: Context): State = State(newContext, path, warnings)
    def withPath(newPath: List[Element]): State = State(context, newPath, warnings)
    def nok(warning: Warning): State = State(context, path, warning :: warnings)
  }
}

trait ScopingRuleTraversal extends TraversalRule with ScopingTraversal with Contexts {
  import context.universe._

  type State <: ScopingState
  trait ScopingState extends RuleState {
    val context: Context
    def withContext(context: Context): State

    def enterValDef(vdef: ValDef): State = {
      val sym = vdef.symbol
      if (context.inBlock)
        context.scope enter sym

      val valDefContext = context.makeNewScope(vdef, sym)
      val isConstrContext = (sym.isParameter || sym.isEarlyInitialized) && sym.owner.isConstructor
      val newContext = if (isConstrContext) valDefContext.makeConstructorContext else valDefContext
      withContext(newContext)
    }

    def enterDefDef(ddef: DefDef): State = {
      val sym = ddef.symbol
      if (context.inBlock)
        context.scope enter sym

      val defDefContext = context.makeNewScope(ddef, sym)
      val isConstrDefaultGetter = ddef.mods.hasDefault && sym.owner.isModuleClass &&
        nme.defaultGetterToMethod(sym.name) == nme.CONSTRUCTOR
      val newContext = if (isConstrDefaultGetter) defDefContext.makeConstructorContext else defDefContext

      newContext.scope enter sym
      for (tparam <- ddef.tparams) newContext.scope enter tparam.symbol
      for (vparams <- ddef.vparamss; vparam <- vparams) newContext.scope enter vparam.symbol

      withContext(newContext)
    }

    def enterTypeDef(tdef: TypeDef): State = {
      val sym = tdef.symbol
      if (context.inBlock)
        context.scope enter sym

      val newContext = context.makeNewScope(tdef, sym)
      for (tparam <- tdef.tparams) newContext.scope enter tparam.symbol
      withContext(newContext)
    }

    def enterFunction(fun: Function): State = {
      val newContext = context.makeNewScope(fun, fun.symbol)
      for (vparam <- fun.vparams) newContext.scope enter vparam.symbol
      withContext(newContext)
    }

    def enterClassDef(cd: ClassDef): State = {
      val newContext = context.makeNewScope(cd, cd.symbol)
      for (tparam <- cd.tparams) newContext.scope enter tparam.symbol
      withContext(context.makeNewScope(cd, cd.symbol))
    }

    def enterModuleDef(md: ModuleDef): State =
      withContext(context.makeNewScope(md, md.symbol.moduleClass))

    def enterTemplate(template: Template): State = {
      val ctx = context.make(template, context.owner, newScope)
      if (template.self.name != nme.WILDCARD) ctx.scope enter template.self.symbol
      withContext(ctx)
    }

    def enterBlock(block: Block): State =
      withContext(context.makeNewScope(block, context.owner))

    def enterImport(i: Import): State =
      withContext(context.make(i))

    def enterCaseDef(cd: CaseDef): State =
      withContext(context.makeNewScope(cd, context.owner))

    def enterBind(b: Bind): State = {
      context.scope enter b.symbol
      withContext(context)
    }

    private[ScopingRuleTraversal] def leaveContext: State = withContext(context.parent)

    def lookup(name: Name, qualifies: Symbol => Boolean): NameLookup = context.lookupSymbol(name, qualifies)
  }

  override lazy val computedStep = {
    val scopingStep = optimize {
      case b: Block =>
        transform(_ enterBlock b, _.leaveContext)

      case vd: ValDef =>
        transform(_ enterValDef vd, _.leaveContext)

      case dd: DefDef =>
        transform(_ enterDefDef dd, _.leaveContext)

      case td: TypeDef =>
        transform(_ enterTypeDef td, _.leaveContext)

      case i: Import =>
        transform(_ enterImport i)

      case cd: ClassDef =>
        transform(_ enterClassDef cd, _.leaveContext)

      case md: ModuleDef =>
        transform(_ enterModuleDef md, _.leaveContext)

      case t: Template =>
        transform(_ enterTemplate t, _.leaveContext)

      case cd: CaseDef =>
        transform(_ enterCaseDef cd, _.leaveContext)

      case b: Bind =>
        transform(_ enterBind b)
    }

    step merge scopingStep
  }

  def lookup(name: Name, qualifies: Symbol => Boolean): NameLookup = state.lookup(name, qualifies)
}

/* NSC -- new Scala compiler
 * Copyright 2005-2013 LAMP/EPFL
 * @author  Martin Odersky
 */
trait Contexts { self: ScopingRuleTraversal =>
  import context.universe._
  import definitions.{ JavaLangPackage, ScalaPackage, PredefModule, ScalaXmlTopScope, ScalaXmlPackage }

  object NoContext extends Context(EmptyTree, NoSymbol, EmptyScope, null, null) {
    // We can't pass the uninitialized `this`. Instead, we treat null specially in `Context#outer`

    enclClass = this
    enclMethod = this

    override val depth = 0
    override def nextEnclosing(p: Context => Boolean): Context = this
    override def enclosingContextChain: List[Context] = Nil
    override def imports: List[ImportInfo] = Nil
    override def toString = "NoContext"
  }

  def ambiguousImports(imp1: ImportInfo, imp2: ImportInfo) =
    LookupAmbiguous(s"it is imported twice in the same scope by\n$imp1\nand $imp2")

  def ambiguousDefnAndImport(owner: Symbol, imp: ImportInfo) =
    LookupAmbiguous(s"it is both defined in $owner and imported subsequently by \n$imp")

  private lazy val startContext = {
    NoContext.make(
      Template(List(), noSelfType, List()) setSymbol context.universe.NoSymbol setType context.universe.NoType,
      rootMirror.RootClass,
      rootMirror.RootClass.info.decls
    )
  }

  var lastAccessCheckDetails: String = ""

  lazy val rootContext: Context = {
    def mkImport(sym: Symbol, selector: List[ImportSelector]): Import = {
      val qual = gen.mkAttributedStableRef(sym)
      val importSym = (
        NoSymbol newImport NoPosition
        setFlag scala.reflect.internal.Flags.SYNTHETIC
        setInfo ImportType(qual)
      )
      val importTree = (
        Import(qual, ImportSelector.wildList)
        setSymbol importSym
        setType NoType
      )
      importTree
    }

    val rootImports = JavaLangPackage :: ScalaPackage :: PredefModule :: Nil
    val rootImportsContext = (startContext /: rootImports)((c, sym) => c.make(mkImport(sym, ImportSelector.wildList)))

    // scala-xml needs `scala.xml.TopScope` to be in scope globally as `$scope`
    // We detect `scala-xml` by looking for `scala.xml.TopScope` and
    // inject the equivalent of `import scala.xml.{TopScope => $scope}`
    val contextWithXML =
      if (ScalaXmlTopScope == NoSymbol) rootImportsContext
      else rootImportsContext.make(mkImport(ScalaXmlPackage, ImportSelector(nme.TopScope, 0, nme.dollarScope, 0) :: Nil))

    contextWithXML
  }

  /**
   * A motley collection of the state and loosely associated behaviour of the type checker.
   * Each `Typer` has an associated context, and as it descends into the tree new `(Typer, Context)`
   * pairs are spawned.
   *
   * Meet the crew; first the state:
   *
   *   - A tree, symbol, and scope representing the focus of the typechecker
   *   - An enclosing context, `outer`.
   *   - The current compilation unit.
   *   - A variety of bits that track the current error reporting policy (more on this later);
   *     whether or not implicits/macros are enabled, whether we are in a self or super call or
   *     in a constructor suffix. These are represented as bits in the mask `contextMode`.
   *   - Some odds and ends: undetermined type pararameters of the current line of type inference;
   *     contextual augmentation for error messages, tracking of the nesting depth.
   *
   * And behaviour:
   *
   *   - The central point for issuing errors and warnings from the typechecker, with a means
   *     to buffer these for use in 'silent' type checking, when some recovery might be possible.
   *  -  `Context` is something of a Zipper for the tree were are typechecking: it `enclosingContextChain`
   *     is the path back to the root. This is exactly what we need to resolve names (`lookupSymbol`)
   *     and to collect in-scope implicit defintions (`implicitss`)
   *     Supporting these are `imports`, which represents all `Import` trees in in the enclosing context chain.
   *  -  In a similar vein, we can assess accessiblity (`isAccessible`.)
   *
   * More on error buffering:
   *     When are type errors recoverable? In quite a few places, it turns out. Some examples:
   *     trying to type an application with/without the expected type, or with/without implicit views
   *     enabled. This is usually mediated by `Typer.silent`, `Inferencer#tryTwice`.
   *
   *     Intially, starting from the `typer` phase, the contexts either buffer or report errors;
   *     afterwards errors are thrown. This is configured in `rootContext`. Additionally, more
   *     fine grained control is needed based on the kind of error; ambiguity errors are often
   *     suppressed during exploraratory typing, such as determining whether `a == b` in an argument
   *     position is an assignment or a named argument, when `Infererencer#isApplicableSafe` type checks
   *     applications with and without an expected type, or whtn `Typer#tryTypedApply` tries to fit arguments to
   *     a function type with/without implicit views.
   *
   *     When the error policies entails error/warning buffering, the mutable [[ReportBuffer]] records
   *     everything that is issued. It is important to note, that child Contexts created with `make`
   *     "inherit" the very same `ReportBuffer` instance, whereas children spawned through `makeSilent`
   *     receive an separate, fresh buffer.
   *
   * @param tree  Tree associated with this context
   * @param owner The current owner
   * @param scope The current scope
   * @param _outer The next outer context.
   */
  class Context private[traversal] (val tree: Tree, val owner: Symbol, val scope: Scope, _outer: Context, _parent: Context) {
    private def outerIsNoContext = _outer eq null
    final def outer: Context = if (outerIsNoContext) NoContext else _outer
    final def parent: Context = if (outerIsNoContext) NoContext else _parent
    final lazy val source: scala.reflect.io.AbstractFile = if (outerIsNoContext) owner.sourceFile else _outer.source

    /** The next outer context whose tree is a template or package definition */
    var enclClass: Context = _

    @inline private def savingEnclClass[A](c: Context)(a: => A): A = {
      val saved = enclClass
      enclClass = c
      try a finally enclClass = saved
    }

    /** The next outer context whose tree is a method */
    var enclMethod: Context = _

    private var _undetparams: List[Symbol] = List()

    protected def outerDepth = if (outerIsNoContext) 0 else outer.depth

    val depth: Int = {
      val increasesDepth = isRootImport || outerIsNoContext || (outer.scope != scope)
      (if (increasesDepth) 1 else 0) + outerDepth
    }

    /** The currently visible imports */
    def imports: List[ImportInfo] = outer.imports
    /** Equivalent to `imports.headOption`, but more efficient */
    def isRootImport: Boolean = false

    var prefix: Type = NoPrefix

    def inBlock = tree.isInstanceOf[Block]

    /**
     * To enrich error messages involving default arguments.
     * When extending the notion, group diagnostics in an object.
     */
    var diagUsedDefaults: Boolean = false

    /** Saved type bounds for type parameters which are narrowed in a GADT. */
    var savedTypeBounds: List[(Symbol, Type)] = List()

    /** The next enclosing context (potentially `this`) that is owned by a class or method */
    def enclClassOrMethod: Context =
      if (!owner.exists || owner.isClass || owner.isMethod) this
      else outer.enclClassOrMethod

    /** The next enclosing context (potentially `this`) that has a `CaseDef` as a tree */
    def enclosingCaseDef = nextEnclosing(_.tree.isInstanceOf[CaseDef])

    /** ...or an Apply. */
    def enclosingApply = nextEnclosing(_.tree.isInstanceOf[Apply])

    def siteString = {
      def what_s = if (owner.isConstructor) "" else owner.kindString
      def where_s = if (owner.isClass) "" else "in " + enclClass.owner.decodedName
      List(what_s, owner.decodedName, where_s) filterNot (_ == "") mkString " "
    }
    //
    // Tracking undetermined type parameters for type argument inference.
    //
    def undetparamsString =
      if (undetparams.isEmpty) ""
      else undetparams.mkString("undetparams=", ", ", "")
    /** Undetermined type parameters. See `Infer#{inferExprInstance, adjustTypeArgs}`. Not inherited to child contexts */
    def undetparams: List[Symbol] = _undetparams
    def undetparams_=(ps: List[Symbol]) = { _undetparams = ps }

    /** Return and clear the undetermined type parameters */
    def extractUndetparams(): List[Symbol] = {
      val tparams = undetparams
      undetparams = List()
      tparams
    }

    //
    // Child Context Creation
    //

    /**
     * Construct a child context. The parent and child will share the report buffer.
     * Compare with `makeSilent`, in which the child has a fresh report buffer.
     *
     * If `tree` is an `Import`, that import will be avaiable at the head of
     * `Context#imports`.
     */
    def make(tree: Tree = tree, owner: Symbol = owner, scope: Scope = scope, parent: Context = this): Context = {
      if (tree == this.tree && owner == this.owner && scope == this.scope) this else {
        val isTemplateOrPackage = tree match {
          case _: Template | _: PackageDef => true
          case _                           => false
        }
        val isDefDef = tree match {
          case _: DefDef => true
          case _         => false
        }
        val isImport = tree match {
          // The guard is for SI-8403. It prevents adding imports again in the context created by
          // `Namer#createInnerNamer`
          case _: Import if tree != this.tree => true
          case _                              => false
        }
        val sameOwner = owner == this.owner
        val prefixInChild =
          if (isTemplateOrPackage) owner.thisType
          else if (!sameOwner && owner.isTerm) NoPrefix
          else prefix

        // The blank canvas
        val c = if (isImport)
          new Context(tree, owner, scope, this, this.parent) with ImportContext
        else
          new Context(tree, owner, scope, this, parent)

        // Fields that are directly propagated
        c.diagUsedDefaults = diagUsedDefaults

        // Fields that may take on a different value in the child
        c.prefix = prefixInChild
        c.enclClass = if (isTemplateOrPackage) c else enclClass

        // SI-8245 `isLazy` need to skip lazy getters to ensure `return` binds to the right place
        c.enclMethod = if (isDefDef && !owner.isLazy) c else enclMethod

        c
      }
    }

    /** Make a child context that represents a new nested scope */
    def makeNewScope(tree: Tree, owner: Symbol, parent: Context = this): Context =
      make(tree, owner, newNestedScope(scope), parent)

    /**
     * A context for typing constructor parameter ValDefs, super or self invocation arguments and default getters
     * of constructors. These expressions need to be type checked in a scope outside the class, cf. spec 5.3.1.
     *
     * This method is called by namer / typer where `this` is the context for the constructor DefDef. The
     * owner of the resulting (new) context is the outer context for the Template, i.e. the context for the
     * ClassDef. This means that class type parameters will be in scope. The value parameters of the current
     * constructor are also entered into the new constructor scope. Members of the class however will not be
     * accessible.
     */
    def makeConstructorContext = {
      val baseContext = enclClass.outer.nextEnclosing(!_.tree.isInstanceOf[Template])
      // must propagate reporter!
      // (caught by neg/t3649 when refactoring reporting to be specified only by this.reporter and not also by this.contextMode)
      val argContext = baseContext.makeNewScope(tree, owner, this)
      def enterElems(c: Context): Unit = {
        def enterLocalElems(e: ScopeEntry): Unit = {
          if (e != null && e.owner == c.scope) {
            enterLocalElems(e.next)
            argContext.scope enter e.sym
          }
        }
        if (c.owner.isTerm && !c.owner.isLocalDummy) {
          enterElems(c.outer)
          enterLocalElems(c.scope.elems)
        }
      }
      // Enter the scope elements of this (the scope for the constructor DefDef) into the new constructor scope.
      // Concretely, this will enter the value parameters of constructor.
      enterElems(this)
      argContext
    }

    // nextOuter determines which context is searched next for implicits
    // (after `this`, which contributes `newImplicits` below.) In
    // most cases, it is simply the outer context: if we're owned by
    // a constructor, the actual current context and the conceptual
    // context are different when it comes to scoping. The current
    // conceptual scope is the context enclosing the blocks which
    // represent the constructor body (TODO: why is there more than one
    // such block in the outer chain?)
    private def nextOuter = {
      // Drop the constructor body blocks, which come in varying numbers.
      // -- If the first statement is in the constructor, scopingCtx == (constructor definition)
      // -- Otherwise, scopingCtx == (the class which contains the constructor)
      val scopingCtx =
        if (owner.isConstructor) nextEnclosing(c => !c.tree.isInstanceOf[Block])
        else this

      scopingCtx.outer
    }

    def nextEnclosing(p: Context => Boolean): Context =
      if (p(this)) this else outer.nextEnclosing(p)

    def enclosingContextChain: List[Context] = this :: outer.enclosingContextChain

    private def treeTruncated = tree.toString.replaceAll("\\s+", " ").lines.mkString("\\n").take(70)
    private def treeIdString = if (settings.uniqid.value) "#" + System.identityHashCode(tree).toString.takeRight(3) else ""
    private def treeString = tree match {
      case x: Import => "" + x
      case Template(parents, `noSelfType`, body) =>
        val pstr = if ((parents eq null) || parents.isEmpty) "Nil" else parents mkString " "
        val bstr = if (body eq null) "" else body.length + " stats"
        s"""Template($pstr, _, $bstr)"""
      case x => s"${tree.shortClass}${treeIdString}:${treeTruncated}"
    }

    override def toString =
      sm"""|Context() {
           |   owner       = $owner
           |   tree        = $treeString
           |   scope       = ${scope.size} decls
           |   outer.owner = ${outer.owner}
           |}"""

    //
    // Accessibility checking
    //

    /** Is `sub` a subclass of `base` or a companion object of such a subclass? */
    private def isSubClassOrCompanion(sub: Symbol, base: Symbol) =
      sub.isNonBottomSubClass(base) ||
        sub.isModuleClass && sub.linkedClassOfClass.isNonBottomSubClass(base)

    /**
     * Return the closest enclosing context that defines a subclass of `clazz`
     *  or a companion object thereof, or `NoContext` if no such context exists.
     */
    def enclosingSubClassContext(clazz: Symbol): Context = {
      var c = this.enclClass
      while (c != NoContext && !isSubClassOrCompanion(c.owner, clazz))
        c = c.outer.enclClass
      c
    }

    def enclosingNonImportContext: Context = {
      var c = this
      while (c != NoContext && c.tree.isInstanceOf[Import])
        c = c.outer
      c
    }

    /**
     * XXX: code copied over from scala.tools.nsc.typechecker.Namers
     *
     * The companion class or companion module of `original`.
     * Calling .companionModule does not work for classes defined inside methods.
     *
     * !!! Then why don't we fix companionModule? Does the presence of these
     * methods imply all the places in the compiler calling sym.companionModule are
     * bugs waiting to be reported? If not, why not? When exactly do we need to
     * call this method?
     */
    def companionSymbolOf(original: Symbol, ctx: Context): Symbol = {
      val owner = original.owner
      // SI-7264 Force the info of owners from previous compilation runs.
      //     //         Doing this generally would trigger cycles; that's what we also
      //         //         use the lower-level scan through the current Context as a fall back.
      original.companionSymbol orElse {
        ctx.lookup(original.name.companionName, owner).suchThat(sym =>
          (original.isTerm || sym.hasModuleFlag) &&
            (sym isCoDefinedWith original))
      }
    }

    /** A version of `Symbol#linkedClassOfClass` that works with local companions, ala `companionSymbolOf`. */
    def linkedClassOfClassOf(original: Symbol, ctx: Context): Symbol =
      if (original.isModuleClass)
        companionSymbolOf(original.sourceModule, ctx)
      else
        companionSymbolOf(original, ctx).moduleClass

    /** Is `sym` accessible as a member of `pre` in current context? */
    def isAccessible(sym: Symbol, pre: Type, superAccess: Boolean = false): Boolean = {
      lastAccessCheckDetails = ""
      // Console.println("isAccessible(%s, %s, %s)".format(sym, pre, superAccess))

      // don't have access if there is no linked class (so exclude linkedClass=NoSymbol)
      def accessWithinLinked(ab: Symbol) = {
        val linked = linkedClassOfClassOf(ab, this)
        linked.fold(false)(accessWithin)
      }

      /* Are we inside definition of `ab`? */
      def accessWithin(ab: Symbol) = {
        // #3663: we must disregard package nesting if sym isJavaDefined
        if (sym.isJavaDefined) {
          // is `o` or one of its transitive owners equal to `ab`?
          // stops at first package, since further owners can only be surrounding packages
          @tailrec def abEnclosesStopAtPkg(o: Symbol): Boolean =
            (o eq ab) || (!o.isPackageClass && (o ne NoSymbol) && abEnclosesStopAtPkg(o.owner))
          abEnclosesStopAtPkg(owner)
        }
        else (owner hasTransOwner ab)
      }

      def isSubThisType(pre: Type, clazz: Symbol): Boolean = pre match {
        case ThisType(pclazz) => pclazz isNonBottomSubClass clazz
        case _                => false
      }

      /* Is protected access to target symbol permitted */
      def isProtectedAccessOK(target: Symbol) = {
        val c = enclosingSubClassContext(sym.owner)
        if (c == NoContext)
          lastAccessCheckDetails =
            "\n Access to protected " + target + " not permitted because" +
              "\n " + "enclosing " + this.enclClass.owner +
              this.enclClass.owner.locationString + " is not a subclass of " +
              "\n " + sym.owner + sym.owner.locationString + " where target is defined"
        c != NoContext &&
          {
            target.isType || { // allow accesses to types from arbitrary subclasses fixes #4737
              val res =
                isSubClassOrCompanion(pre.widen.typeSymbol, c.owner) ||
                  c.owner.isModuleClass &&
                  isSubClassOrCompanion(pre.widen.typeSymbol, c.owner.linkedClassOfClass)
              if (!res)
                lastAccessCheckDetails =
                  "\n Access to protected " + target + " not permitted because" +
                    "\n prefix type " + pre.widen + " does not conform to" +
                    "\n " + c.owner + c.owner.locationString + " where the access take place"
              res
            }
          }
      }

      (pre == NoPrefix) || {
        val ab = sym.accessBoundary(sym.owner)

        ((ab.isTerm || ab == rootMirror.RootClass)
          || (accessWithin(ab) || accessWithinLinked(ab)) &&
          (!sym.isLocalToThis
            || sym.owner.isImplClass // allow private local accesses to impl classes
            || sym.isProtected && isSubThisType(pre, sym.owner)
            || pre =:= sym.owner.thisType)
            || sym.isProtected &&
            (superAccess
              || pre.isInstanceOf[ThisType]
              || phase.erasedTypes
              || (sym.overrideChain exists isProtectedAccessOK) // that last condition makes protected access via self types work.
              ))
        // note: phase.erasedTypes disables last test, because after addinterfaces
        // implementation classes are not in the superclass chain. If we enable the
        // test, bug780 fails.
      }
    }

    //
    // Type bound management
    //

    def pushTypeBounds(sym: Symbol): Unit = {
      savedTypeBounds ::= ((sym, sym.info))
    }

    def restoreTypeBounds(tp: Type): Type = {
      def restore(): Type = savedTypeBounds.foldLeft(tp) {
        case (current, (sym, savedInfo)) =>
          def bounds_s(tb: TypeBounds) = if (tb.isEmptyBounds) "<empty bounds>" else s"TypeBounds(lo=${tb.lo}, hi=${tb.hi})"
          //@M TODO: when higher-kinded types are inferred, probably need a case PolyType(_, TypeBounds(...)) if ... =>
          val TypeBounds(lo, hi) = sym.info.bounds
          val isUnique = lo <:< hi && hi <:< lo
          val isPresent = current contains sym
          def saved_s = bounds_s(savedInfo.bounds)
          def current_s = bounds_s(sym.info.bounds)

          if (isUnique && isPresent)
            current.instantiateTypeParams(List(sym), List(hi))
          else current
      }
      try restore()
      finally {
        for ((sym, savedInfo) <- savedTypeBounds) sym setInfo savedInfo

        savedTypeBounds = Nil
      }
    }

    //
    // Imports and symbol lookup
    //

    /**
     * It's possible that seemingly conflicting identifiers are
     *  identifiably the same after type normalization.  In such cases,
     *  allow compilation to proceed.  A typical example is:
     *    package object foo { type InputStream = java.io.InputStream }
     *    import foo._, java.io._
     */
    private def resolveAmbiguousImport(name: Name, imp1: ImportInfo, imp2: ImportInfo): Option[ImportInfo] = {
      val imp1Explicit = imp1 isExplicitImport name
      val imp2Explicit = imp2 isExplicitImport name
      val ambiguous = if (imp1.depth == imp2.depth) imp1Explicit == imp2Explicit else !imp1Explicit && imp2Explicit
      val imp1Symbol = (imp1 importedSymbol name).initialize filter (s => isAccessible(s, imp1.qual.tpe, superAccess = false))
      val imp2Symbol = (imp2 importedSymbol name).initialize filter (s => isAccessible(s, imp2.qual.tpe, superAccess = false))

      // The types of the qualifiers from which the ambiguous imports come.
      // If the ambiguous name is a value, these must be the same.
      def t1 = imp1.qual.tpe
      def t2 = imp2.qual.tpe
      // The types of the ambiguous symbols, seen as members of their qualifiers.
      // If the ambiguous name is a monomorphic type, we can relax this far.
      def mt1 = t1 memberType imp1Symbol
      def mt2 = t2 memberType imp2Symbol

      def characterize = List(
        s"types:  $t1 =:= $t2  ${t1 =:= t2}  members: ${mt1 =:= mt2}",
        s"member type 1: $mt1",
        s"member type 2: $mt2"
      ).mkString("\n  ")

      if (!ambiguous || !imp2Symbol.exists) Some(imp1)
      else if (!imp1Symbol.exists) Some(imp2)
      else (
        // The symbol names are checked rather than the symbols themselves because
        // each time an overloaded member is looked up it receives a new symbol.
        // So foo.member("x") != foo.member("x") if x is overloaded.  This seems
        // likely to be the cause of other bugs too...
        if (t1 =:= t2 && imp1Symbol.name == imp2Symbol.name) {
          log(s"Suppressing ambiguous import: $t1 =:= $t2 && $imp1Symbol == $imp2Symbol")
          Some(imp1)
        }
        // Monomorphism restriction on types is in part because type aliases could have the
        // same target type but attach different variance to the parameters. Maybe it can be
        // relaxed, but doesn't seem worth it at present.
        else if (mt1 =:= mt2 && name.isTypeName && imp1Symbol.isMonomorphicType && imp2Symbol.isMonomorphicType) {
          log(s"Suppressing ambiguous import: $mt1 =:= $mt2 && $imp1Symbol and $imp2Symbol are equivalent")
          Some(imp1)
        }
        else {
          log(s"Import is genuinely ambiguous:\n  " + characterize)
          None
        }
      )
    }

    /**
     * The symbol with name `name` imported via the import in `imp`,
     *  if any such symbol is accessible from this context.
     */
    def importedAccessibleSymbol(imp: ImportInfo, name: Name): Symbol =
      importedAccessibleSymbol(imp, name, requireExplicit = false)

    private def importedAccessibleSymbol(imp: ImportInfo, name: Name, requireExplicit: Boolean): Symbol =
      imp.importedSymbol(name, requireExplicit) filter (s => isAccessible(s, imp.qual.tpe, superAccess = false))

    /**
     * Is `sym` defined in package object of package `pkg`?
     *  Since sym may be defined in some parent of the package object,
     *  we cannot inspect its owner only; we have to go through the
     *  info of the package object.  However to avoid cycles we'll check
     *  what other ways we can before pushing that way.
     */
    def isInPackageObject(sym: Symbol, pkg: Symbol): Boolean = {
      def uninitialized(what: String) = {
        log(s"Cannot look for $sym in package object of $pkg; $what is not initialized.")
        false
      }
      def pkgClass = if (pkg.isTerm) pkg.moduleClass else pkg
      def matchesInfo = (
        // need to be careful here to not get a cyclic reference during bootstrap
        if (pkg.isInitialized) {
          val module = pkg.info member nme.PACKAGEkw
          if (module.isInitialized)
            module.info.member(sym.name).alternatives contains sym
          else
            uninitialized("" + module)
        }
        else uninitialized("" + pkg)
      )
      def inPackageObject(sym: Symbol) = (
        // To be in the package object, one of these must be true:
        //   1) sym.owner is a package object class, and sym.owner.owner is the package class for `pkg`
        //   2) sym.owner is inherited by the correct package object class
        // We try to establish 1) by inspecting the owners directly, and then we try
        // to rule out 2), and only if both those fail do we resort to looking in the info.
        !sym.hasPackageFlag && sym.owner.exists && (
          if (sym.owner.isPackageObjectClass)
            sym.owner.owner == pkgClass
          else
            !sym.owner.isPackageClass && matchesInfo
        )
      )

      // An overloaded symbol might not have the expected owner!
      // The alternatives must be inspected directly.
      pkgClass.isPackageClass && (
        if (sym.isOverloaded)
          sym.alternatives forall (isInPackageObject(_, pkg))
        else
          inPackageObject(sym)
      )
    }

    def isNameInScope(name: Name) = lookupSymbol(name, _ => true).isSuccess

    /**
     * Find the symbol of a simple name starting from this context.
     *  All names are filtered through the "qualifies" predicate,
     *  the search continuing as long as no qualifying name is found.
     */
    def lookupSymbol(name: Name, qualifies: Symbol => Boolean): NameLookup = {
      var lookupError: NameLookup = null // set to non-null if a definite error is encountered
      var inaccessible: NameLookup = null // records inaccessible symbol for error reporting in case none is found
      var defSym: Symbol = NoSymbol // the directly found symbol
      var pre: Type = NoPrefix // the prefix type of defSym, if a class member
      var cx: Context = this // the context under consideration
      var symbolDepth: Int = -1 // the depth of the directly found symbol

      object resetPos extends Traverser {
        override def traverse(t: Tree): Unit = {
          if (t != EmptyTree) t.setPos(NoPosition)
          super.traverse(t)
        }
      }

      def finish(qual: Tree, sym: Symbol): NameLookup = (
        if (lookupError ne null) lookupError
        else sym match {
          case NoSymbol if inaccessible ne null => inaccessible
          case NoSymbol                         => LookupNotFound
          case _                                => LookupSucceeded(qual, sym)
        }
      )
      def finishDefSym(sym: Symbol, pre0: Type): NameLookup =
        if (requiresQualifier(sym))
          finish(gen.mkAttributedQualifier(pre0), sym)
        else
          finish(EmptyTree, sym)

      def isPackageOwnedInDifferentUnit(s: Symbol) = (
        // XXX: simplified check, I hope it still works!
        s.isDefinedInPackage && s.sourceFile != source
      )
      def requiresQualifier(s: Symbol) = (
        s.owner.isClass
        && !s.owner.isPackageClass
        && !s.isTypeParameterOrSkolem
      )
      def lookupInPrefix(name: Name) = pre member name filter qualifies
      def accessibleInPrefix(s: Symbol) = isAccessible(s, pre, superAccess = false)

      def searchPrefix = {
        cx = cx.enclClass
        val found0 = lookupInPrefix(name)
        val found1 = found0 filter accessibleInPrefix
        if (found0.exists && !found1.exists && inaccessible == null)
          inaccessible = LookupInaccessible(found0, self.lastAccessCheckDetails)

        found1
      }

      def lookupInScope(scope: Scope) =
        (scope lookupUnshadowedEntries name filter (e => qualifies(e.sym))).toList

      def newOverloaded(owner: Symbol, pre: Type, entries: List[ScopeEntry]) =
        logResult(s"overloaded symbol in $pre")(owner.newOverloaded(pre, entries map (_.sym)))

      // Constructor lookup should only look in the decls of the enclosing class
      // not in the self-type, nor in the enclosing context, nor in imports (SI-4460, SI-6745)
      if (name == nme.CONSTRUCTOR) return {
        val enclClassSym = cx.enclClass.owner
        val scope = cx.enclClass.prefix.baseType(enclClassSym).decls
        val constructorSym = lookupInScope(scope) match {
          case Nil       => NoSymbol
          case hd :: Nil => hd.sym
          case entries   => newOverloaded(enclClassSym, cx.enclClass.prefix, entries)
        }
        finishDefSym(constructorSym, cx.enclClass.prefix)
      }

      // cx.scope eq null arises during FixInvalidSyms in Duplicators
      while (defSym == NoSymbol && (cx ne NoContext) && (cx.scope ne null)) {
        pre = cx.enclClass.prefix
        defSym = lookupInScope(cx.scope) match {
          case Nil => searchPrefix
          case entries @ (hd :: tl) =>
            // we have a winner: record the symbol depth
            symbolDepth = (cx.depth - cx.scope.nestingLevel) + hd.depth
            if (tl.isEmpty) hd.sym
            else newOverloaded(cx.owner, pre, entries)
        }
        if (!defSym.exists)
          cx = cx.outer // push further outward
      }
      if (symbolDepth < 0)
        symbolDepth = cx.depth

      var impSym: Symbol = NoSymbol
      var imports = Context.this.imports
      def imp1 = imports.head
      def imp2 = imports.tail.head
      def sameDepth = imp1.depth == imp2.depth
      def imp1Explicit = imp1 isExplicitImport name
      def imp2Explicit = imp2 isExplicitImport name

      def lookupImport(imp: ImportInfo, requireExplicit: Boolean) =
        importedAccessibleSymbol(imp, name, requireExplicit) filter qualifies

      // Java: A single-type-import declaration d in a compilation unit c of package p
      // that imports a type named n shadows, throughout c, the declarations of:
      //
      //  1) any top level type named n declared in another compilation unit of p
      //
      // A type-import-on-demand declaration never causes any other declaration to be shadowed.
      //
      // Scala: Bindings of different kinds have a precedence deﬁned on them:
      //
      //  1) Deﬁnitions and declarations that are local, inherited, or made available by a
      //     package clause in the same compilation unit where the deﬁnition occurs have
      //     highest precedence.
      //  2) Explicit imports have next highest precedence.
      def depthOk(imp: ImportInfo) = (
        imp.depth > symbolDepth
        || (imp.isExplicitImport(name) && imp.depth == symbolDepth)
      )

      while (!impSym.exists && imports.nonEmpty && depthOk(imports.head)) {
        impSym = lookupImport(imp1, requireExplicit = false)
        if (!impSym.exists)
          imports = imports.tail
      }

      if (defSym.exists && impSym.exists) {
        // imported symbols take precedence over package-owned symbols in different compilation units.
        if (isPackageOwnedInDifferentUnit(defSym))
          defSym = NoSymbol
        // Defined symbols take precedence over erroneous imports.
        else if (impSym.isError || impSym.name == nme.CONSTRUCTOR)
          impSym = NoSymbol
        // Otherwise they are irreconcilably ambiguous
        else
          return ambiguousDefnAndImport(defSym.alternatives.head.owner, imp1)
      }

      // At this point only one or the other of defSym and impSym might be set.
      if (defSym.exists)
        finishDefSym(defSym, pre)
      else if (impSym.exists) {
        // We continue walking down the imports as long as the tail is non-empty, which gives us:
        //   imports  ==  imp1 :: imp2 :: _
        // And at least one of the following is true:
        //   - imp1 and imp2 are at the same depth
        //   - imp1 is a wildcard import, so all explicit imports from outer scopes must be checked
        def keepLooking = (
          lookupError == null
          && imports.tail.nonEmpty
          && (sameDepth || !imp1Explicit)
        )
        // If we find a competitor imp2 which imports the same name, possible outcomes are:
        //
        //  - same depth, imp1 wild, imp2 explicit:        imp2 wins, drop imp1
        //  - same depth, imp1 wild, imp2 wild:            ambiguity check
        //  - same depth, imp1 explicit, imp2 explicit:    ambiguity check
        //  - differing depth, imp1 wild, imp2 explicit:   ambiguity check
        //  - all others:                                  imp1 wins, drop imp2
        //
        // The ambiguity check is: if we can verify that both imports refer to the same
        // symbol (e.g. import foo.X followed by import foo._) then we discard imp2
        // and proceed. If we cannot, issue an ambiguity error.
        while (keepLooking) {
          // If not at the same depth, limit the lookup to explicit imports.
          // This is desirable from a performance standpoint (compare to
          // filtering after the fact) but also necessary to keep the unused
          // import check from being misled by symbol lookups which are not
          // actually used.
          val other = lookupImport(imp2, requireExplicit = !sameDepth)
          def imp1wins() = { imports = imp1 :: imports.tail.tail }
          def imp2wins() = { impSym = other; imports = imports.tail }

          if (!other.exists) // imp1 wins; drop imp2 and continue.
            imp1wins()
          else if (sameDepth && !imp1Explicit && imp2Explicit) // imp2 wins; drop imp1 and continue.
            imp2wins()
          else resolveAmbiguousImport(name, imp1, imp2) match {
            case Some(imp) => if (imp eq imp1) imp1wins() else imp2wins()
            case _         => lookupError = ambiguousImports(imp1, imp2)
          }
        }

        // optimization: don't write out package prefixes
        finish(resetPos(imp1.qual.duplicate), impSym)
      }
      else finish(EmptyTree, NoSymbol)
    }

    /**
     * Find a symbol in this context or one of its outers.
     *
     * Used to find symbols are owned by methods (or fields), they can't be
     * found in some scope.
     *
     * Examples: companion module of classes owned by a method, default getter
     * methods of nested methods. See NamesDefaults.scala
     */
    def lookup(name: Name, expectedOwner: Symbol) = {
      var res: Symbol = NoSymbol
      var ctx = this
      while (res == NoSymbol && ctx.outer != ctx) {
        val s = ctx.scope lookup name
        if (s != NoSymbol && s.owner == expectedOwner)
          res = s
        else
          ctx = ctx.outer
      }
      res
    }
  } //class Context

  /** A `Context` focussed on an `Import` tree */
  trait ImportContext extends Context {
    private val impInfo: ImportInfo = {
      val info = new ImportInfo(tree.asInstanceOf[Import], outerDepth)
      info
    }
    override final def imports = impInfo :: super.imports
    override final def isRootImport = !tree.pos.isDefined
    override final def toString = super.toString + " with " + s"ImportContext { $impInfo; outer.owner = ${outer.owner} }"
  }

  class ImportInfo(val tree: Import, val depth: Int) {
    def pos = tree.pos
    def posOf(sel: ImportSelector) = tree.pos withPoint sel.namePos

    /** The prefix expression */
    def qual: Tree = tree.symbol.info match {
      case ImportType(expr) => expr
      case ErrorType        => tree setType NoType // fix for #2870
      case _                => scala.sys.error("Should never happen")
    }

    /** Is name imported explicitly, not via wildcard? */
    def isExplicitImport(name: Name): Boolean =
      tree.selectors exists (_.rename == name.toTermName)

    /**
     * The symbol with name `name` imported from import clause `tree`.
     */
    def importedSymbol(name: Name): Symbol = importedSymbol(name, requireExplicit = false)

    /** If requireExplicit is true, wildcard imports are not considered. */
    def importedSymbol(name: Name, requireExplicit: Boolean): Symbol = {
      var result: Symbol = NoSymbol
      var renamed = false
      var selectors = tree.selectors
      def current = selectors.head
      while ((selectors ne Nil) && result == NoSymbol) {
        if (current.rename == name.toTermName)
          result = qual.tpe.nonLocalMember( // new to address #2733: consider only non-local members for imports
            if (name.isTypeName) current.name.toTypeName else current.name
          )
        else if (current.name == name.toTermName)
          renamed = true
        else if (current.name == nme.WILDCARD && !renamed && !requireExplicit)
          result = qual.tpe.nonLocalMember(name)

        if (result == NoSymbol)
          selectors = selectors.tail
      }

      // Harden against the fallout from bugs like SI-6745
      //
      // [JZ] I considered issuing a devWarning and moving the
      //      check inside the above loop, as I believe that
      //      this always represents a mistake on the part of
      //      the caller.
      if (definitions isImportable result) result
      else NoSymbol
    }
    private def selectorString(s: ImportSelector): String = {
      if (s.name == nme.WILDCARD && s.rename == null) "_"
      else if (s.name == s.rename) "" + s.name
      else s.name + " => " + s.rename
    }

    def allImportedSymbols: Iterable[Symbol] =
      importableMembers(qual.tpe) flatMap (transformImport(tree.selectors, _))

    private def transformImport(selectors: List[ImportSelector], sym: Symbol): List[Symbol] = selectors match {
      case List()                                      => List()
      case List(ImportSelector(nme.WILDCARD, _, _, _)) => List(sym)
      case ImportSelector(from, _, to, _) :: _ if from == sym.name =>
        if (to == nme.WILDCARD) List()
        else List(sym.cloneSymbol(sym.owner, sym.rawflags, to))
      case _ :: rest => transformImport(rest, sym)
    }

    override def hashCode = tree.##
    override def equals(other: Any) = other match {
      case that: ImportInfo => (tree == that.tree)
      case _                => false
    }
    override def toString = tree.toString
  }

  type ImportType = context.universe.ImportType
  val ImportType = context.universe.ImportType
}

