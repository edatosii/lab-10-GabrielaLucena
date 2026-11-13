package co.umariana.dao;

import co.umariana.conexion.ConexionBD;
import co.umariana.modelo.ArbolGenealogico;
import co.umariana.modelo.Persona;
import co.umariana.modelo.ResumenArbol;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO (Data Access Object): la única clase que habla con la base de datos. Guarda los árboles genealógicos
 * en las tablas "arboles" y "miembros" (ver db/schema.sql).
 *
 * REGLAS
 *  - Los métodos NO lanzan SQLException: la capturan, imprimen el mensaje con System.out.println y devuelven
 *    un valor "de error" (-1, false, lista vacía o null).
 *  - Siempre PreparedStatement con parámetros (?).
 *  - Guardar y actualizar usan una TRANSACCIÓN (commit / rollback) y dejan setAutoCommit(true) al final.
 *  - Los miembros se guardan en el orden de arbol.miembros() y se leen ORDER BY id.
 */
public class ArbolDAO {

    private static final int MAX_NOMBRE_ARBOL = 100;

    private static final String SQL_INSERT_ARBOL = "INSERT INTO arboles (nombre) VALUES (?)";
    private static final String SQL_INSERT_MIEMBRO =
            "INSERT INTO miembros (arbol_id, posicion, nombre, nacimiento, fallecimiento) VALUES (?, ?, ?, ?, ?)";

    // =====================================================================
    // ETAPA 4.1. Guardar un árbol nuevo
    // =====================================================================

    /**
     * Guarda un árbol nuevo: una fila en "arboles" (con el nombre) y una fila en "miembros" por cada persona.
     *
     * @return el id que la base de datos le dio al árbol; -1 si el nombre es nulo, está en blanco o tiene más
     *         de 100 caracteres, si el árbol es null, o si hubo un error (en ese caso NO queda nada guardado)
     */
    public long guardar(String nombre, ArbolGenealogico arbol) {
        if (arbol == null || nombre == null || nombre.isBlank() || nombre.length() > MAX_NOMBRE_ARBOL) {
            return -1;
        }
        try (Connection con = ConexionBD.conectar()) {
            try {
                con.setAutoCommit(false);

                long id;
                try (PreparedStatement ps = con.prepareStatement(SQL_INSERT_ARBOL, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, nombre);
                    ps.executeUpdate();
                    try (ResultSet claves = ps.getGeneratedKeys()) {
                        if (!claves.next()) {
                            throw new SQLException("La base de datos no devolvió el id del árbol.");
                        }
                        id = claves.getLong(1);
                    }
                }

                insertarMiembros(con, id, arbol);
                con.commit();
                return id;
            } catch (SQLException e) {
                deshacer(con);
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.out.println("Error al guardar el árbol: " + e.getMessage());
            return -1;
        }
    }

    // =====================================================================
    // ETAPA 4.2. Listar
    // =====================================================================

    /**
     * @return un resumen por cada árbol guardado (id, nombre, cuántos miembros tiene y cuándo se creó),
     *         ordenados por id; lista vacía si no hay árboles o si hay un error
     */
    public List<ResumenArbol> listar() {
        String sql = "SELECT a.id, a.nombre, a.creado, COUNT(m.id) AS total "
                + "FROM arboles a LEFT JOIN miembros m ON m.arbol_id = a.id "
                + "GROUP BY a.id, a.nombre, a.creado ORDER BY a.id";
        List<ResumenArbol> resumenes = new ArrayList<>();
        try (Connection con = ConexionBD.conectar();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Timestamp creado = rs.getTimestamp("creado");
                LocalDateTime fecha = creado == null ? null : creado.toLocalDateTime();
                // SUPUESTO: constructor ResumenArbol(long id, String nombre, int totalMiembros, LocalDateTime creado)
                resumenes.add(new ResumenArbol(rs.getLong("id"), rs.getString("nombre"),
                        rs.getInt("total"), fecha));
            }
            return resumenes;
        } catch (SQLException e) {
            System.out.println("Error al listar los árboles: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // =====================================================================
    // ETAPA 4.3. Cargar un árbol
    // =====================================================================

    /**
     * @return el árbol con ese id, reconstruido con todos sus miembros; null si no existe ese id o si hay un error
     */
    public ArbolGenealogico cargar(long id) {
        String sql = "SELECT posicion, nombre, nacimiento, fallecimiento FROM miembros "
                + "WHERE arbol_id = ? ORDER BY id";
        try (Connection con = ConexionBD.conectar();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                ArbolGenealogico arbol = null;
                while (rs.next()) {
                    int fallecido = rs.getInt("fallecimiento");
                    Integer fallecimiento = rs.wasNull() ? null : fallecido;
                    Persona persona = new Persona(rs.getString("posicion"), rs.getString("nombre"),
                            rs.getInt("nacimiento"), fallecimiento);
                    if (arbol == null) {
                        arbol = new ArbolGenealogico(persona); // la primera fila es el sujeto
                    } else {
                        arbol.agregar(persona);
                    }
                }
                return arbol; // null si no había filas
            }
        } catch (SQLException e) {
            System.out.println("Error al cargar el árbol: " + e.getMessage());
            return null;
        } catch (RuntimeException e) {
            // Datos guardados que no forman un árbol válido (IllegalArgumentException / ArbolInvalidoException).
            System.out.println("Error al reconstruir el árbol: " + e.getMessage());
            return null;
        }
    }

    // =====================================================================
    // ETAPA 4.4. Actualizar
    // =====================================================================

    /**
     * Deja en la base de datos exactamente los miembros de {@code arbol}. El nombre del árbol NO cambia.
     *
     * @return true si se actualizó; false si no existe un árbol con ese id, si el árbol es null o si hubo un
     *         error (en ese caso NO se modifica nada)
     */
    public boolean actualizar(long id, ArbolGenealogico arbol) {
        if (arbol == null) {
            return false;
        }
        try (Connection con = ConexionBD.conectar()) {
            try {
                con.setAutoCommit(false);

                if (!existeArbol(con, id)) {
                    con.rollback();
                    return false;
                }
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM miembros WHERE arbol_id = ?")) {
                    ps.setLong(1, id);
                    ps.executeUpdate();
                }
                insertarMiembros(con, id, arbol);
                con.commit();
                return true;
            } catch (SQLException e) {
                deshacer(con);
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.out.println("Error al actualizar el árbol: " + e.getMessage());
            return false;
        }
    }

    // =====================================================================
    // ETAPA 4.5. Eliminar
    // =====================================================================

    /**
     * Elimina el árbol y todos sus miembros (la base de datos borra los miembros sola: ON DELETE CASCADE).
     *
     * @return true si se eliminó; false si no existía ese id o si hubo un error
     */
    public boolean eliminar(long id) {
        try (Connection con = ConexionBD.conectar();
             PreparedStatement ps = con.prepareStatement("DELETE FROM arboles WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("Error al eliminar el árbol: " + e.getMessage());
            return false;
        }
    }

    // =====================================================================
    // Auxiliares
    // =====================================================================

    /** Inserta todos los miembros del árbol, en el orden de arbol.miembros(). */
    private void insertarMiembros(Connection con, long arbolId, ArbolGenealogico arbol) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(SQL_INSERT_MIEMBRO)) {
            for (Persona p : arbol.miembros()) {
                ps.setLong(1, arbolId);
                ps.setString(2, p.getPosicion());
                ps.setString(3, p.getNombre());
                ps.setInt(4, p.getNacimiento());
                if (p.getFallecimiento() == null) {
                    ps.setNull(5, java.sql.Types.INTEGER);
                } else {
                    ps.setInt(5, p.getFallecimiento());
                }
                ps.executeUpdate();
            }
        }
    }

    private boolean existeArbol(Connection con, long id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT id FROM arboles WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Deshace la transacción; si el propio rollback falla, solo lo informa. */
    private void deshacer(Connection con) {
        try {
            con.rollback();
        } catch (SQLException e) {
            System.out.println("Error al deshacer la transacción: " + e.getMessage());
        }
    }
}
