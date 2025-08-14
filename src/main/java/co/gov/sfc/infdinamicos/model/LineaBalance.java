package co.gov.sfc.infdinamicos.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

//Una línea del reporte (tiene 1..n cuentas)
public class LineaBalance {

	private final int orden;
	private final String linea;
	private final List<CuentaBalance> cuentas = new ArrayList<>();

	public LineaBalance(int orden, String linea) {
		this.orden = orden;
		this.linea = linea;
	}

	public int getOrden() {
		return orden;
	}

	public String getLinea() {
		return linea;
	}

	public List<CuentaBalance> getCuentas() {
		return cuentas;
	}

	public boolean esAtomica() {
		return cuentas.size() == 1;
	}

	public void add(CuentaBalance t) {
		cuentas.add(t);
	}
	
    /** Devuelve el primer código del grupo, si existe (del CSV). */
    public Optional<String> getCodigoPrincipal() {
        return cuentas.isEmpty() ? Optional.empty()
                                  : Optional.of(cuentas.get(0).getCodigo());
    }
	
}