: TRUE_ DROP ;
: FALSE_ SWAP DROP ;
: T { TRUE_ } ;
: F { FALSE_ } ;
: IFX ROT ! ;

: NOT_ F T IFX ;
: AND_ F IFX ;
: OR_ T SWAP IFX ;
