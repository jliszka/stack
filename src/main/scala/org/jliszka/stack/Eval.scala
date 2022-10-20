package org.jliszka.stack

import scala.io.Source
import scala.tools.jline.TerminalFactory
import scala.tools.jline.console.ConsoleReader

case class State(data: List[Value], ret: List[Value]) {
    override def toString = {
        if (ret.isEmpty) {
            data.mkString(" ")
        } else {
            "{ " + data.mkString(" ") + ", " + ret.mkString(" ") + " }"
        }
    }
}

object Eval {
    def evalFile(file: String): Unit = {
        val text = Source.fromFile(file).getLines().filter(line => !line.startsWith("#")).mkString(" ").replaceAll("\\s+", " ").trim()
        val prog = Parser.parse(text)
        val (effects, ctx) = Typer.check(prog)
        val state = eval(prog)

        for (defn <- prog.defns) {
            val fn = defn.name
            val typ = Typer.simplify(ctx(fn))
            println(s"> : $fn ${defn.ops.mkString(" ")} ;")
            println(s"=> $fn ( $typ )\n")
        }

        println(s"> ${prog.ops.mkString(" ")}")
        state.data.foreach(println)
        println("ok")
    }
    
    def eval(prog: Prog, state: State = State(Nil, Nil)): State = {
        evalOps(prog.ops, State(Nil, Nil), prog.defns.map(d => d.name -> d.ops).toMap)
    }

    def evalOps(ops: List[Op], state: State, ctx: Map[String, List[Op]]): State = ops match {
        case Nil => state
        case op :: rest => evalOps(rest, evalOp(op, state, ctx), ctx)
    }

    def main(args: Array[String]): Unit = {
        repl()
    }

    val lineReader = new ConsoleReader(System.in, System.out, TerminalFactory.get())

    def repl(state: State = State(Nil, Nil), typeCtx: Map[String, Effect] = Map.empty, valueCtx: Map[String, List[Op]] = Map.empty): Unit = {
        val line = lineReader.readLine("> ")
        if (line == "?") {
            typeCtx.foreach({ case (fn, typ) => {
                val t = Typer.simplify(typ)
                println(s": $fn ( $t )")
            }})
            repl(state, typeCtx, valueCtx)
        } else {
            val text = if (line.startsWith("?")) {
                Source.fromFile(line.substring(1)).getLines().filter(line => !line.startsWith("#")).mkString(" ").replaceAll("\\s+", " ").trim()
            } else line

            try {
                val prog = Parser.parse(text)
                val (effects, typeCtx2) = Typer.check(prog, typeCtx)
                val valueCtx2 = for (defn <- prog.defns) yield {
                    val fn = defn.name
                    val typ = Typer.simplify(typeCtx2(fn))
                    println(s"=> $fn ( $typ )")
                    defn.name -> defn.ops
                }
                val state2 = evalOps(prog.ops, state, valueCtx ++ valueCtx2)
                if (prog.ops.nonEmpty) {
                    state2.data.foreach(println)
                }
                println()
                repl(state2, typeCtx ++ typeCtx2, valueCtx ++ valueCtx2)
            } catch {
                case e: Exception => {
                    println("ERROR: " + e.getMessage)
                    repl(state, typeCtx, valueCtx)
                }
            }
        }
    }

    def evalOp(op: Op, state: State, ctx: Map[String, List[Op]]): State = (op, state.data, state.ret) match {
        case (Plus, IntVal(a) :: IntVal(b) :: data, ret) => State(IntVal(a+b) :: data, ret)
        case (Minus, IntVal(a) :: IntVal(b) :: data, ret) => State(IntVal(b-a) :: data, ret)
        case (Times, IntVal(a) :: IntVal(b) :: data, ret) => State(IntVal(a*b) :: data, ret)
        case (Div, IntVal(a) :: IntVal(b) :: data, ret) => State(IntVal(b/a) :: data, ret)
        case (Mod, IntVal(a) :: IntVal(b) :: data, ret) => State(IntVal(b%a) :: data, ret)
        case (Equal, IntVal(a) :: IntVal(b) :: data, ret) => State(BoolVal(a == b) :: data, ret)
        case (Less, IntVal(a) :: IntVal(b) :: data, ret) => State(BoolVal(b < a) :: data, ret)
        case (And, BoolVal(a) :: BoolVal(b) :: data, ret) => State(BoolVal(a && b) :: data, ret)
        case (Or, BoolVal(a) :: BoolVal(b) :: data, ret) => State(BoolVal(a || b) :: data, ret)
        case (Not, BoolVal(a) :: data, ret) => State(BoolVal(!a) :: data, ret)
        case (Drop, a :: data, ret) => State(data, ret)
        case (Dup, a :: data, ret) => State(a :: a :: data, ret)
        case (Swap, a :: b :: data, ret) => State(b :: a :: data, ret)
        case (ToR, a :: data, ret) => State(data, a :: ret)
        case (FromR, data, a :: ret) => State(a :: data, ret)
        case (If, a :: b :: BoolVal(c) :: data, ret) => State((if (c) b else a) :: data, ret)
        case (Lit(n), data, ret) => State(IntVal(n) :: data, ret)
        case (True, data, ret) => State(BoolVal(true) :: data, ret)
        case (False, data, ret) => State(BoolVal(false) :: data, ret)
        case (Fn(name), data, ret) => evalOps(ctx(name), State(data, ret), ctx)
        case (Lambda(ops), data, ret) => State(LambdaVal(ops) :: data, ret)
        case (Call, LambdaVal(ops) :: data, ret) => evalOps(ops, State(data, ret), ctx)
        case _ => throw new Exception(s"Unable to eval $op with $state")
    }
}
