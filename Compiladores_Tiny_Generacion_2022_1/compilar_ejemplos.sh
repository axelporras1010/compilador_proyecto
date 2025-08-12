#!/bin/bash

echo "=== Compilando Todos los Ejemplos Tiny ==="

# Cambiar al directorio del script si no estamos ya ahí
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Crear directorios de salida si no existen
mkdir -p salida
mkdir -p ejemplo_generado
mkdir -p out

# Detectar el separador de classpath según el OS
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" || "$OSTYPE" == "win32" ]]; then
    CLASSPATH_SEP=";"
else
    CLASSPATH_SEP=":"
fi

# Compilar fuentes Java a 'out'
echo "Compilando fuentes Java..."
javac -cp "src${CLASSPATH_SEP}lib/java-cup-11b-runtime.jar" -d out \
  src/ve/edu/unet/nodosAST/*.java src/ve/edu/unet/*.java || { echo "Fallo la compilacion de Java"; exit 1; }

echo "Compilacion Java completa."

# Contador para estadísticas
total_archivos=0
compilados_exitosos=0
compilados_fallidos=0

echo "Compilando archivos .tiny del directorio ejemplo_fuente/..."
echo "================================================"

# Buscar y compilar todos los archivos .tiny
for archivo_tiny in ejemplo_fuente/*.tiny; do
    # Verificar que el archivo existe (en caso de que no haya archivos .tiny)
    if [[ ! -f "$archivo_tiny" ]]; then
        echo "No se encontraron archivos .tiny en ejemplo_fuente/"
        exit 1
    fi
    
    # Obtener el nombre base del archivo sin la extensión
    nombre_base=$(basename "$archivo_tiny" .tiny)
    
    # Archivos de salida
    archivo_log="salida/${nombre_base}_compilacion.txt"
    archivo_tm="ejemplo_generado/${nombre_base}.tm"
    
    echo "Compilando: $archivo_tiny"
    echo "  → Log: $archivo_log"
    echo "  → Código generado: $archivo_tm"
    
    # Compilar el archivo
    java -cp "out${CLASSPATH_SEP}lib/java-cup-11b-runtime.jar" ve.edu.unet.parser "$archivo_tiny" > "$archivo_log" 2>&1
    
    # Verificar si la compilación fue exitosa
    if [[ $? -eq 0 ]]; then
        # Verificar si se generó el archivo .tm
        if [[ -f "$archivo_tm" ]]; then
            echo "  ✓ Compilación exitosa"
            ((compilados_exitosos++))
        else
            echo "  ⚠ Compilación completada pero no se generó archivo .tm"
            echo "  Ver detalles en: $archivo_log"
        fi
    else
        echo "  ✗ Error en la compilación"
        echo "  Ver detalles en: $archivo_log"
        ((compilados_fallidos++))
    fi
    
    ((total_archivos++))
    echo ""
done

echo "================================================"
echo "=== Resumen de Compilación ==="
echo "Total de archivos procesados: $total_archivos"
echo "Compilaciones exitosas: $compilados_exitosos"
echo "Compilaciones fallidas: $compilados_fallidos"
echo "" 



# Hacer el script ejecutable
chmod +x "$0"