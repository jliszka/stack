s # f = f . s

push a s = (s, a)

zap (s, a) = s

plus ((s, a), b) = (s, a + b)

mult ((s, a), b) = (s, a * b)

lt ((s, a), b) = (s, a < b)

nop s = s

minus ((s, a), b) = (s, a - b)

dup (s, a) = ((s, a), a)

swap ((s, a), b) = ((s, b), a)

over ((s, a), b) = (((s, a), b), a)

eq ((s, a), b) = (s, a == b)

iff (((s, True), t), f) = (s, t)
iff (((s, False), t), f) = (s, f)

lift :: (a -> b) -> (s, a) -> (s, b)
lift f (s, a) = (s, f a)

lift2 :: (a -> b -> c) -> ((s, a), b) -> (s, c)
lift2 f ((s, a), b) = (s, f a b)

call (s, f) = f s

eval f = f ()

dup2 = over # over

-- : FACT DUP 1 = { } { DUP 1 - FACT * } IF ! ;

fact :: (a, Integer) -> (a, Integer)
fact = dup # push 1 # eq # push nop # push (dup # push 1 # minus # fact # mult) # iff # call

-- : GCD DUP 0 = { DROP } { SWAP OVER % GCD } IF ! ;
gcd1 = dup # push 0 # eq # push zap # push (swap # over # lift2 mod # gcd1) # iff # call

-- : FIB DUP 2 < { DROP 1 } { DUP 1 - FIB SWAP 2 - FIB + } IF ! ;
fib :: (a, Integer) -> (a, Integer)
fib = dup # push 2 # lt # ff # tt # iff # call
  where
    ff = push (zap # push 1)
    tt = push (dup # push 1 # minus # fib # swap # push 2 # minus # fib # plus)

-- : IS_EVEN DUP 0 = { DROP TRUE } { 1 - IS_EVEN NOT } IF ! ;
isEven = dup # push 0 # eq # push (zap # push True) # push (push 1 # minus # isEven # lift not) # iff # call
