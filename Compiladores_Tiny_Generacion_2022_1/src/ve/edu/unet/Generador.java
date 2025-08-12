package ve.edu.unet;

import ve.edu.unet.nodosAST.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Generador {
	/* Ilustracion de la disposicion de la memoria en
	 * este ambiente de ejecucion para el lenguaje Tiny
	 *
	 * |t1	|<- mp (Maxima posicion de memoria de la TM
	 * |t1	|<- desplazamientoTmp (tope actual)
	 * |free|
	 * |free|
	 * |... |
	 * |x	|
	 * |y	|<- gp
	 * 
	 * */
	
	
	
	/* desplazamientoTmp es una variable inicializada en 0
	 * y empleada como el desplazamiento de la siguiente localidad
	 * temporal disponible desde la parte superior o tope de la memoria
	 * (la que apunta el registro MP).
	 * 
	 * - Se decrementa (desplazamientoTmp--) despues de cada almacenamiento y
	 * 
	 * - Se incrementa (desplazamientoTmp++) despues de cada eliminacion/carga en 
	 *   otra variable de un valor de la pila.
	 * 
	 * Pudiendose ver como el apuntador hacia el tope de la pila temporal
	 * y las llamadas a la funcion emitirRM corresponden a una inserccion 
	 * y extraccion de esta pila
	 */
	private static int desplazamientoTmp = 0;
	private static TablaSimbolos tablaSimbolos = null;
	
	// Variables para manejo de funciones
	private static int contadorEtiquetas = 0;
	private static java.util.Stack<Integer> pilaBreak = new java.util.Stack<Integer>();
	private static java.util.Stack<Integer> pilaContinue = new java.util.Stack<Integer>();

	// Compilación diferida de funciones
	private static final Map<String, NodoFuncion> funcionesRegistradas = new HashMap<>();
	private static final Map<String, Integer> inicioFuncion = new HashMap<>();
	private static final Set<String> funcionesEmitidas = new HashSet<>();

	// Layout de activación por función
	private static final Map<String, FunctionLayout> layoutsFuncion = new HashMap<>();
	private static String funcionActual = null;

	private static class FunctionLayout {
		Map<String, Integer> offsetParametros = new HashMap<>(); // desde FP (>= 2)
		Map<String, Integer> offsetLocales = new HashMap<>();    // offsets negativos (< 0)
		Map<String, Integer> tamanioLocalArray = new HashMap<>();
		Set<String> parametrosArray = new java.util.HashSet<>();
		int numParametros = 0;
		int slotsLocales = 0; // cantidad total de slots para locales (incluye arrays)
	}
	
	public static void setTablaSimbolos(TablaSimbolos tabla){
		tablaSimbolos = tabla;
	}
	
	public static void generarCodigoObjeto(NodoBase raiz){
		System.out.println();
		System.out.println();
		System.out.println("------ CODIGO OBJETO DEL LENGUAJE TINY GENERADO PARA LA TM ------");
		System.out.println();
		System.out.println();
		generarPreludioEstandar();
		generar(raiz);
		/*Genero el codigo de finalizacion de ejecucion del codigo*/   
		UtGen.emitirComentario("Fin de la ejecucion.");
		UtGen.emitirRO("HALT", 0, 0, 0, "");
		System.out.println();
		System.out.println();
		System.out.println("------ FIN DEL CODIGO OBJETO DEL LENGUAJE TINY GENERADO PARA LA TM ------");
	}
	
	// Método sobrecargado para generar código a archivo .tm
	public static void generarCodigoObjetoAArchivo(NodoBase raiz, String nombreArchivo) throws IOException {
		try {
			// Inicializar salida a archivo
			UtGen.inicializarArchivoSalida(nombreArchivo);
			
			System.out.println("Generando código objeto en archivo: " + nombreArchivo);
			
			// Generar código objeto
			generarPreludioEstandar();
			generar(raiz);
			/*Genero el codigo de finalizacion de ejecucion del codigo*/   
			UtGen.emitirComentario("Fin de la ejecucion.");
			UtGen.emitirRO("HALT", 0, 0, 0, "");
			
			System.out.println("Archivo " + nombreArchivo + " generado exitosamente.");
		} finally {
			// Asegurar que el archivo se cierre
			UtGen.cerrarArchivoSalida();
		}
	}
	
	//Funcion principal de generacion de codigo
	//prerequisito: Fijar la tabla de simbolos antes de generar el codigo objeto 
	private static void generar(NodoBase nodo){
		if(tablaSimbolos!=null){
			if (nodo instanceof NodoPrograma){
				generarPrograma(nodo);
			}else if (nodo instanceof NodoDeclaracion){
				generarDeclaracion(nodo);
			}else if (nodo instanceof NodoFuncion){
				registrarFuncion((NodoFuncion) nodo);
			}else if (nodo instanceof NodoIf){
				generarIf(nodo);
			}else if (nodo instanceof NodoRepeat){
				generarRepeat(nodo);
			}else if (nodo instanceof NodoFor){
				generarFor(nodo);
			}else if (nodo instanceof NodoAsignacion){
				generarAsignacion(nodo);
			}else if (nodo instanceof NodoLeer){
				generarLeer(nodo);
			}else if (nodo instanceof NodoEscribir){
				generarEscribir(nodo);
			}else if (nodo instanceof NodoLlamadaFuncion){
				generarLlamadaFuncion(nodo);
			}else if (nodo instanceof NodoReturn){
				generarReturn(nodo);
			}else if (nodo instanceof NodoBreak){
				generarBreak(nodo);
			}else if (nodo instanceof NodoContinue){
				generarContinue(nodo);
			}else if (nodo instanceof NodoValor){
				generarValor(nodo);
			}else if (nodo instanceof NodoString){
				generarString(nodo);
			}else if (nodo instanceof NodoIdentificador){
				generarIdentificador(nodo);
			}else if (nodo instanceof NodoOperacion){
				generarOperacion(nodo);
			}else{
				System.out.println("BUG: Tipo de nodo a generar desconocido: " + nodo.getClass().getSimpleName());
			}
			/*Si el hijo de extrema izquierda tiene hermano a la derecha lo genero tambien*/
			if(nodo.TieneHermano())
				generar(nodo.getHermanoDerecha());
		}else
			System.out.println("ERROR: por favor fije la tabla de simbolos a usar antes de generar codigo objeto!!!");
	}

	private static void generarPrograma(NodoBase nodo){
		NodoPrograma n = (NodoPrograma)nodo;
		if(UtGen.debug) UtGen.emitirComentario("-> programa");
		
		// Generar declaraciones globales
		if(n.getGlobal_block() != null){
			generar(n.getGlobal_block());
		}
		
		// Registrar funciones (sin generar su cuerpo)
		if(n.getFunction_block() != null){
			generar(n.getFunction_block());
		}
		
		// Generar programa principal
		if(n.getMain() != null){
			generar(n.getMain());
		}
		
		if(UtGen.debug) UtGen.emitirComentario("<- programa");
	}

	private static void generarDeclaracion(NodoBase nodo){
		NodoDeclaracion n = (NodoDeclaracion)nodo;
		if(UtGen.debug) UtGen.emitirComentario("-> declaracion: " + n.getNombreVariable());
		
		// Si es local, inicializamos a 0 en el frame activo
		if(!n.isEsGlobal() && funcionActual != null){
			FunctionLayout fl = layoutsFuncion.get(funcionActual);
			if (fl != null) {
				Integer offLocal = fl.offsetLocales.get(n.getNombreVariable());
				Integer offParam = fl.offsetParametros.get(n.getNombreVariable());
				if(offLocal != null){
					if(n.isEsArray()){
						int tam = fl.tamanioLocalArray.getOrDefault(n.getNombreVariable(), 0);
						// Inicializar array local a 0
						for(int i=0;i<tam;i++){
							UtGen.emitirRM("LDC", UtGen.AC, 0, 0, "local array: init 0");
							UtGen.emitirRM("ST", UtGen.AC, offLocal - i, UtGen.FP, "local array: store");
						}
					}else{
						UtGen.emitirRM("LDC", UtGen.AC, 0, 0, "local: init 0");
						UtGen.emitirRM("ST", UtGen.AC, offLocal, UtGen.FP, "local: store");
					}
					if(UtGen.debug) UtGen.emitirComentario("<- declaracion local");
					return;
				} else if (offParam != null) {
					// Los parámetros ya están en el frame; no hacer nada
					if(UtGen.debug) UtGen.emitirComentario("<- declaracion parametro");
					return;
				}
			}
		}
		
		// Globales
		// Obtener la dirección asignada por la tabla de símbolos
		int direccion = tablaSimbolos.getDireccion(n.getNombreVariable());
		
		if(n.isEsArray()){
			// Declaración de array global
			UtGen.emitirComentario("Declaracion de array: " + n.getNombreVariable() + 
					      (n.getTamaño() != null ? " tamaño definido" : " tamaño por parámetro"));
			
			if(n.getTamaño() != null && n.isEsGlobal()){
				// Array global con tamaño definido - reservar espacio (inicializar a 0)
				NodoBase tb = n.getTamaño();
				int tam = 0;
				if (tb instanceof NodoValor) tam = ((NodoValor)tb).getValorEntero();
				for(int i = 0; i < tam; i++){
					UtGen.emitirRM("LDC", UtGen.AC, 0, 0, "array: inicializar");
					UtGen.emitirRM("ST", UtGen.AC, direccion + i, UtGen.GP, "array: pos global");
				}
			}
		} else {
			// Declaración de variable simple global
			UtGen.emitirComentario("Declaracion de variable: " + n.getNombreVariable() + 
					      (n.isEsGlobal() ? " (global)" : " (local)"));
			
			if(n.isEsGlobal()){
				// Variable global - inicializar a cero
				UtGen.emitirRM("LDC", UtGen.AC, 0, 0, "global: inicializar variable " + n.getNombreVariable() + " a cero");
				UtGen.emitirRM("ST", UtGen.AC, direccion, UtGen.GP, "global: almacenar en direccion " + direccion);
			}
		}
		
		if(UtGen.debug) UtGen.emitirComentario("<- declaracion");
	}

	// Registrar función en la primera pasada (sin generar código)
	private static void registrarFuncion(NodoFuncion funcion) {
		if (funcion.getNombre() == null) {
			UtGen.emitirComentario("ADVERTENCIA: funcion sin nombre");
			return;
		}
		funcionesRegistradas.put(funcion.getNombre(), funcion);
		// Precalcular layout de la función
		FunctionLayout fl = construirLayoutFuncion(funcion);
		layoutsFuncion.put(funcion.getNombre(), fl);
		UtGen.emitirComentario("registrada funcion: " + funcion.getNombre());
	}
	
	private static FunctionLayout construirLayoutFuncion(NodoFuncion f){
		FunctionLayout fl = new FunctionLayout();
		// Parametros
		java.util.List<NodoDeclaracion> listaParams = new java.util.ArrayList<>();
		NodoBase p = f.getParametros();
		while(p != null){
			if(p instanceof NodoDeclaracion){
				listaParams.add((NodoDeclaracion)p);
			}
			p = p.getHermanoDerecha();
		}
		fl.numParametros = listaParams.size();
		for (int idx = 0; idx < listaParams.size(); idx++){
			NodoDeclaracion pd = listaParams.get(idx);
			int off = 2 + (listaParams.size() - 1 - idx); // ajustar por orden de push izq->der
			fl.offsetParametros.put(pd.getNombreVariable(), off);
			if(pd.isEsArray()) fl.parametrosArray.add(pd.getNombreVariable());
		}
		// Locales: recorrer cuerpo
		contarLocales(f.getCuerpo(), fl);
		return fl;
	}
	
	private static void contarLocales(NodoBase nodo, FunctionLayout fl){
		if(nodo == null) return;
		if(nodo instanceof NodoDeclaracion){
			NodoDeclaracion nd = (NodoDeclaracion)nodo;
			if(!nd.isEsGlobal()){
				if(nd.isEsArray()){
					int tam = 0;
					if(nd.getTamaño() instanceof NodoValor){
						tam = ((NodoValor)nd.getTamaño()).getValorEntero();
					}
					// reservar bloque contiguo
					int base = -(fl.slotsLocales + 1);
					fl.offsetLocales.put(nd.getNombreVariable(), base);
					fl.tamanioLocalArray.put(nd.getNombreVariable(), tam);
					fl.slotsLocales += tam;
				} else {
					int off = -(fl.slotsLocales + 1);
					fl.offsetLocales.put(nd.getNombreVariable(), off);
					fl.slotsLocales += 1;
				}
			}
		}
		// Recorrer hijos
		if(nodo instanceof NodoIf){
			contarLocales(((NodoIf)nodo).getParteThen(), fl);
			contarLocales(((NodoIf)nodo).getParteElse(), fl);
			contarLocales(((NodoIf)nodo).getPrueba(), fl);
		} else if (nodo instanceof NodoRepeat){
			contarLocales(((NodoRepeat)nodo).getCuerpo(), fl);
			contarLocales(((NodoRepeat)nodo).getPrueba(), fl);
		} else if (nodo instanceof NodoFor){
			NodoFor nf = (NodoFor)nodo;
			contarLocales(nf.getValorInicial(), fl);
			contarLocales(nf.getValorFinal(), fl);
			contarLocales(nf.getIncremento(), fl);
			contarLocales(nf.getCuerpo(), fl);
		} else if (nodo instanceof NodoAsignacion){
			contarLocales(((NodoAsignacion)nodo).getExpresion(), fl);
			contarLocales(((NodoAsignacion)nodo).getIndice(), fl);
		} else if (nodo instanceof NodoEscribir){
			contarLocales(((NodoEscribir)nodo).getExpresion(), fl);
		} else if (nodo instanceof NodoOperacion){
			NodoOperacion no = (NodoOperacion)nodo;
			contarLocales(no.getOpIzquierdo(), fl);
			contarLocales(no.getOpDerecho(), fl);
		} else if (nodo instanceof NodoLlamadaFuncion){
			contarLocales(((NodoLlamadaFuncion)nodo).getArgumentos(), fl);
		}
		if(nodo.TieneHermano()) contarLocales(nodo.getHermanoDerecha(), fl);
	}

	private static void generarFor(NodoBase nodo){
		NodoFor n = (NodoFor)nodo;
		if(UtGen.debug) UtGen.emitirComentario("-> for");
		
		// Etiquetas para control de flujo
		int etiquetaInicio = contadorEtiquetas++;
		int etiquetaFin = contadorEtiquetas++;
		int etiquetaContinue = contadorEtiquetas++;
		
		// Agregar etiquetas a las pilas para break y continue
		pilaBreak.push(etiquetaFin);
		pilaContinue.push(etiquetaContinue);
		
		// Inicialización de la variable de control
		generar(n.getValorInicial());
		int direccionVar = tablaSimbolos.getDireccion(n.getVariable());
		UtGen.emitirRM("ST", UtGen.AC, direccionVar, UtGen.GP, "for: inicializar variable " + n.getVariable());
		
		// Etiqueta de inicio del bucle
		int localidadInicio = UtGen.emitirSalto(0);
		UtGen.emitirComentario("for: inicio del bucle");
		
		// Verificar condición (variable <= valor_final)
		UtGen.emitirRM("LD", UtGen.AC, direccionVar, UtGen.GP, "for: cargar variable de control");
		UtGen.emitirRM("ST", UtGen.AC, desplazamientoTmp--, UtGen.MP, "for: guardar variable en pila temp");
		
		generar(n.getValorFinal());
		UtGen.emitirRM("LD", UtGen.AC1, ++desplazamientoTmp, UtGen.MP, "for: cargar variable de pila temp");
		
		// Comparación: si variable > valor_final, saltar al final
		UtGen.emitirRO("SUB", UtGen.AC, UtGen.AC1, UtGen.AC, "for: variable - valor_final");
		int localidadSaltoFin = UtGen.emitirSalto(1);
		UtGen.emitirComentario("for: salto condicional al final");
		
		// Cuerpo del bucle
		generar(n.getCuerpo());
		
		// Etiqueta para continue
		int localidadContinue = UtGen.emitirSalto(0);
		UtGen.emitirComentario("for: punto de continue");
		
		// Incremento de la variable
		UtGen.emitirRM("LD", UtGen.AC, direccionVar, UtGen.GP, "for: cargar variable para incremento");
		UtGen.emitirRM("ST", UtGen.AC, desplazamientoTmp--, UtGen.MP, "for: guardar variable en pila temp");
		
		generar(n.getIncremento());
		UtGen.emitirRM("LD", UtGen.AC1, ++desplazamientoTmp, UtGen.MP, "for: cargar variable de pila temp");
		UtGen.emitirRO("ADD", UtGen.AC, UtGen.AC1, UtGen.AC, "for: incrementar variable");
		UtGen.emitirRM("ST", UtGen.AC, direccionVar, UtGen.GP, "for: guardar variable incrementada");
		
		// Salto al inicio del bucle
		UtGen.emitirRM_Abs("LDA", UtGen.PC, localidadInicio, "for: salto al inicio");
		
		// Etiqueta de fin del bucle
		int localidadFin = UtGen.emitirSalto(0);
		UtGen.emitirComentario("for: fin del bucle");
		
		// Completar salto condicional
		UtGen.cargarRespaldo(localidadSaltoFin);
		UtGen.emitirRM_Abs("JGT", UtGen.AC, localidadFin, "for: saltar si variable > final");
		UtGen.restaurarRespaldo();
		
		// Remover etiquetas de las pilas
		pilaBreak.pop();
		pilaContinue.pop();
		
		if(UtGen.debug) UtGen.emitirComentario("<- for");
	}

	private static void generarLlamadaFuncion(NodoBase nodo){
		NodoLlamadaFuncion n = (NodoLlamadaFuncion)nodo;
		if(UtGen.debug) UtGen.emitirComentario("-> llamada funcion: " + n.getNombreFuncion());
		
		// Preparar datos de la función
		NodoFuncion defFuncion = funcionesRegistradas.get(n.getNombreFuncion());
		FunctionLayout fl = layoutsFuncion.get(n.getNombreFuncion());
		int numArgsEsperados = (fl != null) ? fl.numParametros : 0;
		
		// 1) Procesar y apilar argumentos (de izquierda a derecha)
		int numArgs = 0;
		java.util.List<NodoBase> args = new java.util.ArrayList<>();
		NodoBase argNode = n.getArgumentos();
		while(argNode != null){ args.add(argNode); argNode = argNode.getHermanoDerecha(); }
		for(int idx=0; idx<args.size(); idx++){
			NodoBase arg = args.get(idx);
			// Si el parametro esperado es array, pasar la base (direccion)
			boolean pasarBaseArray = false;
			if (fl != null && defFuncion != null){
				// recuperar el nodo del parametro correspondiente (en la misma posicion)
				NodoBase p = defFuncion.getParametros();
				for(int k=0; k<idx && p!=null; k++) p = p.getHermanoDerecha();
				if(p instanceof NodoDeclaracion){
					pasarBaseArray = ((NodoDeclaracion)p).isEsArray();
				}
			}
			if(pasarBaseArray && arg instanceof NodoIdentificador){
				// Empujar direccion base del arreglo segun sea global/param/local
				DireccionArray da = calcularBaseArray(((NodoIdentificador)arg).getNombre());
				if(da.esGlobal){
					UtGen.emitirRM("LDC", UtGen.AC, da.baseDireccion, 0, "arg array: base global");
					// AC = dir + GP
					UtGen.emitirRO("ADD", UtGen.AC, UtGen.AC, UtGen.GP, "arg array: base absoluta");
				} else {
					// AC = FP + offset
					UtGen.emitirRM("LDA", UtGen.AC, da.offsetFP, UtGen.FP, "arg array: base local/param");
				}
			} else {
				generar(arg);
			}
			// push
			UtGen.emitirRM("ST", UtGen.AC, 0, UtGen.SP, "call: push arg");
			UtGen.emitirRM("LDA", UtGen.SP, -1, UtGen.SP, "call: sp--");
			numArgs++;
		}
		
		// 2) Apilar direccion de retorno y enlace dinamico
		// Nota: hay 5 instrucciones entre este punto y el salto LDA PC a la función,
		// y debemos regresar a la instrucción SIGUIENTE al salto, por lo que es PC+7
		UtGen.emitirRM("LDA", UtGen.AC, 7, UtGen.PC, "call: calcular return addr (PC+7)");
		UtGen.emitirRM("ST", UtGen.AC, 0, UtGen.SP, "call: push RA");
		UtGen.emitirRM("LDA", UtGen.SP, -1, UtGen.SP, "call: sp--");
		UtGen.emitirRM("ST", UtGen.FP, 0, UtGen.SP, "call: push DL (FP)");
		UtGen.emitirRM("LDA", UtGen.SP, -1, UtGen.SP, "call: sp--");
		// FP = SP + 1 (apunta al enlace dinamico)
		UtGen.emitirRM("LDA", UtGen.FP, 1, UtGen.SP, "call: FP=SP+1");
		
		// Compilación diferida: emitir función si es la primera vez
		Integer inicio = inicioFuncion.get(n.getNombreFuncion());
		if (inicio == null) {
			int posLlamada = UtGen.emitirSalto(1);
			UtGen.restaurarRespaldo();
			inicio = UtGen.emitirSalto(0);
			inicioFuncion.put(n.getNombreFuncion(), inicio);
			funcionesEmitidas.add(n.getNombreFuncion());
			// Emitir prólogo de función: reservar espacio para locales
			UtGen.emitirComentario("=== INICIO FUNCION " + n.getNombreFuncion() + " ===");
			FunctionLayout nfl = layoutsFuncion.get(n.getNombreFuncion());
			int k = (nfl != null) ? nfl.slotsLocales : 0;
			if(k > 0){
				UtGen.emitirRM("LDA", UtGen.SP, -k, UtGen.SP, "prologo: reservar locales");
			}
			// Generar cuerpo
			NodoFuncion def = funcionesRegistradas.get(n.getNombreFuncion());
			String funcGuardada = funcionActual;
			funcionActual = n.getNombreFuncion();
			if (def != null && def.getCuerpo() != null) {
				generar(def.getCuerpo());
			}
			funcionActual = funcGuardada;
			// Return implícito
			emitirEpilogoFuncion(n.getNombreFuncion());
			UtGen.emitirComentario("=== FIN FUNCION " + n.getNombreFuncion() + " ===");
			// Parchar llamada
			UtGen.cargarRespaldo(posLlamada);
			UtGen.emitirRM_Abs("LDA", UtGen.PC, inicio, "call: salto a funcion " + n.getNombreFuncion());
			UtGen.restaurarRespaldo();
		} else {
			UtGen.emitirRM_Abs("LDA", UtGen.PC, inicio, "call: salto a funcion " + n.getNombreFuncion());
		}
		
		// El callee limpia su propio frame (incluye parametros), no hay nada que hacer
		if(UtGen.debug) UtGen.emitirComentario("<- llamada funcion");
	}
	
	private static void emitirEpilogoFuncion(String nombreFuncion){
		FunctionLayout fl = layoutsFuncion.get(nombreFuncion);
		int p = (fl != null) ? fl.numParametros : 0;
		// Desalojar locales: SP = FP
		UtGen.emitirRM("LDA", UtGen.SP, 0, UtGen.FP, "epilogo: SP=FP");
		// Cargar RA y DL
		UtGen.emitirRM("LD", UtGen.AC1, 1, UtGen.FP, "epilogo: cargar RA");
		UtGen.emitirRM("LD", UtGen.FP, 0, UtGen.FP, "epilogo: restaurar FP (DL)");
		// Desalojar RA+DL+params (ajuste de puntero de pila)
		UtGen.emitirRM("LDA", UtGen.SP, 2 + p, UtGen.SP, "epilogo: limpiar frame completo");
		// Saltar a RA
		UtGen.emitirRM("LDA", UtGen.PC, 0, UtGen.AC1, "funcion: retorno");
	}

	private static void generarReturn(NodoBase nodo){
		NodoReturn n = (NodoReturn)nodo;
		if(UtGen.debug) UtGen.emitirComentario("-> return");
		
		// Evaluar expresión de retorno dejando resultado en AC
		if(n.getExpresion() != null){
			generar(n.getExpresion());
		}
		// Epilogo y salto
		emitirEpilogoFuncion(funcionActual);
		
		if(UtGen.debug) UtGen.emitirComentario("<- return");
	}

	private static void generarBreak(NodoBase nodo){
		if(UtGen.debug) UtGen.emitirComentario("-> break");
		
		if(!pilaBreak.isEmpty()){
			int etiquetaSalida = pilaBreak.peek();
			UtGen.emitirComentario("break: salto al final del bucle");
			// El salto se resolverá cuando se complete el bucle
		} else {
			UtGen.emitirComentario("ERROR: break fuera de bucle");
		}
		
		if(UtGen.debug) UtGen.emitirComentario("<- break");
	}

	private static void generarContinue(NodoBase nodo){
		if(UtGen.debug) UtGen.emitirComentario("-> continue");
		
		if(!pilaContinue.isEmpty()){
			int etiquetaContinue = pilaContinue.peek();
			UtGen.emitirComentario("continue: salto al incremento del bucle");
			// El salto se resolverá cuando se complete el bucle
		} else {
			UtGen.emitirComentario("ERROR: continue fuera de bucle");
		}
		
		if(UtGen.debug) UtGen.emitirComentario("<- continue");
	}

	private static void generarString(NodoBase nodo){
		NodoString n = (NodoString)nodo;
		if(UtGen.debug) UtGen.emitirComentario("-> string");
		
		// Para strings, emitimos cada carácter
		String texto = n.getValor().replace("\"", ""); // Remover comillas
		UtGen.emitirComentario("String: " + n.getValor());
		
		for(int i = 0; i < texto.length(); i++){
			UtGen.emitirRM("LDC", UtGen.AC, (int)texto.charAt(i), 0, "string: cargar caracter '" + texto.charAt(i) + "'");
			UtGen.emitirRO("OUT", UtGen.AC, 0, 0, "string: escribir caracter");
		}
		
		if(UtGen.debug) UtGen.emitirComentario("<- string");
	}

	private static void generarIf(NodoBase nodo){
    	NodoIf n = (NodoIf)nodo;
		int localidadSaltoElse,localidadSaltoEnd,localidadActual;
		if(UtGen.debug)	UtGen.emitirComentario("-> if");
		/*Genero el codigo para la parte de prueba del IF*/
		generar(n.getPrueba());
		localidadSaltoElse = UtGen.emitirSalto(1);
		UtGen.emitirComentario("If: el salto hacia el else debe estar aqui");
		/*Genero la parte THEN*/
		generar(n.getParteThen());
		
		/*Genero la parte ELSE*/
		if(n.getParteElse()!=null){
			localidadSaltoEnd = UtGen.emitirSalto(1);
			UtGen.emitirComentario("If: el salto hacia el final debe estar aqui");
			localidadActual = UtGen.emitirSalto(0);
			UtGen.cargarRespaldo(localidadSaltoElse);
			UtGen.emitirRM_Abs("JEQ", UtGen.AC, localidadActual, "if: jmp hacia else");
			UtGen.restaurarRespaldo();
			generar(n.getParteElse());
			localidadActual = UtGen.emitirSalto(0);
			UtGen.cargarRespaldo(localidadSaltoEnd);
			UtGen.emitirRM_Abs("LDA", UtGen.PC, localidadActual, "if: jmp hacia el final");
			UtGen.restaurarRespaldo();
    	} else {
			// Si no hay else, solo necesitamos completar el salto
			localidadActual = UtGen.emitirSalto(0);
			UtGen.cargarRespaldo(localidadSaltoElse);
			UtGen.emitirRM_Abs("JEQ", UtGen.AC, localidadActual, "if: jmp hacia el final");
			UtGen.restaurarRespaldo();
		}
		
		if(UtGen.debug)	UtGen.emitirComentario("<- if");
	}
	
	private static void generarRepeat(NodoBase nodo){
    	NodoRepeat n = (NodoRepeat)nodo;
		int localidadSaltoInicio;
		if(UtGen.debug)	UtGen.emitirComentario("-> repeat");
			localidadSaltoInicio = UtGen.emitirSalto(0);
			UtGen.emitirComentario("repeat: el salto hacia el final (luego del cuerpo) del repeat debe estar aqui");
			/* Genero el cuerpo del repeat */
			generar(n.getCuerpo());
			/* Genero el codigo de la prueba del repeat */
			generar(n.getPrueba());
			UtGen.emitirRM_Abs("JEQ", UtGen.AC, localidadSaltoInicio, "repeat: jmp hacia el inicio del cuerpo");
		if(UtGen.debug)	UtGen.emitirComentario("<- repeat");
	}		
	
	private static void generarAsignacion(NodoBase nodo){
		NodoAsignacion n = (NodoAsignacion)nodo;
		int direccion;
		if(UtGen.debug)	UtGen.emitirComentario("-> asignacion");		
		
		/* Genero el codigo para la expresion a la derecha de la asignacion */
		generar(n.getExpresion());
		
		if(n.esAsignacionArray()){
			// Asignación a array: arr[indice] = valor
			UtGen.emitirRM("ST", UtGen.AC, desplazamientoTmp--, UtGen.MP, "asignacion array: guardar valor");
			
			// Calcular base del array y direccion efectiva
			generar(n.getIndice()); // deja indice en AC
			String nombre = n.getIdentificador();
			DireccionArray da = calcularBaseArray(nombre);
			if(da.esParametroArray){
				// AC1 = base desde FP+offset
				UtGen.emitirRM("LD", UtGen.AC1, da.offsetFP, UtGen.FP, "asig arr: cargar base param array");
				UtGen.emitirRO("ADD", UtGen.AC, UtGen.AC, UtGen.AC1, "asig arr: base+idx");
			} else if(da.esGlobal){
				UtGen.emitirRM("LDC", UtGen.AC1, da.baseDireccion, 0, "asig arr: base global");
				UtGen.emitirRO("ADD", UtGen.AC, UtGen.AC, UtGen.AC1, "asig arr: base+idx");
			} else {
				// local array variable: AC1 = FP+offset
				UtGen.emitirRM("LDA", UtGen.AC1, da.offsetFP, UtGen.FP, "asig arr: base local");
				UtGen.emitirRO("ADD", UtGen.AC, UtGen.AC, UtGen.AC1, "asig arr: base+idx");
			}
			// Cargar valor y almacenar en la dirección calculada
			UtGen.emitirRM("LD", UtGen.AC1, ++desplazamientoTmp, UtGen.MP, "asignacion array: recuperar valor");
			UtGen.emitirRM("ST", UtGen.AC1, 0, UtGen.AC, "asignacion array: almacenar en posicion calculada");
		} else {
			// Asignación normal: var = valor
			AccesoVar av = resolverAccesoVariable(n.getIdentificador());
			if(av.tipo == 0){ // global
				UtGen.emitirRM("ST", UtGen.AC, av.offset, UtGen.GP, "asignacion: global " + n.getIdentificador());
			} else {
				UtGen.emitirRM("ST", UtGen.AC, av.offset, UtGen.FP, "asignacion: local/param " + n.getIdentificador());
			}
		}
		
		if(UtGen.debug)	UtGen.emitirComentario("<- asignacion");
	}
	
	private static void generarLeer(NodoBase nodo){
		NodoLeer n = (NodoLeer)nodo;
		int direccion;
		if(UtGen.debug)	UtGen.emitirComentario("-> leer");
		UtGen.emitirRO("IN", UtGen.AC, 0, 0, "leer: lee un valor entero ");
		AccesoVar av = resolverAccesoVariable(n.getIdentificador());
		if(av.tipo == 0){
			UtGen.emitirRM("ST", UtGen.AC, av.offset, UtGen.GP, "leer: global " + n.getIdentificador());
		} else {
			UtGen.emitirRM("ST", UtGen.AC, av.offset, UtGen.FP, "leer: local/param " + n.getIdentificador());
		}
		if(UtGen.debug)	UtGen.emitirComentario("<- leer");
	}
	
	private static void generarEscribir(NodoBase nodo){
		NodoEscribir n = (NodoEscribir)nodo;
		if(UtGen.debug)	UtGen.emitirComentario("-> escribir");
		/* Genero el codigo de la expresion que va a ser escrita en pantalla */
		generar(n.getExpresion());
		/* Ahora genero la salida */
		UtGen.emitirRO("OUT", UtGen.AC, 0, 0, "escribir: genero la salida de la expresion");
		if(UtGen.debug)	UtGen.emitirComentario("<- escribir");
	}
	
	private static void generarValor(NodoBase nodo){
    	NodoValor n = (NodoValor)nodo;
    	if(UtGen.debug)	UtGen.emitirComentario("-> constante");
    	if (n.esReal()) {
    		UtGen.emitirRM("LDC", UtGen.AC, n.getValorReal().intValue(), 0, "cargar constante: "+n.getValor());
    	} else {
    		UtGen.emitirRM("LDC", UtGen.AC, n.getValorEntero(), 0, "cargar constante: "+n.getValor());
    	}
    	if(UtGen.debug)	UtGen.emitirComentario("<- constante");
	}
	
	private static void generarIdentificador(NodoBase nodo){
		NodoIdentificador n = (NodoIdentificador)nodo;
		if(UtGen.debug)	UtGen.emitirComentario("-> identificador");
		
		if(n.getDesplazamiento() != null){
			// Acceso a array: arr[indice]
			generar(n.getDesplazamiento()); // deja indice en AC
			DireccionArray da = calcularBaseArray(n.getNombre());
			if(da.esParametroArray){
				UtGen.emitirRM("LD", UtGen.AC1, da.offsetFP, UtGen.FP, "id arr: cargar base param");
				UtGen.emitirRO("ADD", UtGen.AC, UtGen.AC, UtGen.AC1, "id arr: base+idx");
				UtGen.emitirRM("LD", UtGen.AC, 0, UtGen.AC, "id arr: load elemento");
			} else if (da.esGlobal){
				UtGen.emitirRM("LDC", UtGen.AC1, da.baseDireccion, 0, "id arr: base global");
				UtGen.emitirRO("ADD", UtGen.AC, UtGen.AC, UtGen.AC1, "id arr: base+idx");
				UtGen.emitirRM("LD", UtGen.AC, 0, UtGen.AC, "id arr: load elemento");
			} else {
				UtGen.emitirRM("LDA", UtGen.AC1, da.offsetFP, UtGen.FP, "id arr: base local");
				UtGen.emitirRO("ADD", UtGen.AC, UtGen.AC, UtGen.AC1, "id arr: base+idx");
				UtGen.emitirRM("LD", UtGen.AC, 0, UtGen.AC, "id arr: load elemento");
			}
		} else {
			AccesoVar av = resolverAccesoVariable(n.getNombre());
			if(av.tipo == 0){
				UtGen.emitirRM("LD", UtGen.AC, av.offset, UtGen.GP, "id: cargar global " + n.getNombre());
			} else {
				UtGen.emitirRM("LD", UtGen.AC, av.offset, UtGen.FP, "id: cargar local/param " + n.getNombre());
			}
		}
		
		if(UtGen.debug)	UtGen.emitirComentario("<- identificador");
	}
	
	private static class AccesoVar {
		// tipo 0: global (GP), tipo 1: FP relativo
		int tipo; int offset;
	}
	private static AccesoVar resolverAccesoVariable(String nombre){
		AccesoVar av = new AccesoVar();
		// Si estamos dentro de una función y el nombre es local o parámetro, usar FP
		if(funcionActual != null){
			FunctionLayout fl = layoutsFuncion.get(funcionActual);
			if(fl != null){
				Integer off = fl.offsetLocales.get(nombre);
				if(off != null){ av.tipo = 1; av.offset = off; return av; }
				off = fl.offsetParametros.get(nombre);
				if(off != null){ av.tipo = 1; av.offset = off; return av; }
			}
		}
		av.tipo = 0; av.offset = tablaSimbolos.getDireccion(nombre);
		return av;
	}
	
	private static class DireccionArray {
		boolean esGlobal;
		boolean esParametroArray;
		int baseDireccion; // para globales
		int offsetFP;      // para locales/param
	}
	private static DireccionArray calcularBaseArray(String nombre){
		DireccionArray da = new DireccionArray();
		if(funcionActual != null){
			FunctionLayout fl = layoutsFuncion.get(funcionActual);
			if(fl != null){
				if(fl.parametrosArray.contains(nombre)){
					da.esParametroArray = true; da.esGlobal = false; da.offsetFP = fl.offsetParametros.get(nombre);
					return da;
				}
				Integer offLoc = fl.offsetLocales.get(nombre);
				if(offLoc != null){ da.esGlobal = false; da.offsetFP = offLoc; return da; }
			}
		}
		// Global
		da.esGlobal = true; da.baseDireccion = tablaSimbolos.getDireccion(nombre);
		return da;
	}
	
	private static void generarOperacion(NodoBase nodo){
		NodoOperacion n = (NodoOperacion)nodo;
		if(UtGen.debug) UtGen.emitirComentario("-> Operacion: " + n.getOperacion());
		if(n.getOpIzquierdo() != null){
			generar(n.getOpIzquierdo());
			/* Almaceno en la pseudo pila de valor temporales el valor de la operacion izquierda */
			UtGen.emitirRM("ST", UtGen.AC, desplazamientoTmp--, UtGen.MP, "op: push en la pila tmp el resultado expresion izquierda");
		}
		
		/* Genero la expresion derecha de la operacion */
		generar(n.getOpDerecho());
		
		/* Ahora cargo/saco de la pila el valor izquierdo */
		if(n.getOpIzquierdo() != null){
			UtGen.emitirRM("LD", UtGen.AC1, ++desplazamientoTmp, UtGen.MP, "op: pop o cargo de la pila el valor izquierdo en AC1");
		}
		
		switch(n.getOperacion()){
			case mas: UtGen.emitirRO("ADD", UtGen.AC, UtGen.AC1, UtGen.AC, "op: +"); break;
			case menos: UtGen.emitirRO("SUB", UtGen.AC, UtGen.AC1, UtGen.AC, "op: -"); break;
			case por: UtGen.emitirRO("MUL", UtGen.AC, UtGen.AC1, UtGen.AC, "op: *"); break;
			case entre: UtGen.emitirRO("DIV", UtGen.AC, UtGen.AC1, UtGen.AC, "op: /"); break;
			case modulo:
				UtGen.emitirRM("ST", UtGen.AC, desplazamientoTmp--, UtGen.MP, "mod: guardar b");
				UtGen.emitirRM("ST", UtGen.AC1, desplazamientoTmp--, UtGen.MP, "mod: guardar a");
				UtGen.emitirRO("DIV", UtGen.AC, UtGen.AC1, UtGen.AC, "mod: a/b");
				UtGen.emitirRM("LD", UtGen.AC1, ++desplazamientoTmp, UtGen.MP, "mod: recuperar a");
				UtGen.emitirRM("LD", 2, ++desplazamientoTmp, UtGen.MP, "mod: recuperar b en r2");
				UtGen.emitirRO("MUL", UtGen.AC, UtGen.AC, 2, "mod: (a/b)*b");
				UtGen.emitirRO("SUB", UtGen.AC, UtGen.AC1, UtGen.AC, "mod: a - (a/b)*b");
				break;
			case potencia: {
				UtGen.emitirRM("ST", UtGen.AC, desplazamientoTmp--, UtGen.MP, "pow: guardar exp");
				UtGen.emitirRM("ST", UtGen.AC1, desplazamientoTmp--, UtGen.MP, "pow: guardar base");
				UtGen.emitirRM("LDC", UtGen.AC, 1, 0, "pow: inicializar resultado = 1");
				UtGen.emitirRM("ST", UtGen.AC, desplazamientoTmp--, UtGen.MP, "pow: guardar res");
				int posRes = desplazamientoTmp + 1;
				int posBase = desplazamientoTmp + 2;
				int posExp = desplazamientoTmp + 3;
				int loopStart = UtGen.emitirSalto(0);
				UtGen.emitirComentario("pow: inicio bucle");
				UtGen.emitirRM("LD", UtGen.AC, posExp, UtGen.MP, "pow: cargar exp");
				int jmpEnd = UtGen.emitirSalto(1);
				UtGen.emitirComentario("pow: salto condicional a fin (exp==0)");
				// res = res * base
				UtGen.emitirRM("LD", UtGen.AC, posRes, UtGen.MP, "pow: cargar res");
				UtGen.emitirRM("LD", UtGen.AC1, posBase, UtGen.MP, "pow: cargar base");
				UtGen.emitirRO("MUL", UtGen.AC, UtGen.AC1, UtGen.AC, "pow: res = res * base");
				UtGen.emitirRM("ST", UtGen.AC, posRes, UtGen.MP, "pow: guardar res");
				// exp = exp - 1
				UtGen.emitirRM("LD", UtGen.AC1, posExp, UtGen.MP, "pow: cargar exp en AC1");
				UtGen.emitirRM("LDC", UtGen.AC, 1, 0, "pow: cargar 1");
				UtGen.emitirRO("SUB", UtGen.AC, UtGen.AC1, UtGen.AC, "pow: exp - 1");
				UtGen.emitirRM("ST", UtGen.AC, posExp, UtGen.MP, "pow: guardar exp");
				UtGen.emitirRM_Abs("LDA", UtGen.PC, loopStart, "pow: repetir");
				int loopEnd = UtGen.emitirSalto(0);
				UtGen.cargarRespaldo(jmpEnd);
				UtGen.emitirRM_Abs("JEQ", UtGen.AC, loopEnd, "pow: salir si exp == 0");
				UtGen.restaurarRespaldo();
				// resultado final en AC
				UtGen.emitirRM("LD", UtGen.AC, posRes, UtGen.MP, "pow: cargar resultado");
				// limpiar pila temporal (res, base, exp)
				UtGen.emitirRM("LD", UtGen.AC1, ++desplazamientoTmp, UtGen.MP, "pow: pop res");
				UtGen.emitirRM("LD", UtGen.AC1, ++desplazamientoTmp, UtGen.MP, "pow: pop base");
				UtGen.emitirRM("LD", UtGen.AC1, ++desplazamientoTmp, UtGen.MP, "pow: pop exp");
				break;
			}
			case menor: UtGen.emitirRO("SUB", UtGen.AC, UtGen.AC1, UtGen.AC, "op: <");
						UtGen.emitirRM("JLT", UtGen.AC, 2, UtGen.PC, "es verdadero (AC<0)");
						UtGen.emitirRM("LDC", UtGen.AC, 0, 0, "falso");
						UtGen.emitirRM("LDA", UtGen.PC, 1, UtGen.PC, "salto a fin");
						UtGen.emitirRM("LDC", UtGen.AC, 1, 0, "verdadero");
						break;
			case mayor: UtGen.emitirRO("SUB", UtGen.AC, UtGen.AC1, UtGen.AC, "op: >");
						UtGen.emitirRM("JGT", UtGen.AC, 2, UtGen.PC, "es verdadero (AC>0)");
						UtGen.emitirRM("LDC", UtGen.AC, 0, 0, "falso");
						UtGen.emitirRM("LDA", UtGen.PC, 1, UtGen.PC, "salto a fin");
						UtGen.emitirRM("LDC", UtGen.AC, 1, 0, "verdadero");
						break;
			case menorigual: UtGen.emitirRO("SUB", UtGen.AC, UtGen.AC1, UtGen.AC, "op: <=");
							  UtGen.emitirRM("JLE", UtGen.AC, 2, UtGen.PC, "es verdadero (AC<=0)");
							  UtGen.emitirRM("LDC", UtGen.AC, 0, 0, "falso");
							  UtGen.emitirRM("LDA", UtGen.PC, 1, UtGen.PC, "salto a fin");
							  UtGen.emitirRM("LDC", UtGen.AC, 1, 0, "verdadero");
							  break;
			case mayorigual: UtGen.emitirRO("SUB", UtGen.AC, UtGen.AC1, UtGen.AC, "op: >=");
							   UtGen.emitirRM("JGE", UtGen.AC, 2, UtGen.PC, "es verdadero (AC>=0)");
							   UtGen.emitirRM("LDC", UtGen.AC, 0, 0, "falso");
							   UtGen.emitirRM("LDA", UtGen.PC, 1, UtGen.PC, "salto a fin");
							   UtGen.emitirRM("LDC", UtGen.AC, 1, 0, "verdadero");
							   break;
			case igual: UtGen.emitirRO("SUB", UtGen.AC, UtGen.AC1, UtGen.AC, "op: ==");
						UtGen.emitirRM("JEQ", UtGen.AC, 2, UtGen.PC, "voy dos instrucciones mas alla if verdadero (AC==0)");
						UtGen.emitirRM("LDC", UtGen.AC, 0, UtGen.AC, "caso de falso (AC=0)");
						UtGen.emitirRM("LDA", UtGen.PC, 1, UtGen.PC, "Salto incodicional a direccion: PC+1 (es falso evito colocarlo verdadero)");
						UtGen.emitirRM("LDC", UtGen.AC, 1, UtGen.AC, "caso de verdadero (AC=1)");
						break;
			case diferente: UtGen.emitirRO("SUB", UtGen.AC, UtGen.AC1, UtGen.AC, "op: !=");
						  UtGen.emitirRM("JNE", UtGen.AC, 2, UtGen.PC, "saltar si AC!=0");
						  UtGen.emitirRM("LDC", UtGen.AC, 0, 0, "caso falso");
						  UtGen.emitirRM("LDA", UtGen.PC, 1, UtGen.PC, "saltar caso verdadero");
						  UtGen.emitirRM("LDC", UtGen.AC, 1, 0, "caso verdadero");
						  break;
			case and:
				UtGen.emitirRM("JEQ", UtGen.AC1, 3, UtGen.PC, "and: si izquierdo es falso, resultado es falso");
				UtGen.emitirRM("JEQ", UtGen.AC, 2, UtGen.PC, "and: si derecho es falso, resultado es falso");
				UtGen.emitirRM("LDC", UtGen.AC, 1, 0, "and: ambos verdaderos");
				UtGen.emitirRM("LDA", UtGen.PC, 1, UtGen.PC, "and: saltar caso falso");
				UtGen.emitirRM("LDC", UtGen.AC, 0, 0, "and: resultado falso");
				break;
			case or:
				UtGen.emitirRM("JNE", UtGen.AC1, 3, UtGen.PC, "or: si izquierdo es verdadero, resultado es verdadero");
				UtGen.emitirRM("JNE", UtGen.AC, 2, UtGen.PC, "or: si derecho es verdadero, resultado es verdadero");
				UtGen.emitirRM("LDC", UtGen.AC, 0, 0, "or: ambos falsos");
				UtGen.emitirRM("LDA", UtGen.PC, 1, UtGen.PC, "or: saltar caso verdadero");
				UtGen.emitirRM("LDC", UtGen.AC, 1, 0, "or: resultado verdadero");
				break;
			default:
				UtGen.emitirComentario("BUG: tipo de operacion desconocida: " + n.getOperacion());
		}
		if(UtGen.debug)	UtGen.emitirComentario("<- Operacion: " + n.getOperacion());
	}
	
	private static void generarPreludioEstandar(){
		UtGen.emitirComentario("* Compilacion TINY para la maquina TM");
		UtGen.emitirComentario("* Prefacio estandar");
		// Inicializar punteros
		UtGen.emitirRM("LDC", UtGen.GP, 0, 0, "init: GP = 0");
		UtGen.emitirRM("LDC", UtGen.MP, 1023, 0, "init: MP = 1023 (tope de memoria temporales)");
		// Reservar zona de pila de activacion (separada de MP)
		UtGen.emitirRM("LDC", UtGen.SP, 512, 0, "init: SP = 512 (pila activacion)");
		UtGen.emitirRM("LDA", UtGen.FP, 0, UtGen.SP, "init: FP = SP");
		UtGen.emitirComentario("* Fin del prefacio estandar");
	}
}