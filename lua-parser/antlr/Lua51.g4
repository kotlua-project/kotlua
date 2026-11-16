grammar Lua51;

expressionOrStatements
    : exp EOF       # SingleExpInput
    | chunk EOF     # StatementsInput
    ;

chunk
    : (stat ';'?)* (laststat ';'?)?
    ;

block
    : chunk
    ;

stat
    : varlist '=' explist                                   # AssignStat
    | functioncall                                          # FunctionCallStat
    | 'do' block 'end'                                      # DoStat
    | 'while' exp 'do' block 'end'                          # WhileStat
    | 'repeat' block 'until' exp                            # RepeatStat
    | 'if' cond=exp 'then' thenBlock=block
      ('elseif' elseifCond+=exp 'then' elseifBlock+=block)*
      ('else' elseBlock=block)?
      'end'                                                 # IfStat
    | 'for' NAME '=' exp ',' exp (',' exp)? 'do' block 'end' # NumericForStat
    | 'for' namelist 'in' explist 'do' block 'end'          # GenericForStat
    | 'function' funcname funcbody                          # FunctionDeclStat
    | 'local' 'function' NAME funcbody                      # LocalFunctionDeclStat
    | 'local' namelist ('=' explist)?                       # LocalVarDeclStat
    ;


laststat
    : 'return' explist?                                     # ReturnStat
    | 'break'                                               # BreakStat
    ;

funcname
    : NAME ('.' NAME)* (':' NAME)?
    ;

varlist
    : var (',' var)*
    ;

var
    : NAME                                                  # NameVar
    | prefixexp '[' exp ']'                                 # IndexVar
    | prefixexp '.' NAME                                    # FieldVar
    ;

namelist
    : NAME (',' NAME)*
    ;

explist
    : exp (',' exp)*
    ;

exp
    : 'nil'                                                 # NilExpr
    | 'false'                                               # FalseExpr
    | 'true'                                                # TrueExpr
    | NUMBER                                                # NumberExpr
    | STRING                                                # StringExpr
    | '...'                                                 # VarargExpr
    | function                                              # FunctionExpr
    | prefixexp                                             # PrefixExpr
    | tableconstructor                                      # TableConstructorExpr
    | <assoc=right> exp op='^' exp                          # PowExpr
    | op=('not' | '#' | '-') exp                            # UnaryExpr
    | exp op=('*' | '/' | '%') exp                          # MulDivModExpr
    | exp op=('+' | '-') exp                                # AddSubExpr
    | <assoc=right> exp op='..' exp                         # ConcatExpr
    | exp op=('<' | '>' | '<=' | '>=' | '~=' | '==') exp    # RelExpr
    | exp op='and' exp                                      # AndExpr
    | exp op='or' exp                                       # OrExpr
    ;

prefixexp
    : NAME                                                  # NamePrefixExp
    | '(' exp ')'                                           # ParenPrefixExp
    | prefixexp '[' exp ']'                                 # IndexPrefixExp
    | prefixexp '.' NAME                                    # FieldPrefixExp
    | prefixexp args                                        # CallPrefixExp
    | prefixexp ':' NAME args                               # MethodCallPrefixExp
    ;

functioncall
    : prefixexp args                                        # CallFunctionCall
    | prefixexp ':' NAME args                               # MethodCallFunctionCall
    ;

args
    : '(' explist? ')'                                      # ParenArgs
    | tableconstructor                                      # TableArgs
    | STRING                                                # StringArgs
    ;

function
    : 'function' funcbody
    ;

funcbody
    : '(' parlist? ')' block 'end'
    ;

parlist
    : namelist (',' '...')?                                 # NamedParList
    | '...'                                                 # VarargParList
    ;

tableconstructor
    : '{' fieldlist? '}'
    ;

fieldlist
    : field (fieldsep field)* fieldsep?
    ;

field
    : '[' exp ']' '=' exp                                   # IndexedField
    | NAME '=' exp                                          # NamedField
    | exp                                                   # PositionalField
    ;

fieldsep
    : ','
    | ';'
    ;

NAME
    : [a-zA-Z_][a-zA-Z_0-9]*
    ;

NUMBER
    : INT
    | HEX
    | FLOAT
    ;

STRING
    : NORMALSTRING
    | CHARSTRING
    | LONGSTRING
    ;

fragment INT
    : [0-9]+
    ;

fragment HEX
    : '0' [xX] [0-9a-fA-F]+
    ;

fragment FLOAT
    : [0-9]+ '.' [0-9]* EXPONENT?
    | '.' [0-9]+ EXPONENT?
    | [0-9]+ EXPONENT
    ;

fragment EXPONENT
    : [eE] [+-]? [0-9]+
    ;

fragment NORMALSTRING
    : '"' (ESCAPE_SEQUENCE | ~["\\\r\n])* '"'
    ;

fragment CHARSTRING
    : '\'' (ESCAPE_SEQUENCE | ~['\\\r\n])* '\''
    ;

fragment LONGSTRING
    : '[' NESTED_STR ']'
    ;

fragment NESTED_STR
    : '=' NESTED_STR '='
    | '[' .*? ']'
    ;

fragment ESCAPE_SEQUENCE
    : '\\' [abfnrtv\\"']
    | '\\' ('\r'? '\n' | '\n' '\r'?)
    | '\\' [0-9] [0-9]? [0-9]?
    ;

COMMENT
    : '--[' NESTED_STR ']' -> channel(HIDDEN)
    ;

LINE_COMMENT
    : '--' ~[\r\n]* -> channel(HIDDEN)
    ;

WS
    : [ \t\r\n\u000B\u000C]+ -> skip
    ;
