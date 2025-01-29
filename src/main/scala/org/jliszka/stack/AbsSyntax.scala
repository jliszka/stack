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

case class Defn(name: String, defns: List[Defn], ops: List[Op])
case class Prog(defns: List[Defn], ops: List[Op])


sealed abstract class Type(name: String) {
    override def toString = name
    def isLambda = false
    def isPoly = false
    def isStack = false
}

sealed abstract class Stack(name: String) extends Type(name) {
    def |>(it: Item): Stack = new |>(this, it)
    def size: Int
    override def isStack = true
}
case class SPoly(i: Int) extends Stack(if (i < 26) ('a'+i-1).toChar.toString + "*" else s"z$i*") {
    override def isPoly = true
    def size: Int = 1
}
case class |>(st: Stack, it: Item) extends Stack("|>") {
    override def toString = s"$st $it"
    def size: Int = st.size + 1
}

sealed abstract class Item(name: String) extends Type(name)
case object Num extends Item("int")
case object Bool extends Item("bool")
case class Poly(i: Int) extends Item(if (i < 26) ('a'+i-1).toChar.toString else s"z$i") {
    override def isPoly = true
}
case class TLambda(effects: Effects) extends Item(effects.toString) {
    override def isLambda = true
}

case class Effect(in: Stack, out: Stack) {
    override def toString = s"$in -- $out"
    def map(f: Stack => Stack): Effect = Effect(f(in), f(out))
}

case class Effects(data: Effect, ret: Effect) {
    override def toString = ret match {
        case Effect(SPoly(a), SPoly(b)) if a == b => s"( $data )"
        case _ => s"( $data, $ret )"
    }
    def map(f: Effect => Effect): Effects = Effects(f(data), f(ret))
}


sealed abstract class Value(s: String) {
    override def toString: String = s
}
case class BoolVal(b: Boolean) extends Value(b.toString)
case class IntVal(i: Int) extends Value(i.toString)
case class LambdaVal(ops: List[Op]) extends Value("{ " + ops.mkString(" ") + " }")
