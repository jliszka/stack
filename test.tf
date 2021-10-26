#: > < NOT ;
: DUP2 OVER OVER ;
#: ROT >R SWAP R> SWAP ;
: FACT DUP 1 = { } { DUP 1 - FACT * } IF ! ;
: GCD DUP 0 = { DROP } { SWAP OVER % GCD } IF ! ;
: FIB DUP 2 < { DROP 1 } { DUP 1 - FIB SWAP 2 - FIB + } IF ! ;
5 FACT
7 FIB
DUP2
GCD


# : WHILE >R OVER R@ ! { @R ! R> R> WHILE } { } IF ! ;
# 0 10 { SWAP OVER + SWAP 1 - } { 0 > } WHILE


# 1-2, 0-1, 2-1 => 1-2
# 0-1 (0-0)
# 0-1 (1-2, 0-1, 2-1, a-b, 2-1) => (1-2, a-b, 2-1)
# 3-1, 1-0, {1-1}

# 1-4, 3-1, 1-0, 1-1 => 1-1
