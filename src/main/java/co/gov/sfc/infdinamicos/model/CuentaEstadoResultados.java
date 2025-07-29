package co.gov.sfc.infdinamicos.model;

public class CuentaEstadoResultados {
    private int ordenCategoria;
    private String categoria;
    private int ordenGrupo;
    private String grupo;
    private String signo;
    private String cuenta;
    private String nombreCuenta;

    public CuentaEstadoResultados(int ordenCategoria, String categoria, int ordenGrupo, String grupo, String signo, String cuenta, String nombreCuenta) {
        this.ordenCategoria = ordenCategoria;
        this.categoria = categoria;
        this.ordenGrupo = ordenGrupo;
        this.grupo = grupo;
        this.signo = signo;
        this.cuenta = cuenta;
        this.nombreCuenta = nombreCuenta;
    }

    public int getOrdenCategoria() { return ordenCategoria; }
    public String getCategoria() { return categoria; }
    public int getOrdenGrupo() { return ordenGrupo; }
    public String getGrupo() { return grupo; }
    public String getSigno() { return signo; }
    public String getCuenta() { return cuenta; }
    public String getNombreCuenta() { return nombreCuenta; }
}

