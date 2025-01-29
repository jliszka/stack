: FACT DUP 1 = { } { DUP 1 - FACT * } IF ! ;
: GCD DUP 0 = { DROP } { SWAP OVER % GCD } IF ! ;
: FIB DUP 2 < { DROP 1 } { DUP 1 - FIB SWAP 2 - FIB + } IF ! ;
: IS_ZERO 0 = ;
: IF! IF ! ;
: IS_EVEN DUP 0 = { DROP TRUE } { 1 - IS_EVEN NOT } IF! ;
: PLUS1 1 + ;
: !! DUP >R ! R> ! ;
: COLLATZ DUP 2 % 0 = { 2 / } { 3 * 1 + } IF ! ;

: PRIME? 
    : P DUP 1 = { DROP TRUE } { 2DUP % 0 = { DROP FALSE } { 1 - P } IF! } IF! ;
    DUP 2 / P SWAP DROP ;

: TEST
#    3 { FACT } !!
    7 FIB
    2DUP
    GCD
    DUP IS_EVEN ;

