: DUP2 OVER OVER ;
: ROT >R SWAP R> SWAP ;
: FACT DUP 1 = { } { DUP 1 - FACT * } IF ! ;
: GCD DUP 0 = { DROP } { SWAP OVER % GCD } IF ! ;
: FIB DUP 2 < { DROP 1 } { DUP 1 - FIB SWAP 2 - FIB + } IF ! ;
: IS_ZERO 0 = ;
: IS_EVEN DUP 0 = { DROP TRUE } { 1 - IS_EVEN NOT } IF ! ;
5 FACT
7 FIB
DUP2
GCD
DUP IS_EVEN