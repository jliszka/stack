s # f = f . s
push a (s, r) = ((s, a), r)
zap ((s, a), r) = (s, r)
nop (s, r) = (s, r)
dup ((s, a), r) = (((s, a), a), r)
swap (((s, a), b), r) = (((s, b), a), r)
iff ((((s, True), t), f), r) = ((s, t), r)
iff ((((s, False), t), f), r) = ((s, f), r)
toR ((s, a), r) = (s, (r, a))
fromR (s, (r, a)) = ((s, a), r)

lift :: (a -> b) -> ((s, a), r) -> ((s, b), r)
lift f ((s, a), r) = ((s, f a), r)

lift2 :: (a -> b -> c) -> (((s, a), b), r) -> ((s, c), r)
lift2 f (((s, a), b), r) = ((s, f a b), r)

plus :: (((a, Integer), Integer), b) -> ((a, Integer), b)
plus = lift2 (+)
minus :: (((a, Integer), Integer), b) -> ((a, Integer), b)
minus = lift2 (-)
mult :: (((a, Integer), Integer), b) -> ((a, Integer), b)
mult = lift2 (*)
lt :: Ord a =>  (((s, a), a), r) -> ((s, Bool), r)
lt = lift2 (<)
eq :: Eq a =>  (((s, a), a), r) -> ((s, Bool), r)
eq = lift2 (==)

call ((s, f), r) = f (s, r)

over = toR # dup # fromR # swap

dup2 = over # over

--fact :: ((a, Integer), b) -> ((a, Integer), b)
fact = dup # push 1 # eq # push nop # push (dup # push 1 # minus # fact # mult) # iff # call

gcd1 = dup # push 0 # eq # push zap # push (swap # over # lift2 mod # gcd1) # iff # call

--apply2 = dup # toR # call # fromR # call
