package co.gov.sfc.infdinamicos.service;

public class GrupoClave {
    public String ordencat;
    public String ordengru;
    public String nombreGrupo;

    public GrupoClave(String ordencat, String ordengru, String nombreGrupo) {
        this.ordencat = ordencat;
        this.ordengru = ordengru;
        this.nombreGrupo = nombreGrupo;
    }

    @Override
    public String toString() {
        return nombreGrupo; // para mostrar en el resultado
    }
}
