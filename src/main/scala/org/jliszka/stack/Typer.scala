package org.jliszka.stack

object Typer {
    val nop = Effect(Nil, Nil)
    var ticket: Int = 0
    def ptype: Type = {
        ticket += 1
        Poly(ticket)
    }
    def reset(): Unit = {
        ticket = 0
    }

    def simplify(effect: Effect): Effect = {
        def effectPolys(eff: Effect): Set[Int] = {
            (eff.in.flatMap(polys).toSet) union (eff.out.flatMap(polys).toSet)
        }
        def polys(t: Type): Set[Int] = t match {
            case Num => Set.empty
            case Bool => Set.empty
            case Poly(n) => Set(n)
            case TLambda(effs) => effectPolys(effs.data) union effectPolys(effs.ret)
        }
        def substituteEffect(effect: Effect, poly: Map[Int, Type]): Effect = {
            Effect(effect.in.map(t => substitute(t, poly)), effect.out.map(t => substitute(t, poly)))
        }
        def substitute(typ: Type, poly: Map[Int, Type]): Type = typ match {
            case Num => Num
            case Bool => Bool
            case Poly(i) => poly(i)
            case TLambda(effects) => TLambda(Effects(substituteEffect(effects.data, poly), substituteEffect(effects.ret, poly)))
        }

        val polySet = (effect.in.flatMap(polys).toSet) union (effect.out.flatMap(polys).toSet)
        val polyMap = polySet.toList.sorted.zipWithIndex.map({ case (p, i) => p -> Poly(i+1) }).toMap
        substituteEffect(effect, polyMap)
    }

    def check(prog: Prog): (Effects, Map[String, Effect]) = {
        reset()
        val ctx = prog.defns.foldLeft(Map.empty[String, Effect])({ case (ctx, defn) => {
            val effects = checkDefn(defn, ctx)
            if (effects.ret != nop) {
                throw new Exception("Top-level function cannot have return stack effects")
            }
            ctx + (defn.name -> effects.data)
        }})
        (check(prog.ops.reverse, ctx), ctx)
    }

    def checkDefn(defn: Defn, ctx: Map[String, Effect]): Effects = {
        def helper(shapes: List[Effect]): Effects = shapes match {
            case Nil => throw new Exception(s"Could not type function ${defn.name}")
            case shape :: t => try {
                check(defn.ops.reverse, ctx + (defn.name -> shape)) 
            } catch {
                case e: Exception => if (t.isEmpty) throw e else helper(t)
            }
        }
        helper(List(
            Effect(Nil, Nil),
            Effect(ptype :: Nil, Nil),
            Effect(ptype :: ptype :: Nil, Nil),
            Effect(ptype :: ptype :: ptype :: Nil, Nil),
            Effect(Nil, ptype :: Nil),
            Effect(Nil, ptype :: ptype :: Nil),
            Effect(Nil, ptype :: ptype :: ptype :: Nil),
        ))
    }

    def check(ops: List[Op], ctx: Map[String, Effect]): Effects = ops match {
        case Nil => Effects(nop, nop)
        case Plus :: t => applyEffects(check(t, ctx), Effect(Num :: Num :: Nil, Num :: Nil))
        case Minus :: t => applyEffects(check(t, ctx), Effect(Num :: Num :: Nil, Num :: Nil))
        case Times :: t => applyEffects(check(t, ctx), Effect(Num :: Num :: Nil, Num :: Nil))
        case Div :: t => applyEffects(check(t, ctx), Effect(Num :: Num :: Nil, Num :: Nil))
        case Mod :: t => applyEffects(check(t, ctx), Effect(Num :: Num :: Nil, Num :: Nil))
        case Equal :: t => applyEffects(check(t, ctx), Effect(Num :: Num :: Nil, Bool :: Nil))
        case Less :: t => applyEffects(check(t, ctx), Effect(Num :: Num :: Nil, Bool :: Nil))
        case And :: t => applyEffects(check(t, ctx), Effect(Bool :: Bool :: Nil, Bool :: Nil))
        case Or :: t => applyEffects(check(t, ctx), Effect(Bool :: Bool :: Nil, Bool :: Nil))
        case Not :: t => applyEffects(check(t, ctx), Effect(Bool :: Nil, Bool :: Nil))
        case Drop :: t => applyEffects(check(t, ctx), Effect(ptype :: Nil, Nil))
        case Dup :: t => {
            val a = ptype
            applyEffects(check(t, ctx), Effect(a :: Nil, a :: a :: Nil))
        }
        case Over :: t => {
            val a = ptype
            val b = ptype
            applyEffects(check(t, ctx), Effect(a :: b :: Nil, b :: a :: b :: Nil))
        }
        case Swap :: t => {
            val a = ptype
            val b = ptype
            applyEffects(check(t, ctx), Effect(a :: b :: Nil, b :: a :: Nil))
        }
        case ToR :: t => {
            val a = ptype
            applyEffects(check(t, ctx), Effect(a :: Nil, Nil), Effect(Nil, a :: Nil))
        }
        case FromR :: t => {
            val a = ptype
            applyEffects(check(t, ctx), Effect(Nil, a :: Nil), Effect(a :: Nil, Nil))
        }
        case If :: t => {
            val a = ptype
            applyEffects(check(t, ctx), Effect(a :: a :: Bool :: Nil, a :: Nil))
        }
        case Lit(n) :: t => applyEffects(check(t, ctx), Effect(Nil, Num :: Nil))
        case True :: t => applyEffects(check(t, ctx), Effect(Nil, Bool :: Nil))
        case False :: t => applyEffects(check(t, ctx), Effect(Nil, Bool :: Nil))
        case Fn(name) :: t => applyEffects(check(t, ctx), ctx(name))
        case Lambda(ops) :: t => applyEffects(check(t, ctx), Effect(Nil, TLambda(check(ops.reverse, ctx)) :: Nil))
        case Call :: t => {
            // Effect(PolyLambda(in, out) :: in, out))
            val e = check(t, ctx)
            e.data.out match {
                case TLambda(fnEffects) :: t2 => applyEffects(Effects(Effect(e.data.in, t2), e.ret), fnEffects.data, fnEffects.ret)
                case h :: t => throw new Exception("Trying to call non-lambda " + h)
                case _ => throw new Exception("Invoked call on an empty stack")
            }
        }
    }

    def applyEffects(effect1: Effects, effect2Data: Effect, effect2Ret: Effect = nop): Effects = {
        def applyPolyT(typ: Type, poly: Map[Int, Type]): Type = typ match {
            case Num => Num
            case Bool => Bool
            case Poly(i) => poly.get(i).map(t => applyPolyT(t, poly)).getOrElse(typ)
            case TLambda(effects) => TLambda(Effects(applyPoly(effects.data, poly), applyPoly(effects.ret, poly)))
        }
        def applyPoly(effect: Effect, poly: Map[Int, Type]): Effect = {
            Effect(effect.in.map(t => applyPolyT(t, poly)), effect.out.map(t => applyPolyT(t, poly)))
        }

        val (dataEffect, dataMatches) = matcher(effect1.data, effect2Data)
        val (retEffect, retMatches) = matcher(effect1.ret, effect2Ret)
        val poly = unify(dataMatches ++ retMatches)
        Effects(applyPoly(dataEffect, poly), applyPoly(retEffect, poly))
    }

    def matcher(e1: Effect, e2: Effect): (Effect, Set[(Type, Type)]) = {
        def matcherT(a: List[Type], b: List[Type]): (List[Type], List[Type], List[(Type, Type)]) = {
            (a, b) match {
                case (Nil, Nil) => (Nil, Nil, Nil)
                case (t, Nil) => (t, Nil, Nil)
                case (Nil, t) => (Nil, t, Nil)
                case (h1::t1, h2::t2) => {
                    val (ea, eb, ms) = matcherT(t1, t2)
                    (ea, eb, (h1, h2) :: ms)
                }
            }
        }

        val (excessOut1, excessIn2, matches) = matcherT(e1.out, e2.in)
        (Effect(e1.in ++ excessIn2, e2.out ++ excessOut1), matches.toSet)
    }

    def unify(matches: Set[(Type, Type)]): Map[Int, Type] = {
        val out = Map.newBuilder[Int, Type]
        val sets = unifyRec(matches)

        for (set <- sets) {
            val (polys, rest) = set.partition({ case Poly(i) => true case _ => false })
            val canonical = if (rest.isEmpty) {
                polys.minBy({ case Poly(i) => i case _ => 9999 })
            } else {
                leastUpperBound(rest)
            }

            for (Poly(i) <- polys) {
                if (canonical != Poly(i)) {
                    out += i -> canonical
                }
            }
        }
        out.result()
    }

    def unifyRec(matches: Set[(Type, Type)]): List[Set[Type]] = {
        def lambdaMatches(f1: TLambda, f2: TLambda): Set[(Type, Type)] = {
            List(
                f1.effects.data.in zip f2.effects.data.in,
                f1.effects.data.out zip f2.effects.data.out,
                f1.effects.ret.in zip f2.effects.ret.in,
                f1.effects.ret.out zip f2.effects.ret.out).flatten.toSet
        }

        def equivalenceClasses(poly: List[(Type, Type)], sets: List[Set[Type]]): List[Set[Type]] = {
            def find(t1: Type, t2: Type, sets: List[Set[Type]]): List[Set[Type]] = sets match {
                case Nil => List(Set(t1, t2))
                case (s :: t) => if (s(t1) || s(t2)) (s + t1 + t2) :: find(t1, t2, t) else s :: find(t1, t2, t)
            }

            def union(t1: Type, t2: Type, sets: List[Set[Type]]): List[Set[Type]] = {
                val (both, rest) = sets.partition(s => s(t1) && s(t2))
                if (both.isEmpty) Set(t1, t2) :: rest else both.reduce(_ union _) :: rest
            }
            poly match {
                case Nil => Nil
                case ((t1, t2) :: ts) => union(t1, t2, find(t1, t2, equivalenceClasses(ts, sets)))
            }
        }

        val sets = equivalenceClasses(matches.toList, Nil)
        val moreMatches = sets.flatMap(set => {
            val lambdas = set.collect({ case t: TLambda => t })
            if (lambdas.isEmpty) Nil
            else lambdas.zip(lambdas.tail).flatMap({ case (l1, l2) => lambdaMatches(l1, l2) })
        }).toSet

        val newMatches = matches union moreMatches
        if (newMatches.size > matches.size) {
            unifyRec(newMatches)
        } else {
            sets
        }
    }

    def leastUpperBound(types: Set[Type]): Type = {
        def leastUpperBoundE(e1: Effect, e2: Effect): Effect = {
            val e1NetChange = e1.out.size - e1.in.size
            val e2NetChange = e2.out.size - e2.in.size
            if (e1NetChange != e2NetChange) {
                throw new Exception(s"Cannot unify lambdas with types $e1 and $e2")
            }
            if (e1.in.size > e2.in.size) e1 else e2
        }

        def leastUpperBoundL(f1: TLambda, f2: TLambda): TLambda = {
            val dataEffect = leastUpperBoundE(f1.effects.data, f2.effects.data)
            val retEffect = leastUpperBoundE(f1.effects.ret, f2.effects.ret)
            TLambda(Effects(dataEffect, retEffect))
        }

        if (types.size == 1) {
            types.head
        } else if (types.exists(t => !t.isLambda)) {
            throw new Exception("Cannot unify " + types)
        } else {
            types.collect({ case t: TLambda => t }).reduce(leastUpperBoundL)
        }
    }
}
