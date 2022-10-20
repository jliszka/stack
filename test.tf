: OVER >R DUP R> SWAP ;
: DUP2 OVER OVER ;
: ROT >R SWAP R> SWAP ;
: FACT DUP 1 = { } { DUP 1 - FACT * } IF ! ;
: GCD DUP 0 = { DROP } { SWAP OVER % GCD } IF ! ;
: FIB DUP 2 < { DROP 1 } { DUP 1 - FIB SWAP 2 - FIB + } IF ! ;
: IS_ZERO 0 = ;
: IFX IF ! ;
: IS_EVEN DUP 0 = { DROP TRUE } { 1 - IS_EVEN NOT } IFX ;
: PLUS1 1 + ;
: APPLY2 DUP >R ! R> ! ;

: P DUP 1 = { DROP TRUE } { DUP2 % 0 = { DROP FALSE } { 1 - P } IFX } IFX ;
: PRIME? DUP 2 / P SWAP DROP ;

#3 { FACT } APPLY2
#7 FIB
#DUP2
#GCD
#DUP IS_EVEN

