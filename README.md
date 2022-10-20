# A Typed Forth-like Stack Language

## Background
Forth is an untyped stack language. A Forth program consists of a sequence of "words," which are operators that modify the contents of the stack. A number of built-in words are provided for primitive operations, and the programmer can define new words from existing words.

Absent a formal type system, it's common to annotate a word with a description of how it affects the stack. 
This is done in a Forth comment of the form

```
( before -- after )
```
Some examples of built-in words and their stack effects:
```
DROP ( n -- )
DUP ( n -- n n )
SWAP ( n1 n2 -- n2 n1 )
OVER ( n1 n2 -- n1 n2 n1 )
+ ( n1 n2 -- sum )
```
The behavior of many Forth words are completely determined by their effect on the stack.

New words can be defined from existing words. For example, this is how you would define a word `DUP2` that duplicates the top 2 elements of the stack, using the built-in word `OVER`.

```
: DUP2 ( n1 n2 -- n1 n2 n1 n2 ) OVER OVER ;
```

### Stacks 
Forth has 2 stacks that can be manipulated by a user program: the data stack and the return stack.
The data stack is what most words act on, and the return stack is used by the system to keep track of function calls.
However, user-defined words are allowed to use the return stack as scratch space.
For instance, it can be useful place to temporarily store items from the top of the data stack to get at items buried deep in the data stack.
However, the user-defined word must be careful to restore the return stack to its original state before returning.

The type of a word can be more completely described by its effects on both stacks.
For instance, the types of the words `>R` ("to R", move a value from the data stack to the return stack) and `R>` ("from R", move a value from the return stack to the data stack) could be expressed as follows:

```
>R ( n -- , -- n )
R> ( -- n , n -- )
```

## Motivation

It should be possible to formalize stack effect annotations into a type system.
It should be possible to infer the type of user-defined words without any explicit type annotations.
Further, type checking should allow us to statically verify that user-defined words and programs
- have a valid type (i.e., can't "go wrong"),
- don't underflow the data stack,
- and don't mess up the return stack.

A type can assert that a word operates on particular primitive types, e.g.

```
+ ( int int -- int )
< ( int int -- bool )
AND ( bool bool -- bool )
FACT ( int -- int )
```

but polymorphic types should be allowed too, e.g.

```
2DUP ( a b -- a b a b )
= ( a a -- bool )
```

Typechecking a user-defined word or a program should be a simple matter of looking up the stack effects of built-in and user-defined words in the definition, keeping track of their cumulative effects on the stack, and making sure the types match up where required.

The goal here is not to add a type system to standard Forth, but to see if a Forth-like stack language can be constructed with some static type safety in the tradition of our favorite functional programming languages.

## Subtypes
There is a sense in which a type can be a subtype of another type. Type `A` is a subtype of type `B` if an `A` could substitute for a `B` when a `B` is needed.
For instance, if you need a `( int -- int int )`, an `( a -- a a )` will suffice; however, the converse is not true. 

A more interesting case is that a `( -- int )` would also suffice. To show this, imagine the data stack is `2 3`. You close your eyes and when you open them, the stack is now `2 3 4`. You can't tell whether I applied the word
```
: PLUSONE ( int -- int int ) DUP 1 + ;
```
or the word
```
: FOUR ( -- int ) 4 ;
```

However, the converse is not true. The type `( int -- int int )` cannot substitue for `( -- int )` because the former requires that there already be a value of type `int` on top of the stack, but the latter does not. Similarly, the type `( a -- a int )` also cannot substitute for `( -- int )` either, because it requires that there is _something_ on the stack (even though it doesn't care what type it is), whereas `( -- int )` has no such requirement.

In any case, `( -- int )` and `( a -- a a )` should unify to their least upper bound `( int -- int int )`.

This last fact allows us to enforce the rule that a user-defined program or word cannot mess up the return stack simply by checking that its effect on the return stack is `( -- )`.

It might seem that you can always form subtypes by removing "constraints" from both sides until one side is empty. But consider the type `( int int -- bool )`. This does have subtypes, e.g., `( a a -- bool )`, but none are "smaller" than the original type in that way.

I think what's misleading about this false rule is the notation. The type `( int int -- int )`, for example, doesn't require that the initial stack has exactly 2 elements, and doesn't assert that it will leave the stack with only 1 element on it. It only asserts that it operates only on a section of the top of the stack. So maybe a better notation would be something like `( a* int int -- a* int )`, with the `a*` indicating zero or more elements of possibly heterogenous type. The important thing is that both `a*`s refer to the same sequence of types on both sides. And the only way for a program to have that type for all possible values of `a*` is if it doesn't touch that section of the stack. 

So then it's easy to see that, for `( a* int int -- a* int )`, letting both `a*`s "consume" another `int` produces the subtype `( a* int -- a* )`. However, this procedure doesn't work for `( a* int int -- a* bool )` because the `a*`s can't consume the same type.

I think this is a more suggestive syntax, so I will use it going forward. Where omitted, it's assumed that `( b -- c )` means `( a* b -- a* c )`.

## Conditional execution
Recursive definitions should also be allowed. For this to work, conditional execution must also be supported. Forth does this in a very non-functional way. For example, this Forth program duplicates the second element of the stack if the top element of the stack is 0:
```
0 = IF DUP THEN
```
This is not a very "functional" language feature because it can't be typed: depending on the value on top of the stack, the net effect on the stack is either `( int int -- int )` or `( int int -- int int )`.

A more functional approach would require an `ELSE` clause that has the same effect on the stack as the `THEN` clause. The branches are not required to have the same type, but instead they would only need to be able to be unified as some least upper bound type.
For example, the `THEN` clause could have type `( -- int )` and the `ELSE` clause could have type `( a -- a a )`, and they would unify to their common supertype `( int -- int int )`.

The current implementation supports the following syntax for `IF`:

```
cond { then } { else } IF
```

The `{ ... }` syntax suggests deferred execution: neither clause is executed until the `IF` decides which one should apply.

For example, this program of type `( int -- int int )` would duplicate the top element of the stack if it's a positive number, or else push a 4 onto the stack:

```
DUP 0 > { DUP } { 4 } IF
```

Now we can try some recursive definitions:
```
: FACT DUP 1 = { } { DUP 1 - FACT * } IF ;
: GCD DUP 0 = { DROP } { SWAP OVER % GCD } IF ;
```
`FACT` should have type `( int -- int )` and `GCD` should have type `( int int -- int )`, and these types should be able to be inferred without any explicit type annotations.

The `{ ... }` syntax for deferred execution could also be allowed outside of `IF` statements, and would allow the programmer some additional flexibility, e.g., for defining their own control structures. 
Similar to a lambda, it would simply push a reference to an anonymous word onto the stack.
We would then also need syntax to evaluate the lambda, or break it out of its protective braces and become "live" code. Slightly modifying the above examples, we could require `IF` statements to work as follows:

```
: FACT DUP 1 = { } { DUP 1 - FACT * } IF ! ;
```
In this example, `IF` merely executes a `DROP` or a `SWAP DROP` depending on the condition, and `!` does the work of evaluating the lambda that remains on the stack. Then for simple values, the `{}`s are not even necessary:
```
: NOT FALSE TRUE IF ;
```

One interesting idea is to make the following definitions:
```
: TRUE { DROP }
: FALSE { SWAP DROP }
: IF ROT !
```
I think it's worth having boolean literals in the language, but it's neat that you don't really need them.

## Type inference for recursive functions
In functional programming languages, a typical approach for typing a recursive function is to type the body of the function while assuming the function itself has the polymorphic "bottom" type &alpha; &rarr; &beta;, and then let the type unification constraint solver determine whether &alpha; and &beta; have more specific types. In our case, the bottom type is `( a* -- b* )`.

## Other examples
We can give the `!` word (which "unpacks" and applies a lambda to the stack) an explicit type. Our system should type it as follows:

```
: APPLY !
=> APPLY ( a* ( a* -- b* ) -- b* )
```

Maybe we'd like a shorthand for `IF !`. It's possible to type this as well:

```
: IFX IF ! ;
=> IFX ( a* bool ( a* -- b* ) ( a* -- b* ) -- b* )
```

Now consider this definition:
```
: APPLY2 DUP >R ! R> !
```
This function applies a lambda to the stack twice. It duplicates the top element (assumed to be a lambda), temporarily places the copy on the return stack, applies the lambda, moves the copy back from the return stack, and applies it again. What's the type of this word? Maybe this, most generally?
```
( a* ( a* -- b* a* ) -- b* b* a* )
```

This seems like a good test case for ensuring that star types are implemented correctly.

## Implementation

An implementation in scala of a parser, type checker and evaluator for this language is [here](https://github.com/jliszka/stack/tree/main/src/main/scala/org/jliszka/stack).

To run all 3 phases:
```
$ sbt console
scala> import org.jliszka.stack._
scala> Eval.evalFile("test.tf")
> : DUP2 OVER OVER ;
=> DUP2 ( a b -- a b a b )

> : FACT DUP 1 = {  } { DUP 1 - FACT * } IF ! ;
=> FACT ( int -- int )

> : GCD DUP 0 = { DROP } { SWAP OVER % GCD } IF ! ;
=> GCD ( int int -- int )

> : FIB DUP 2 < { DROP 1 } { DUP 1 - FIB SWAP 2 - FIB + } IF ! ;
=> FIB ( int -- int )

> 5 FACT 7 FIB DUP2 GCD
3
21
120
ok
```
