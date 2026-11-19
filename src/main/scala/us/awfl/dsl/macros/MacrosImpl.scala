package us.awfl.dsl.macros

import scala.quoted.*
import us.awfl.dsl.{BaseValue, Value, obj, Resolver}

private[macros] object MacrosImpl:

  def matchedCasesImpl[A: Type, B: Type](value: Expr[Value[A]])(pf: Expr[PartialFunction[A, B]])(using q: Quotes): Expr[List[(BaseValue[A], B)]] =
    val cases = extractCaseDefs[A, B](pf)
    buildAllBranches[A, B](value)(cases)

  // --- Crawler 1: PF AST Extraction -----------------------------------------------------------
  def extractCaseDefs[A: Type, B: Type](pf: Expr[PartialFunction[A, B]])(using q: Quotes): List[q.reflect.CaseDef] =
    import q.reflect.*

    val term = pf.asTerm

    object findCases extends TreeTraverser:
      var result: Option[List[CaseDef]] = None
      override def traverseTree(tree: Tree)(owner: Symbol): Unit =
        if result.isDefined then ()
        else
          tree match
            // Inline PF literals in Scala 3 typically synthesize applyOrElse with the Match
            case DefDef(name, _, _, Some(Match(_, cs))) if name == "applyOrElse" || name == "apply" =>
              result = Some(cs)
            case Match(_, cs) if result.isEmpty =>
              // Fallback: first match we see under the PF literal
              result = Some(cs)
            case _ => super.traverseTree(tree)(owner)

    findCases.traverseTree(term)(Symbol.spliceOwner)
    findCases.result.getOrElse {
      report.errorAndAbort(
        "matchedCases requires an inline literal PartialFunction with case branches. Inline a `{ case ... }` block instead of passing a `val`."
      )
    }

  // Helpers ------------------------------------------------------------------------------------
  private def isListValue(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean =
    import q.reflect.*
    tpe <:< TypeRepr.of[us.awfl.dsl.ListValue[?]]

  private def isBaseValue(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean =
    import q.reflect.*
    tpe <:< TypeRepr.of[us.awfl.dsl.BaseValue[?]]

  private def typeArgOf(using q: Quotes)(tpe: q.reflect.TypeRepr, sym: q.reflect.Symbol): Option[q.reflect.TypeRepr] =
    import q.reflect.*
    tpe.baseType(sym) match
      case AppliedType(_, List(arg)) => Some(arg)
      case _ => None

  // For literals and singleton objects
  def reconstructConstantPatternValue[A: Type](using q: Quotes)(pattern: q.reflect.Tree): Option[q.reflect.Term] =
    import q.reflect.*
    pattern match
      case l @ Literal(_) => Some(l)
      case t @ Ident(_) if t.symbol != null && !t.symbol.isNoSymbol && t.symbol.flags.is(Flags.Module) =>
        Some(Ref(t.symbol))
      case t @ Select(_, _) if t.symbol != null && t.symbol.flags.is(Flags.Module) =>
        Some(Ref(t.symbol))
      case Bind(_, inner) => reconstructConstantPatternValue[A](inner)
      case _ => None

  // Build an argument term for constructor param pname using resolver path (no runtime flatMap / casts)
  private def buildArgFromResolver(using q: Quotes)(pCtorTpe: q.reflect.TypeRepr, pname: String, resolverTerm: q.reflect.Term): q.reflect.Term =
    import q.reflect.*

    val listSym  = TypeRepr.of[us.awfl.dsl.ListValue].typeSymbol
    val baseSym  = TypeRepr.of[us.awfl.dsl.BaseValue].typeSymbol

    if pCtorTpe <:< TypeRepr.of[us.awfl.dsl.ListValue[?]] then
      val inner = typeArgOf(pCtorTpe, listSym).getOrElse(TypeRepr.of[Nothing])
      inner.asType match
        case '[t] =>
          val call = TypeApply(Select.unique(resolverTerm, "list"), List(TypeTree.of[t]))
          Apply(call, List(Expr(pname).asTerm))
    else if pCtorTpe <:< TypeRepr.of[us.awfl.dsl.BaseValue[?]] then
      // Value/Resolved/BaseValue path
      val inner = typeArgOf(pCtorTpe, baseSym).orElse(typeArgOf(pCtorTpe, TypeRepr.of[us.awfl.dsl.Value].typeSymbol)).getOrElse(TypeRepr.of[Nothing])
      inner.asType match
        case '[t] =>
          val call = TypeApply(Select.unique(resolverTerm, "in"), List(TypeTree.of[t]))
          Apply(call, List(Expr(pname).asTerm))
    else
      // raw type: Resolver.in[t](pname).get
      pCtorTpe.asType match
        case '[t] =>
          val call = TypeApply(Select.unique(resolverTerm, "in"), List(TypeTree.of[t]))
          val v    = Apply(call, List(Expr(pname).asTerm))
          Select.unique(v, "get")

  // Build case-class constructed term S.apply(args...) using resolver path only
  private def constructCaseClassFromResolver[A: Type](using q: Quotes)(clsSym: q.reflect.Symbol, value: Expr[Value[A]]): q.reflect.Term =
    import q.reflect.*

    if !clsSym.flags.is(Flags.Case) then
      report.errorAndAbort("Expected a case class in pattern.")

    val ctorDef = clsSym.primaryConstructor.tree match
      case d: DefDef => d
      case _ => report.errorAndAbort("Unexpected constructor shape for case class pattern.")
    val ctorParams: List[ValDef] = ctorDef.paramss.collect { case tpc: TermParamClause => tpc.params }.flatten

    val resolverTerm: Term = Select.unique(value.asTerm, "resolver")

    val argTerms: List[Term] = ctorParams.map { v =>
      val pname = v.name
      val pCtorTpe = v.tpt.tpe
      buildArgFromResolver(pCtorTpe, pname, resolverTerm)
    }

    val companion = clsSym.companionModule
    if companion.isNoSymbol then report.errorAndAbort("Missing companion for case class pattern.")
    val applySym = companion.methodMember("apply").headOption.getOrElse {
      report.errorAndAbort("Companion apply not found for case class pattern.")
    }
    Apply(Select(Ref(companion), applySym), argTerms)

  // Build left BaseValue[A] for a pattern and collect binder env without runtime matching.
  private def buildLeftAndEnvFromPattern[A: Type](value: Expr[Value[A]])(using q: Quotes)(pattern: q.reflect.Tree): (Expr[BaseValue[A]], Map[q.reflect.Symbol, q.reflect.Term]) =
    import q.reflect.*

    reconstructConstantPatternValue[A](pattern) match
      case Some(const) => ('{ obj[A](${const.asExprOf[A]}) }, Map.empty)
      case None =>
        pattern match
          // Typed binder, e.g. `case seg: S => ...` in either shape
          case Typed(b @ Bind(_, Wildcard()), tpt) =>
            tpt.tpe.typeSymbol match
              case clsSym if !clsSym.isNoSymbol =>
                val constructed = constructCaseClassFromResolver[A](clsSym, value)
                ('{ obj[A](${constructed.asExprOf[A]}) }, Map(b.symbol -> constructed))
              case _ => (value, Map.empty)

          case b @ Bind(_, Typed(Wildcard(), tpt)) =>
            tpt.tpe.typeSymbol match
              case clsSym if !clsSym.isNoSymbol =>
                val constructed = constructCaseClassFromResolver[A](clsSym, value)
                ('{ obj[A](${constructed.asExprOf[A]}) }, Map(b.symbol -> constructed))
              case _ => (value, Map.empty)

          case Bind(_, inner) => buildLeftAndEnvFromPattern[A](value)(inner)
          case Wildcard() => (value, Map.empty)

          case TypedOrTest(inner, _) =>
            buildLeftAndEnvFromPattern[A](value)(inner)

          case Unapply(fun, _, subpatterns) =>
            // Determine the case class S from the unapply's owner (the companion object)
            val owner = fun.symbol.owner
            val clsSym = owner.companionClass
            if clsSym.isNoSymbol then
              report.errorAndAbort("Unsupported Unapply owner; expected a case class companion.")

            val constructed = constructCaseClassFromResolver[A](clsSym, value)

            val ctorDef = clsSym.primaryConstructor.tree.asInstanceOf[DefDef]
            val ctorParams: List[ValDef] = ctorDef.paramss.collect { case tpc: TermParamClause => tpc.params }.flatten

            val resolverTerm: Term = Select.unique(value.asTerm, "resolver")

            val binderEnv: Map[Symbol, Term] = (ctorParams zip subpatterns).flatMap { (v, sp) =>
              val pname = v.name
              val pCtorTpe = v.tpt.tpe
              sp match
                case b @ Bind(_, _) =>
                  val term = buildArgFromResolver(pCtorTpe, pname, resolverTerm)
                  List(b.symbol -> term)
                case Typed(b @ Bind(_, _), _) =>
                  val term = buildArgFromResolver(pCtorTpe, pname, resolverTerm)
                  List(b.symbol -> term)
                case Wildcard() => Nil
                case other =>
                  report.errorAndAbort("Only simple binds or wildcards are supported in case-class patterns.")
            }.toMap

            ('{ obj[A](${constructed.asExprOf[A]}) }, binderEnv)

          case t: Typed =>
            // Type test without a binder, treat as wildcard (no env)
            (value, Map.empty)

          case _ =>
            // Fallback: treat as wildcard/inbound value
            (value, Map.empty)

  // Substitute bound identifiers in RHS with our generated terms
  private def rewriteRhsWithEnv[B: Type](using q: Quotes)(rhs: q.reflect.Term, env: Map[q.reflect.Symbol, q.reflect.Term]): Expr[B] =
    import q.reflect.*

    if env.isEmpty then rhs.asExprOf[B]
    else
      val normalized = rhs.changeOwner(Symbol.spliceOwner)
      val byName: Map[String, Term] = env.map { case (sym, term) => sym.name -> term }.toMap
      object Rewriter extends TreeMap:
        override def transformTerm(tree: Term)(owner: Symbol): Term =
          tree match
            case id @ Ident(name) =>
              env.get(id.symbol)
                .orElse(byName.get(name))
                .map(_.changeOwner(owner))
                .getOrElse(super.transformTerm(tree)(owner))
            case inlined @ Inlined(_, _, body) =>
              val nb = transformTerm(body)(owner)
              Inlined(None, Nil, nb)
            case _ => super.transformTerm(tree)(owner)
      Rewriter.transformTerm(normalized)(Symbol.spliceOwner).asExprOf[B]

  // --- Build final branch list (no filtering) -------------------------------------------------
  def buildAllBranches[A: Type, B: Type](value: Expr[Value[A]])(using q: Quotes)(cases: List[q.reflect.CaseDef]): Expr[List[(BaseValue[A], B)]] =
    import q.reflect.*

    val tuples: List[Expr[(BaseValue[A], B)]] = cases.map { cd =>
      cd.guard match
        case Some(_) => report.errorAndAbort("Guards are not supported in matchedCases.")
        case None =>
          val (left, env) = buildLeftAndEnvFromPattern[A](value)(cd.pattern)
          val rhs: Expr[B] = rewriteRhsWithEnv[B](cd.rhs, env)
          '{ ($left, $rhs) }
    }

    Expr.ofList(tuples)
