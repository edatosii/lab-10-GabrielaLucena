package co.umariana.construccion;

import co.umariana.modelo.ArbolGenealogico;
import co.umariana.modelo.ArbolInvalidoException;
import co.umariana.modelo.Persona;
import co.umariana.modelo.Posicion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Construye un árbol genealógico a partir de un texto: cada línea es una persona.
 *
 *     posicion;nombre;nacimiento;fallecimiento
 *
 * Ejemplo (el fallecimiento puede quedar vacío):
 *
 *     1;John Doe;2002;
 *     2;Henry Doe;1920;1990
 *     3;Maria Doe;1921;
 *     1.1;Timmy Doe;2021;
 *     1.2;Amy Doe;2026;
 *
 * REGLAS
 *   1. Si el texto es null o no tiene ninguna línea con contenido: ArbolInvalidoException ("El texto está vacío...").
 *   2. Las líneas pueden terminar en \n o en \r\n. Las líneas en blanco se ignoran, pero los números de línea
 *      que se informan cuentan TODAS las líneas del texto (la primera es la línea 1).
 *   3. Cada línea tiene 4 campos separados por ';' (el cuarto puede estar vacío). Se acepta también la línea
 *      con solo 3 campos. Los campos se recortan (trim). Menos de 3 o más de 4 campos: error.
 *   4. El nacimiento y el fallecimiento deben ser números enteros (fallecimiento vacío = "no tiene").
 *   5. Cada persona debe ser válida (Persona.validar) y no puede haber dos líneas con la misma posición.
 *   6. Debe existir la persona "1" (el sujeto), pero puede estar en cualquier línea.
 *   7. Las líneas pueden venir en CUALQUIER orden: se agregan al árbol en un orden en el que cada persona ya
 *      tenga a la que necesita. Las reglas del árbol las valida ArbolGenealogico.
 *   8. Los errores asociados a una línea empiezan con "Línea N: ". Los que no son de una línea (texto vacío,
 *      falta el sujeto) no llevan ese prefijo.
 *   9. Solo se muestran los miembros que están en el texto.
 */
public final class ConstructorArbol {

    /** Una persona leída del texto, junto con el número de línea de donde salió. */
    private static final class Entrada {
        final int linea;
        final Persona persona;

        Entrada(int linea, Persona persona) {
            this.linea = linea;
            this.persona = persona;
        }
    }

    private ConstructorArbol() {
    }

    /**
     * Convierte el texto en un árbol (ver las reglas arriba).
     *
     * @throws ArbolInvalidoException si el texto no cumple las reglas
     */
    public static ArbolGenealogico construir(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new ArbolInvalidoException("El texto está vacío: no hay ninguna persona.");
        }

        // 1. Leer y validar cada línea.
        String[] lineas = texto.split("\r?\n", -1);
        List<Entrada> entradas = new ArrayList<>();
        for (int i = 0; i < lineas.length; i++) {
            if (lineas[i].isBlank()) {
                continue;
            }
            int numero = i + 1;
            Persona persona = leerLinea(lineas[i], numero);

            for (Entrada previa : entradas) {
                if (previa.persona.getPosicion().equals(persona.getPosicion())) {
                    throw error(numero, "la posición " + persona.getPosicion() + " está repetida.");
                }
            }
            entradas.add(new Entrada(numero, persona));
        }

        if (entradas.isEmpty()) {
            throw new ArbolInvalidoException("El texto está vacío: no hay ninguna persona.");
        }

        // 2. Separar el sujeto, los ascendientes y los descendientes.
        Entrada sujeto = null;
        List<Entrada> ascendientes = new ArrayList<>();
        List<Entrada> descendientes = new ArrayList<>();
        for (Entrada e : entradas) {
            String pos = e.persona.getPosicion();
            if (Posicion.esSujeto(pos)) {
                sujeto = e;
            } else if (Posicion.esAscendiente(pos)) {
                ascendientes.add(e);
            } else {
                descendientes.add(e);
            }
        }
        if (sujeto == null) {
            throw new ArbolInvalidoException("Falta la persona 1 (el sujeto).");
        }

        // 3. Ordenar: ascendientes por número Ahnentafel; descendientes por generación y luego por posición.
        ascendientes.sort(Comparator.comparingInt(e -> Integer.parseInt(e.persona.getPosicion())));
        descendientes.sort(Comparator
                .comparingInt((Entrada e) -> Posicion.generacion(e.persona.getPosicion()))
                .thenComparing((a, b) -> compararDAboville(a.persona.getPosicion(), b.persona.getPosicion())));

        // 4. Armar el árbol: primero el sujeto, y luego cada persona cuando ya existe de quien depende.
        ArbolGenealogico arbol;
        try {
            arbol = new ArbolGenealogico(sujeto.persona);
        } catch (IllegalArgumentException e) { // incluye ArbolInvalidoException
            throw error(sujeto.linea, e.getMessage());
        }
        agregarTodos(arbol, ascendientes);
        agregarTodos(arbol, descendientes);
        return arbol;
    }

    private static void agregarTodos(ArbolGenealogico arbol, List<Entrada> entradas) {
        for (Entrada e : entradas) {
            try {
                arbol.agregar(e.persona);
            } catch (IllegalArgumentException ex) { // incluye ArbolInvalidoException
                throw error(e.linea, ex.getMessage());
            }
        }
    }

    /** Convierte una línea "posicion;nombre;nacimiento;fallecimiento" en una Persona válida. */
    private static Persona leerLinea(String linea, int numero) {
        String[] campos = linea.split(";", -1);
        if (campos.length < 3 || campos.length > 4) {
            throw error(numero, "se esperaban 3 o 4 campos separados por ';' y hay " + campos.length + ".");
        }

        String posicion = campos[0].trim();
        String nombre = campos[1].trim();
        int nacimiento = entero(campos[2], "nacimiento", numero);
        Integer fallecimiento = null;
        if (campos.length == 4 && !campos[3].isBlank()) {
            fallecimiento = entero(campos[3], "fallecimiento", numero);
        }

        Persona persona = new Persona(posicion, nombre, nacimiento, fallecimiento);
        try {
            persona.validar();
        } catch (IllegalArgumentException e) {
            throw error(numero, e.getMessage());
        }
        return persona;
    }

    private static int entero(String valor, String campo, int linea) {
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            throw error(linea, "el " + campo + " debe ser un número entero.");
        }
    }

    /** Compara dos posiciones d'Aboville número a número (1.2 < 1.10). */
    private static int compararDAboville(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        for (int i = 0; i < Math.min(pa.length, pb.length); i++) {
            int c = Integer.compare(Integer.parseInt(pa[i]), Integer.parseInt(pb[i]));
            if (c != 0) {
                return c;
            }
        }
        return Integer.compare(pa.length, pb.length);
    }

    private static ArbolInvalidoException error(int linea, String mensaje) {
        return new ArbolInvalidoException("Línea " + linea + ": " + mensaje);
    }
}