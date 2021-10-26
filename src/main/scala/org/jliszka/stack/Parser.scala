package org.jliszka.stack

trait Parser[A] {
    self =>
    def parse(tokens: List[Token]): Option[(A, List[Token])]
    def map[B](f: A => B): Parser[B] = new Parser[B] {
        def parse(tokens: List[Token]): Option[(B, List[Token])] = {
            for {
                (a, ts) <- self.parse(tokens)
            } yield (f(a), ts)
        }
    }
    def flatMap[B](f: A => Parser[B]): Parser[B] = new Parser[B] {
        def parse(tokens: List[Token]): Option[(B, List[Token])] = {
            for {
                (a, ts) <- self.parse(tokens)
                (b, ts2) <- f(a).parse(ts)
            } yield (b, ts2)
        }
    }
    def filter(f: A => Boolean): Parser[A] = new Parser[A] {
        def parse(tokens: List[Token]): Option[(A, List[Token])] = {
            for {
                (a, ts) <- self.parse(tokens)
                if f(a)
            } yield (a, ts)
        }
    }
    def withFilter(f: A => Boolean): Parser[A] = filter(f)

    def repeat: Parser[List[A]] = new Parser[List[A]] {
        def parse(tokens: List[Token]): Option[(List[A], List[Token])] = {
            self.parse(tokens) match {
                case None => Some((Nil, tokens))
                case Some((a, ts)) => this.parse(ts) match {
                    case None => Some((a :: Nil), tokens)
                    case Some((bs, ts2)) => Some((a :: bs, ts2))
                }
            }
        }
    }

    def <<<[B](other: => Parser[B]): Parser[A] = {
        for {
            a <- self
            b <- other
        } yield a
    }

    def >>>[B](other: => Parser[B]): Parser[B] = {
        for {
            a <- self
            b <- other
        } yield b
    }

    def &&&[B](other: => Parser[B]): Parser[(A, B)] = {
        for {
            a <- self
            b <- other
        } yield (a, b)
    }

    def |||(other: => Parser[A]): Parser[A] = new Parser[A] {
        def parse(tokens: List[Token]): Option[(A, List[Token])] = {
            self.parse(tokens) orElse other.parse(tokens)
        }
    }
}

object Parser {
    def lex(s: String): List[Token] = {
        s.split(" ").toList.map({
            case "{" => LBracket
            case "}" => RBracket
            case ":" => Colon
            case ";" => Semicolon
            case s => try { Number(s.toInt) } catch { case e: NumberFormatException => Name(s) }
        })
    }

    def parse(s: String): Prog = {
        parseProg.parse(lex(s)).map({ case (prog, tokens) => {
            if (!tokens.isEmpty) {
                throw new Exception("Unexpected end of input at " + tokens)
            }
            prog
        }}).get
    }

    val parseAny: Parser[Token] = new Parser[Token] {
        def parse(tokens: List[Token]): Option[(Token, List[Token])] = tokens match {
            case Nil => None
            case h::t => Some((h, t))
        }
    }

    def succeed[A](a: A): Parser[A] = new Parser[A] {
        def parse(tokens: List[Token]): Option[(A, List[Token])] = Some((a, tokens))
    }

    def fail[A]: Parser[A] = new Parser[A] {
        def parse(tokens: List[Token]): Option[(A, List[Token])] = None
    }

    def parseLit(t: Token): Parser[Token] = {
        for {
            a <- parseAny
            if a == t
        } yield a
    }

    val anyName: Parser[String] = {
        parseAny.flatMap(t => t match {
            case Name(n) => succeed(n)
            case _ => fail
        })
    }

    val anyNumber: Parser[Int] = {
        parseAny.flatMap(t => t match {
            case Number(n) => succeed(n)
            case _ => fail
        })
    }

    val parseLambda: Parser[Op] = {
        (parseLit(LBracket) >>> parseOp.repeat <<< parseLit(RBracket)).map(Lambda)
    }

    val parseOp: Parser[Op] = {
        parseLambda |||
        parseLit(Name("+")).map(_ => Plus) |||
        parseLit(Name("-")).map(_ => Minus) |||
        parseLit(Name("*")).map(_ => Times) |||
        parseLit(Name("/")).map(_ => Div) |||
        parseLit(Name("%")).map(_ => Mod) |||
        parseLit(Name("=")).map(_ => Equal) |||
        parseLit(Name("<")).map(_ => Less) |||
        parseLit(Name("AND")).map(_ => And) |||
        parseLit(Name("OR")).map(_ => Or) |||
        parseLit(Name("NOT")).map(_ => Not) |||
        parseLit(Name("DROP")).map(_ => Drop) |||
        parseLit(Name("DUP")).map(_ => Dup) |||
        parseLit(Name("OVER")).map(_ => Over) |||
        parseLit(Name("SWAP")).map(_ => Swap) |||
        parseLit(Name(">R")).map(_ => ToR) |||
        parseLit(Name("R>")).map(_ => FromR) |||
        parseLit(Name("IF")).map(_ => If) |||
        parseLit(Name("TRUE")).map(_ => True) |||
        parseLit(Name("FALSE")).map(_ => False) |||
        parseLit(Name("!")).map(_ => Call) |||
        anyName.map(Fn) |||
        anyNumber.map(Lit)
    }

    val parseDefn: Parser[Defn] = {
        (parseLit(Colon) >>> anyName &&& parseOp.repeat <<< parseLit(Semicolon)).map({ case (name, ops) => Defn(name, ops) })
    }

    val parseProg: Parser[Prog] = {
        (parseDefn.repeat &&& parseOp.repeat).map({ case (defns, ops) => Prog(defns, ops) })
    }
}
