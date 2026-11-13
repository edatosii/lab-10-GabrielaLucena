package co.umariana.modelo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * El árbol genealógico: un SUJETO (posición "1") con sus ASCENDIENTES (padres, abuelos... en Ahnentafel)
 * y sus DESCENDIENTES (hijos, nietos... en d'Aboville). Ver la clase Posicion.
 *
 * Son en realidad dos árboles unidos en la raíz:
 *
 *     ascendientes:  árbol BINARIO. Cada persona tiene como mucho un padre (2n) y una madre (2n + 1).
 *     descendientes: árbol GENERAL. Cada persona tiene una lista de hijos (1.1, 1.2, ...) ordenada por número.
 *
 * El árbol solo guarda a los miembros que EXISTEN: no hay "huecos" ni personas vacías.
 *
 * REGLAS DEL ÁRBOL
 *   - La posición "1" (el sujeto) existe siempre y no se puede eliminar.
 *   - Una posición no puede repetirse.
 *   - Un ascendiente n solo se puede agregar si ya existe su hijo n / 2, y debe haber NACIDO ANTES
 *     (año de nacimiento menor) que ese hijo.
 *   - Un descendiente solo se puede agregar si ya existe su progenitor, y debe haber NACIDO DESPUÉS
 *     (año de nacimiento mayor) que su progenitor.
 */
public class ArbolGenealogico {

    /** Nodo del árbol: la persona, su padre, su madre y la lista de sus hijos (ordenada por número). */
    private static final class Nodo {
        final Persona persona;
        Nodo padre;
        Nodo madre;
        final List<Nodo> hijos = new ArrayList<>();

        Nodo(Persona persona) {
            this.persona = persona;
        }
    }

    private final Nodo raiz;
    private int cantidad;

    /**
     * Crea un árbol que solo tiene al sujeto.
     *
     * @throws IllegalArgumentException si la persona no es válida (Persona.validar)
     * @throws ArbolInvalidoException si su posición no es "1"
     */
    public ArbolGenealogico(Persona sujeto) {
        if (sujeto == null) {
            throw new ArbolInvalidoException("El sujeto no puede ser nulo.");
        }
        sujeto.validar();
        if (!Posicion.esSujeto(sujeto.getPosicion())) {
            throw new ArbolInvalidoException("El sujeto debe tener la posición 1 y tiene "
                    + sujeto.getPosicion() + ".");
        }
        this.raiz = new Nodo(sujeto);
        this.cantidad = 1;
    }

    /** @return el sujeto (la persona de la posición "1") */
    public Persona sujeto() {
        return raiz.persona;
    }

    /** @return cuántos miembros tiene el árbol (el sujeto cuenta; un árbol nuevo tiene 1) */
    public int tamano() {
        return cantidad;
    }

    /**
     * @return la persona de esa posición, o null si no existe en el árbol o si la posición no es válida
     */
    public Persona buscar(String posicion) {
        Nodo nodo = buscarNodo(posicion);
        return nodo == null ? null : nodo.persona;
    }

    /** @return true si hay alguien en esa posición (false también si la posición no es válida) */
    public boolean contiene(String posicion) {
        return buscarNodo(posicion) != null;
    }

    /**
     * Agrega una persona en la posición que ella misma trae (la usa el constructor del árbol a partir del texto).
     * El orden de llamada importa: primero debe existir la persona de la que depende (ver REGLAS DEL ÁRBOL).
     *
     * @return la misma persona
     * @throws IllegalArgumentException si la persona no es válida (Persona.validar)
     * @throws ArbolInvalidoException si la posición ya existe (o es "1"), si falta la persona de la que depende,
     *         o si los años de nacimiento no cumplen las reglas
     */
    public Persona agregar(Persona persona) {
        if (persona == null) {
            throw new ArbolInvalidoException("La persona no puede ser nula.");
        }
        persona.validar();
        String posicion = persona.getPosicion();

        if (Posicion.esSujeto(posicion) || contiene(posicion)) {
            throw new ArbolInvalidoException("La posición " + posicion + " está repetida.");
        }

        if (Posicion.esAscendiente(posicion)) {
            String posHijo = Posicion.posicionDelHijoEnLinea(posicion);
            Nodo hijo = buscarNodo(posHijo);
            if (hijo == null) {
                throw new ArbolInvalidoException("Falta la posición " + posHijo
                        + ", de la que depende la posición " + posicion + ".");
            }
            if (persona.getNacimiento() >= hijo.persona.getNacimiento()) {
                throw new ArbolInvalidoException("La posición " + posicion
                        + " debe haber nacido antes que la posición " + posHijo + ".");
            }
            Nodo nuevo = new Nodo(persona);
            if (Integer.parseInt(posicion) % 2 == 0) {
                hijo.padre = nuevo;
            } else {
                hijo.madre = nuevo;
            }
        } else {
            // Posicion.validar ya garantizó que es d'Aboville.
            String posProgenitor = Posicion.posicionDelProgenitor(posicion);
            Nodo progenitor = buscarNodo(posProgenitor);
            if (progenitor == null) {
                throw new ArbolInvalidoException("Falta la posición " + posProgenitor
                        + ", de la que depende la posición " + posicion + ".");
            }
            if (persona.getNacimiento() <= progenitor.persona.getNacimiento()) {
                throw new ArbolInvalidoException("La posición " + posicion
                        + " debe haber nacido después que la posición " + posProgenitor + ".");
            }
            insertarHijo(progenitor, new Nodo(persona));
        }

        cantidad++;
        return persona;
    }

    /**
     * Agrega el PADRE de la persona de esa posición, en la posición 2n (ver Posicion.posicionDelPadre).
     * Solo se pueden agregar padres al sujeto y a sus ascendientes.
     *
     * @return la persona creada
     * @throws ArbolInvalidoException si la posición no existe, si no es el sujeto ni un ascendiente, si ese padre
     *         ya existe o si no cumple las reglas del árbol
     * @throws IllegalArgumentException si los datos de la persona no son válidos
     */
    public Persona agregarPadre(String posicion, String nombre, int nacimiento, Integer fallecimiento) {
        Nodo nodo = nodoParaAscendiente(posicion);
        String nueva = Posicion.posicionDelPadre(posicion);
        verificarCabeYLibre(nueva, nodo.padre);
        return agregar(new Persona(nueva, nombre, nacimiento, fallecimiento));
    }

    /**
     * Igual que agregarPadre, pero agrega la MADRE (posición 2n + 1).
     */
    public Persona agregarMadre(String posicion, String nombre, int nacimiento, Integer fallecimiento) {
        Nodo nodo = nodoParaAscendiente(posicion);
        String nueva = Posicion.posicionDeLaMadre(posicion);
        verificarCabeYLibre(nueva, nodo.madre);
        return agregar(new Persona(nueva, nombre, nacimiento, fallecimiento));
    }

    /**
     * Agrega un HIJO a la persona de esa posición. Solo se pueden agregar hijos al sujeto y a sus descendientes.
     * Su posición es la del progenitor + "." + el número siguiente al MAYOR número de hijo que ya exista
     * (o 1 si todavía no tiene hijos). Ej.: si "1" tiene los hijos 1.1 y 1.3, el nuevo es 1.4.
     *
     * @return la persona creada
     * @throws ArbolInvalidoException si la posición no existe, si no es el sujeto ni un descendiente, o si no cumple
     *         las reglas del árbol
     * @throws IllegalArgumentException si los datos de la persona no son válidos
     */
    public Persona agregarHijo(String posicion, String nombre, int nacimiento, Integer fallecimiento) {
        Nodo progenitor = buscarNodo(posicion);
        if (progenitor == null) {
            throw new ArbolInvalidoException("La posición " + posicion + " no existe.");
        }
        if (!Posicion.esSujeto(posicion) && !Posicion.esDescendiente(posicion)) {
            throw new ArbolInvalidoException("Solo se pueden agregar hijos al sujeto y a sus descendientes.");
        }

        int siguiente = 1;
        if (!progenitor.hijos.isEmpty()) {
            Nodo ultimo = progenitor.hijos.get(progenitor.hijos.size() - 1);
            siguiente = Posicion.orden(ultimo.persona.getPosicion()) + 1;
        }
        if (siguiente > Posicion.MAX_ORDEN) {
            throw new ArbolInvalidoException("La posición " + posicion + " ya no admite más hijos.");
        }
        String nueva = posicion + "." + siguiente;
        if (!Posicion.esDAboville(nueva)) {
            throw new ArbolInvalidoException("La posición " + nueva + " es demasiado larga.");
        }
        return agregar(new Persona(nueva, nombre, nacimiento, fallecimiento));
    }

    /**
     * Elimina a la persona de esa posición y TODO lo que depende de ella:
     *   - si es un ascendiente: ella y todos sus propios ascendientes (sus padres, abuelos...);
     *   - si es un descendiente: ella y todos sus propios descendientes (hijos, nietos...).
     * Las demás posiciones NO cambian de número.
     *
     * @return cuántas personas se eliminaron (ella incluida)
     * @throws ArbolInvalidoException si la posición es "1" (el sujeto no se puede eliminar) o si no existe
     */
    public int eliminar(String posicion) {
        if (Posicion.esSujeto(posicion)) {
            throw new ArbolInvalidoException("El sujeto (posición 1) no se puede eliminar.");
        }
        Nodo nodo = buscarNodo(posicion);
        if (nodo == null) {
            throw new ArbolInvalidoException("La posición " + posicion + " no existe.");
        }

        if (Posicion.esAscendiente(posicion)) {
            Nodo hijo = buscarNodo(Posicion.posicionDelHijoEnLinea(posicion));
            if (hijo.padre == nodo) {
                hijo.padre = null;
            } else {
                hijo.madre = null;
            }
        } else {
            Nodo progenitor = buscarNodo(Posicion.posicionDelProgenitor(posicion));
            progenitor.hijos.remove(nodo);
        }

        int eliminados = contar(nodo);
        cantidad -= eliminados;
        return eliminados;
    }

    /**
     * @return todos los miembros en este orden: el sujeto; luego los ascendientes por número Ahnentafel
     *         ascendente (2, 3, 4, 5...); luego los descendientes en PREORDEN (cada persona y después sus hijos:
     *         1.1, 1.1.1, 1.1.2, 1.2, ...). La lista es una copia nueva: modificarla no cambia el árbol.
     */
    public List<Persona> miembros() {
        List<Persona> lista = new ArrayList<>();
        lista.add(raiz.persona);
        lista.addAll(ascendientes());
        lista.addAll(descendientes());
        return lista;
    }

    /** @return solo los ascendientes, por número Ahnentafel ascendente (sin el sujeto) */
    public List<Persona> ascendientes() {
        // Recorrido por niveles, padre antes que madre: da exactamente el orden 2, 3, 4, 5...
        List<Persona> lista = new ArrayList<>();
        Queue<Nodo> cola = new ArrayDeque<>();
        cola.add(raiz);
        while (!cola.isEmpty()) {
            Nodo actual = cola.poll();
            if (actual != raiz) {
                lista.add(actual.persona);
            }
            if (actual.padre != null) {
                cola.add(actual.padre);
            }
            if (actual.madre != null) {
                cola.add(actual.madre);
            }
        }
        return lista;
    }

    /** @return solo los descendientes, en preorden (sin el sujeto) */
    public List<Persona> descendientes() {
        List<Persona> lista = new ArrayList<>();
        for (Nodo hijo : raiz.hijos) {
            preorden(hijo, lista);
        }
        return lista;
    }

    // ------------------------------------------------------------------ auxiliares

    /** Busca el nodo de una posición siguiendo su camino; null si no existe o la posición no es válida. */
    private Nodo buscarNodo(String posicion) {
        if (!Posicion.esValida(posicion)) {
            return null;
        }
        Nodo actual = raiz;
        if (Posicion.esAhnentafel(posicion)) {
            int n = Integer.parseInt(posicion);
            int mayorBit = 31 - Integer.numberOfLeadingZeros(n);
            // Los bits después del primer 1 dicen el camino: 0 = padre, 1 = madre.
            for (int i = mayorBit - 1; i >= 0 && actual != null; i--) {
                actual = ((n >> i) & 1) == 0 ? actual.padre : actual.madre;
            }
            return actual;
        }
        String[] partes = posicion.split("\\.");
        for (int i = 1; i < partes.length && actual != null; i++) {
            actual = hijoConOrden(actual, Integer.parseInt(partes[i]));
        }
        return actual;
    }

    private Nodo hijoConOrden(Nodo progenitor, int orden) {
        for (Nodo hijo : progenitor.hijos) {
            if (Posicion.orden(hijo.persona.getPosicion()) == orden) {
                return hijo;
            }
        }
        return null;
    }

    /** Inserta el hijo en su lugar para que la lista siga ordenada por número de orden. */
    private void insertarHijo(Nodo progenitor, Nodo nuevo) {
        int orden = Posicion.orden(nuevo.persona.getPosicion());
        int indice = 0;
        while (indice < progenitor.hijos.size()
                && Posicion.orden(progenitor.hijos.get(indice).persona.getPosicion()) < orden) {
            indice++;
        }
        progenitor.hijos.add(indice, nuevo);
    }

    /** Verifica que la posición exista y sea el sujeto o un ascendiente (los únicos que tienen padres). */
    private Nodo nodoParaAscendiente(String posicion) {
        Nodo nodo = buscarNodo(posicion);
        if (nodo == null) {
            throw new ArbolInvalidoException("La posición " + posicion + " no existe.");
        }
        if (!Posicion.esAhnentafel(posicion)) {
            throw new ArbolInvalidoException("Solo se pueden agregar padres al sujeto y a sus ascendientes.");
        }
        return nodo;
    }

    private void verificarCabeYLibre(String nueva, Nodo ocupante) {
        if (!Posicion.esAhnentafel(nueva)) {
            throw new ArbolInvalidoException("La posición " + nueva + " supera el límite de generaciones.");
        }
        if (ocupante != null) {
            throw new ArbolInvalidoException("La posición " + nueva + " ya existe.");
        }
    }

    /** Cuenta el nodo y todo lo que cuelga de él (sus ascendientes o sus descendientes). */
    private int contar(Nodo nodo) {
        if (nodo == null) {
            return 0;
        }
        int total = 1 + contar(nodo.padre) + contar(nodo.madre);
        for (Nodo hijo : nodo.hijos) {
            total += contar(hijo);
        }
        return total;
    }

    private void preorden(Nodo nodo, List<Persona> destino) {
        destino.add(nodo.persona);
        for (Nodo hijo : nodo.hijos) {
            preorden(hijo, destino);
        }
    }
}