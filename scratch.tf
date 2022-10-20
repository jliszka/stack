
# : CALL1 ! 1 +   ( ( * -- * int) -- int )
# { 3 } CALL1     ( -- ( -- int ) ) => ( -- int )
# { + } CALL1     ( -- ( int int -- int ) ) => ( int int -- int )

# : CALLE ! 1 =   ( ( * -- * int) -- bool )
# { 3 } CALLE     ( -- ( -- int ) ) => ( -- bool )
# { + } CALLE     ( -- ( int int -- int ) ) => ( int int -- bool )
# { DUP } CALLE     ( -- ( a a -- a ) ) => ( int -- int int ) => ( int -- int bool ) 


# : COMPOSE { ??? }

# : WHILE >R OVER R@ ! { @R ! R> R> WHILE } { } IF ! ;
# 0 10 { SWAP OVER + SWAP 1 - } { 0 > } WHILE


# 1-2, 0-1, 2-1 => 1-2
# 0-1 (0-0)
# 0-1 (1-2, 0-1, 2-1, a-b, 2-1) => (1-2, a-b, 2-1)
# 3-1, 1-0, {1-1}

# 1-4, 3-1, 1-0, 1-1 => 1-1