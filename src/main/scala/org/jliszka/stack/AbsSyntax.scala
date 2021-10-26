package org.jliszka.stack

sealed trait Token
case object LBracket extends Token
case object RBracket extends Token
case object Colon extends Token
case object Semicolon extends Token
case class Name(s: String) extends Token
case class Number(i: Int) extends Token


sealed abstract class Op(sym: String) {
    override def toString = sym
}
case object Plus extends Op("+")
case object Minus extends Op("-")
case object Times extends Op("*")
case object Div extends Op("/")
case object Mod extends Op("%")
case object Equal extends Op("=")
case object Less extends Op("<")
case object And extends Op("AND")
case object Or extends Op("OR")
case object Not extends Op("NOT")
case object Drop extends Op("DROP")
case object Dup extends Op("DUP")
case object Over extends Op("OVER")
case object Swap extends Op("SWAP")
case object ToR extends Op(">R")
case object FromR extends Op("R>")
case object If extends Op("IF")
case class Lit(n: Int) extends Op(n.toString)
case object True extends Op("TRUE")
case object False extends Op("FALSE")
case class Fn(name: String) extends Op(name)
case class Lambda(ops: List[Op]) extends Op("{ " + ops.mkString(" ") + " }")
case object Call extends Op("!")

case class Defn(name: String, ops: List[Op])
case class Prog(defns: List[Defn], ops: List[Op])


sealed abstract class Type(name: String) {
    override def toString = name
    def isLambda = false
}
case object Num extends Type("int")
case object Bool extends Type("bool")
case class Poly(i: Int) extends Type(('a'+i-1).toChar.toString)
case class TLambda(effects: Effects) extends Type(effects.toString) {
    override def isLambda = true
}

case class Effect(in: List[Type], out: List[Type]) {
    override def toString = {
        val inStr = in.map(_.toString).reverse.mkString(" ")
        val outStr = out.map(_.toString).reverse.mkString(" ")
        s"$inStr -- $outStr"
    }
}

case class Effects(data: Effect, ret: Effect) {
    override def toString = s"($data, $ret)"
}


sealed abstract class Value(s: String) {
    override def toString: String = s
}
case class BoolVal(b: Boolean) extends Value(b.toString)
case class IntVal(i: Int) extends Value(i.toString)
case class LambdaVal(ops: List[Op]) extends Value("{ " + ops.mkString(" ") + " }")
