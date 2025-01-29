package org.jliszka.stack

import scala.io.Source
import scala.tools.jline.TerminalFactory
import scala.tools.jline.console.ConsoleReader


case class Stacks(data: List[Value], ret: List[Value]) {
    override def toString = {
        if (ret.isEmpty) {
            data.mkString(" ")
        } else {
            "{ " + data.mkString(" ") + ", " + ret.mkString(" ") + " }"
        }
    }
}

case class State(stacks: Stacks, types: Map[String, Effect], defs: Map[String, Defn])

object Eval {
    def evalFile(file: String, state: State): (State, Prog) = {
        val text = Source.fromFile(file).getLines().filter(line => !line.startsWith("#")).mkString(" ").replaceAll("\\s+", " ").trim()
        evalString(text, state)
    }

    def evalString(text: String, state: State): (State, Prog) = {
        val prog = Parser.parse(text)
        val (_, types) = Typer.check(prog, state.types)
        val stacks = eval(prog, state)
        val defs = prog.defns.map(d => d.name -> d).toMap
        (State(stacks, types, defs), prog)
    }
    
    def eval(prog: Prog, state: State): Stacks = {
        evalOps(prog.ops, state.stacks, state.defs)
    }

    def evalOps(ops: List[Op], stacks: Stacks, ctx: Map[String, Defn]): Stacks = ops match {
        case Nil => stacks
        case op :: rest => evalOps(rest, evalOp(op, stacks, ctx), ctx)
    }

    def main(args: Array[String]): Unit = {
        val empty = State(Stacks(Nil, Nil), Map.empty, Map.empty)
        val (state, prog) = evalFile("defs.tf", empty)
        repl(state)
    }

    val lineReader = new ConsoleReader(System.in, System.out, TerminalFactory.get())

    def repl(state: State): Unit = {
        val line = lineReader.readLine("> ")
        if (line == "?") {
            state.types.toList.sortBy(_._1).foreach({ case (fn, typ) => {
                println(s": $fn ( $typ )")
            }})
            repl(state)
        } else if (line == "??") { 
            // Exit
        } else {
            val text = if (line.startsWith("?")) {
                Source.fromFile(line.substring(1)).getLines().filter(line => !line.startsWith("#")).mkString(" ").replaceAll("\\s+", " ").trim()
            } else line

            try {
                val (newState, prog) = evalString(text, state)
                
                for (defn <- prog.defns) {
                    val typ = newState.types(defn.name)
                    println(s"=> ${defn.name} ( $typ )")
                }
                
                if (prog.ops.nonEmpty) {
                    newState.stacks.data.foreach(println)
                }
                println()

                repl(State(newState.stacks, newState.types, state.defs ++ newState.defs))
            } catch {
                case e: Exception => {
                    println("ERROR: " + e.getMessage)
                    repl(state)
                }
            }
        }
    }

    def evalOp(op: Op, stacks: Stacks, ctx: Map[String, Defn]): Stacks = (op, stacks.data, stacks.ret) match {
        case (Plus, IntVal(a) :: IntVal(b) :: data, ret) => Stacks(IntVal(a+b) :: data, ret)
        case (Minus, IntVal(a) :: IntVal(b) :: data, ret) => Stacks(IntVal(b-a) :: data, ret)
        case (Times, IntVal(a) :: IntVal(b) :: data, ret) => Stacks(IntVal(a*b) :: data, ret)
        case (Div, IntVal(a) :: IntVal(b) :: data, ret) => Stacks(IntVal(b/a) :: data, ret)
        case (Mod, IntVal(a) :: IntVal(b) :: data, ret) => Stacks(IntVal(b%a) :: data, ret)
        case (Equal, IntVal(a) :: IntVal(b) :: data, ret) => Stacks(BoolVal(a == b) :: data, ret)
        case (Less, IntVal(a) :: IntVal(b) :: data, ret) => Stacks(BoolVal(b < a) :: data, ret)
        case (And, BoolVal(a) :: BoolVal(b) :: data, ret) => Stacks(BoolVal(a && b) :: data, ret)
        case (Or, BoolVal(a) :: BoolVal(b) :: data, ret) => Stacks(BoolVal(a || b) :: data, ret)
        case (Not, BoolVal(a) :: data, ret) => Stacks(BoolVal(!a) :: data, ret)
        case (Drop, a :: data, ret) => Stacks(data, ret)
        case (Dup, a :: data, ret) => Stacks(a :: a :: data, ret)
        case (Swap, a :: b :: data, ret) => Stacks(b :: a :: data, ret)
        case (ToR, a :: data, ret) => Stacks(data, a :: ret)
        case (FromR, data, a :: ret) => Stacks(a :: data, ret)
        case (If, a :: b :: BoolVal(c) :: data, ret) => Stacks((if (c) b else a) :: data, ret)
        case (Lit(n), data, ret) => Stacks(IntVal(n) :: data, ret)
        case (True, data, ret) => Stacks(BoolVal(true) :: data, ret)
        case (False, data, ret) => Stacks(BoolVal(false) :: data, ret)
        case (Fn(name), data, ret) => {
            val defn = ctx(name)
            val ctx2 = defn.defns.map(d => d.name -> d).toMap
            evalOps(defn.ops, Stacks(data, ret), ctx ++ ctx2)
        }
        case (Lambda(ops), data, ret) => Stacks(LambdaVal(ops) :: data, ret)
        case (Call, LambdaVal(ops) :: data, ret) => evalOps(ops, Stacks(data, ret), ctx)
        case _ => throw new Exception(s"Unable to eval $op with $stacks")
    }
}
