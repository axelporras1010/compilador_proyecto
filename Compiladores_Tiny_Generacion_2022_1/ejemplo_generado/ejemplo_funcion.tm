* * Compilacion TINY para la maquina TM
* * Prefacio estandar
0:      LDC       5,0(0)        init: GP = 0
1:      LDC       6,1023(0)      init: MP = 1023 (tope de memoria temporales)
2:      LDC       3,512(0)      init: SP = 512 (pila activacion)
3:      LDA       4,0(3)        init: FP = SP
* * Fin del prefacio estandar
* -> programa
* -> declaracion: x
* Declaracion de variable: x (global)
4:      LDC       0,0(0)        global: inicializar variable x a cero
5:      ST        0,0(5)        global: almacenar en direccion 0
* <- declaracion
* registrada funcion: duplicar
* -> declaracion: resultado
* Declaracion de variable: resultado (local)
* <- declaracion
* -> asignacion
* -> constante
6:      LDC       0,5(0)        cargar constante: 5
* <- constante
7:      ST        0,0(5)        asignacion: global x
* <- asignacion
* -> asignacion
* -> llamada funcion: duplicar
* -> identificador
8:      LD        0,0(5)        id: cargar global x
* <- identificador
9:      ST        0,0(3)        call: push arg
10:     LDA       3,-1(3)       call: sp--
11:     LDA       0,3(7)        call: calcular return addr (PC+3)
12:     ST        0,0(3)        call: push RA
13:     LDA       3,-1(3)       call: sp--
14:     ST        4,0(3)        call: push DL (FP)
15:     LDA       3,-1(3)       call: sp--
16:     LDA       4,1(3)        call: FP=SP+1
17:     LDA       7,0(7)        call: salto a funcion duplicar
* <- llamada funcion
18:     ST        0,4(5)        asignacion: global resultado
* <- asignacion
* -> escribir
* -> identificador
19:     LD        0,4(5)        id: cargar global resultado
* <- identificador
20:     OUT       0,0,0         escribir: genero la salida de la expresion
* <- escribir
* -> escribir
* -> identificador
21:     LD        0,0(5)        id: cargar global x
* <- identificador
22:     OUT       0,0,0         escribir: genero la salida de la expresion
* <- escribir
* <- programa
* Fin de la ejecucion.
23:     HALT      0,0,0         
