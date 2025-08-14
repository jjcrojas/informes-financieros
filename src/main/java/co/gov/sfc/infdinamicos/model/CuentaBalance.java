package co.gov.sfc.infdinamicos.model;

//cuenta con signo
public class CuentaBalance {
	private final String codigo;
	private final int signo; // +1 o -1

	public CuentaBalance(String codigo, int signo) {
		this.codigo = codigo;
		this.signo = signo;
	}

	public String getCodigo() {
		return codigo;
	}

	public int getSigno() {
		return signo;
	}
}