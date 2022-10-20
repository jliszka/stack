package org.jliszka.stack

object Typer {
    var ticket: Int = 0
    def tick(): Int = {
        ticket += 1
        ticket
    }
    def ptype: Item = {
        Poly(tick())
    }
    def stype: Stack = {
        SPoly(tick())
    }
    def reset(): Unit = {
        ticket = 0
    }

    def nop: Effect = {
        val s1 = stype
        Effect(s1, s1)
    }

    def check(prog: Prog, ctx: Map[String, Effect] = Map.empty): (Effects, Map[String, Effect]) = {
        reset()
        val ctx2 = prog.defns.foldLeft(ctx)({ case (ctx, defn) => {
            val effects = checkDefn(defn, ctx)
            effects.ret match {
                case (Effect(a, b)) if a == b => // OK
                case _ => throw new Exception("Top-level function cannot have return stack effects")
            }
            ctx + (defn.name -> effects.data)
        }})
        (checkOps(prog.ops, ctx2), ctx2)
    }

    def checkDefn(defn: Defn, ctx: Map[String, Effect]): Effects = {
        checkOps(defn.ops, ctx + (defn.name -> Effect(stype, stype)))
    }

    def checkOp(op: Op, ctx: Map[String, Effect]): Effects = {
        val s = stype
        def noRet(s1: Stack, s2: Stack): Effects = {
            Effects(Effect(s1, s2), nop)
        }
        op match {
            case Plus => noRet(s |> Num |> Num, s |> Num)
            case Minus => noRet(s |> Num |> Num, s |> Num)
            case Times => noRet(s |> Num |> Num, s |> Num)
            case Div => noRet(s |> Num |> Num, s |> Num)
            case Mod => noRet(s |> Num |> Num, s |> Num)
            case Equal => noRet(s |> Num |> Num, s |> Bool)
            case Less => noRet(s |> Num |> Num, s |> Bool)
            case And => noRet(s |> Bool |> Bool, s |> Bool)
            case Or => noRet(s |> Bool |> Bool, s |> Bool)
            case Not => noRet(s |> Bool, s |> Bool)
            case Drop => noRet(s |> ptype, s)
            case Dup => {
                val a = ptype
                noRet(s |> a, s |> a |> a)
            }
            case Swap => {
                val a = ptype
                val b = ptype
                noRet(s |> b |> a, s |> a |> b)
            }
            case ToR => {
                val a = ptype
                val r = stype
                Effects(Effect(s |> a, s), Effect(r, r |> a))
            }
            case FromR => {
                val a = ptype
                val r = stype
                Effects(Effect(s, s |> a), Effect(r |> a, r))
            }
            case If => {
                val a = ptype
                noRet(s |> Bool |> a |> a, s |> a)
            }
            case Lit(n) => noRet(s, s |> Num)
            case True => noRet(s, s |> Bool)
            case False => noRet(s, s |> Bool)
            case Fn(name) => Effects(freshen(ctx(name)), nop)
            case Lambda(ops) => noRet(s, s |> TLambda(checkOps(ops, ctx)))
            case Call => {
                val ls = stype
                val rs = stype
                noRet(ls |> TLambda(Effects(Effect(ls, rs), nop)), rs)
            }
        }
    }

    def checkOps(ops: List[Op], ctx: Map[String, Effect]): Effects = {
        val (effects, matches) = checkOpsRec(ops, ctx, Effects(nop, nop), Nil)
        val poly: Map[Int, Type] = unify(matches.toSet)
        effects.map(applyPoly(_, poly))
    }

    def checkOpsRec(ops: List[Op], ctx: Map[String, Effect], eff: Effects, matches: List[(Type, Type)]): (Effects, List[(Type, Type)]) = ops match {
        case Nil => (eff, matches)
        case h::t => {
            val (headEffects, headMatches) = applyEffects(eff, checkOp(h, ctx))
            checkOpsRec(t, ctx, headEffects, matches ++ headMatches)
        }
    }

    def applyEffects(effect1: Effects, effect2: Effects): (Effects, List[(Type, Type)]) = {
        val (dataEffect, dataMatches) = matcher(effect1.data, effect2.data)
        val (retEffect, retMatches) = matcher(effect1.ret, effect2.ret)
        Effects(dataEffect, retEffect) -> (dataMatches ++ retMatches)
    }

    def matcher(e1: Effect, e2: Effect): (Effect, List[(Type, Type)]) = {
        (Effect(e1.in, e2.out), matcherT(e1.out, e2.in))
    }

    def matcherT(a: Stack, b: Stack): List[(Type, Type)] = {
        (a, b) match {
            case (st1 |> it1, st2 |> it2) => (it1, it2) :: matcherT(st1, st2)
            case _ => List((a, b))
        }
    }

    def applyPoly(effect: Effect, poly: Map[Int, Type]): Effect = {
        def applyPolyT(typ: Type, poly: Map[Int, Type]): Item = typ match {
            case Num => Num
            case Bool => Bool
            case p @ Poly(i) => poly.get(i).map(t => applyPolyT(t, poly)).getOrElse(p)
            case TLambda(effects) => TLambda(effects.map(applyPoly(_, poly)))
            case _ => throw new Exception(s"$typ is not an item")
        }
        def applyPolyS(typ: Type, poly: Map[Int, Type]): Stack = typ match {
            case p @ SPoly(i) => poly.get(i).map(t => applyPolyS(t, poly)).getOrElse(p)
            case stack |> item => applyPolyS(stack, poly) |> applyPolyT(item, poly)
            case _ => throw new Exception(s"$typ is not a stack")
        }
        effect.map(applyPolyS(_, poly))
    }

    def unify(matches: Set[(Type, Type)]): Map[Int, Type] = {
        val out = Map.newBuilder[Int, Type]
        val sets = unifyRec(matches)

        for (set <- sets) {
            val (polys, rest) = set.partition(_.isPoly)
            val canonical = if (rest.isEmpty) {
                polys.minBy({ case Poly(i) => i case SPoly(i) => i case _ => 9999 })
            } else {
                leastUpperBound(rest)
            }

            for (p <- (polys - canonical)) {
                p match {
                    case Poly(i) => out += i -> canonical
                    case SPoly(i) => out += i -> canonical
                    case _ => throw new Exception(s"$p is not poly")
                }
            }
        }
        out.result()
    }

    def unifyRec(matches: Set[(Type, Type)]): List[Set[Type]] = {
        def matchLambdas(f1: TLambda, f2: TLambda): Set[(Type, Type)] = {
            List(
                matcherT(f1.effects.data.in, f2.effects.data.in),
                matcherT(f1.effects.data.out, f2.effects.data.out),
                matcherT(f1.effects.ret.in, f2.effects.ret.in),
                matcherT(f1.effects.ret.out, f2.effects.ret.out)
            ).flatten.toSet
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
        val lambdaMatches = sets.flatMap(set => {
            val lambdas = set.collect({ case t: TLambda => t })
            for {
                l1 <- lambdas
                l2 <- lambdas
                if l1 != l2
                m <- matchLambdas(l1, l2)
            } yield m
        }).toSet

        val stackMatches = sets.flatMap(set => {
            val stacks = set.collect({ case s: Stack => s })
            for {
                s1 <- stacks
                s2 <- stacks
                if s1 != s2
                m <- matcherT(s1, s2)
            } yield m
        }).toSet

        val newMatches = matches union lambdaMatches union stackMatches
        if (newMatches.size > matches.size) {
            unifyRec(newMatches)
        } else {
            sets
        }
    }

    def leastUpperBound(types: Set[Type]): Type = {
        def leastUpperBoundE(e1: Effect, e2: Effect): Effect = {
            /*
            val e1NetChange = e1.out.size - e1.in.size
            val e2NetChange = e2.out.size - e2.in.size
            if (e1NetChange != e2NetChange) {
                throw new Exception(s"Cannot unify lambdas with types $e1 and $e2")
            }*/
            if (e1.in.size > e2.in.size) e1 else e2
        }

        def leastUpperBoundL(f1: TLambda, f2: TLambda): TLambda = {
            val dataEffect = leastUpperBoundE(f1.effects.data, f2.effects.data)
            val retEffect = leastUpperBoundE(f1.effects.ret, f2.effects.ret)
            TLambda(Effects(dataEffect, retEffect))
        }

        def leastUpperBoundS(s1: Stack, s2: Stack): Stack = {
            if (s1.size > s2.size) s1 else s2
        }

        if (types.size == 1) {
            types.head
        } else if (types.forall(_.isLambda)) {
            types.collect({ case t: TLambda => t }).reduce(leastUpperBoundL)
        } else if (types.forall(_.isStack)) {
            types.collect({ case t: Stack => t }).reduce(leastUpperBoundS)
        } else {
            throw new Exception("Cannot unify " + types)
        }
    }

    def effectPolys(eff: Effect): Set[Int] = {
        def polys(t: Type): Set[Int] = t match {
            case Num => Set.empty
            case Bool => Set.empty
            case Poly(n) => Set(n)
            case SPoly(n) => Set(n)
            case st |> it => polys(st) union polys(it)
            case TLambda(effs) => effectPolys(effs.data) union effectPolys(effs.ret)
        }
        polys(eff.in) union polys(eff.out)
    }

    def substituteEffect(effect: Effect, polyMap: Map[Int, Int]): Effect = {
        def substituteStack(stack: Stack, polyMap: Map[Int, Int]): Stack = stack match {
            case SPoly(i) => SPoly(polyMap(i))
            case st |> it => substituteStack(st, polyMap) |> substituteItem(it, polyMap)
        }
        def substituteItem(it: Item, polyMap: Map[Int, Int]): Item = it match {
            case Num => Num
            case Bool => Bool
            case Poly(i) => Poly(polyMap(i))
            case TLambda(effects) => TLambda(effects.map(substituteEffect(_, polyMap)))
        }
        effect.map(substituteStack(_, polyMap))
    }

    def simplify(effect: Effect): Effect = {
        val polySet = effectPolys(effect)
        val polyMap: Map[Int, Int] = polySet.toList.sorted.zipWithIndex.map({ case (p, i) => p -> (i + 1) }).toMap
        substituteEffect(effect, polyMap)
    }

    def freshen(effect: Effect): Effect = {
        val polySet = effectPolys(effect)
        val polyMap: Map[Int, Int] = polySet.toList.map(p => p -> tick()).toMap
        substituteEffect(effect, polyMap)
    }
}
