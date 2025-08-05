package co.gov.sfc.infdinamicos.model;

import java.util.ArrayList;
import java.util.List;

public class GrupoEstadoResultados {
    private String nombreGrupo; // Ej: "Comisiones Pensiones Obligatorias"
    private List<String> cuentas; // Ej: ["411528", "411530", ...]

    public GrupoEstadoResultados(String nombreGrupo) {
        this.nombreGrupo = nombreGrupo;
        this.cuentas = new ArrayList<>();
    }

    public String getNombreGrupo() {
        return nombreGrupo;
    }

    public List<String> getCuentas() {
        return cuentas;
    }

    public void addCuenta(String cuenta) {
        this.cuentas.add(cuenta);
    }
}
