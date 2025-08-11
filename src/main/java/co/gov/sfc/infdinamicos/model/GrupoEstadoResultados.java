package co.gov.sfc.infdinamicos.model;

import java.util.ArrayList;
import java.util.List;

public class GrupoEstadoResultados {
    private final String nombreGrupo;
    private final String categoria;
    private final int ordencat;
    private final int ordengru;
    private final List<String> cuentas = new ArrayList<>();

    public GrupoEstadoResultados(String nombreGrupo, String categoria, int ordencat, int ordengru) {
        this.nombreGrupo = nombreGrupo;
        this.categoria = categoria;
        this.ordencat = ordencat;
        this.ordengru = ordengru;
    }

    public String getNombreGrupo() { return nombreGrupo; }
    public String getCategoria()   { return categoria; }
    public int getOrdencat()       { return ordencat; }
    public int getOrdengru()       { return ordengru; }
    public List<String> getCuentas() { return cuentas; }
    public void addCuenta(String c) { cuentas.add(c); }
}
