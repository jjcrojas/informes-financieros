package co.gov.sfc.infdinamicos.service;

import co.gov.sfc.infdinamicos.model.CuentaBalance;
import co.gov.sfc.infdinamicos.model.GrupoEstadoResultados;
import co.gov.sfc.infdinamicos.model.LineaBalance;

import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ServicioCuentasDesdeArchivo {

	/**
	 * Lee un archivo CSV desde resources y devuelve un Map con: - key = grupo -
	 * value = lista de códigos de cuenta
	 *
	 * Sirve tanto para Estado de Resultados como para Balance, etc.
	 */

	public Map<String, GrupoEstadoResultados> armarGruposDeCuentas(String nombreArchivo, boolean tieneEncabezado,
			String separador) {

		Map<String, GrupoEstadoResultados> mapa = new TreeMap<>((a, b) -> {
			String[] pa = a.split("-");
			String[] pb = b.split("-");
			int ca = Integer.parseInt(pa[0]);
			int cb = Integer.parseInt(pb[0]);
			if (ca != cb)
				return Integer.compare(ca, cb);
			return Integer.compare(Integer.parseInt(pa[1]), Integer.parseInt(pb[1]));
		});

		try (BufferedReader br = new BufferedReader(new FileReader("src/main/resources/" + nombreArchivo))) {
			String linea;
			if (tieneEncabezado)
				br.readLine();

			while ((linea = br.readLine()) != null) {
				String[] cols = linea.split(separador, -1);

				int ordencat = Integer.parseInt(cols[0].trim());
				String nombreCategoria = cols[1].trim().toLowerCase(); // <-- normalizamos
				int ordengru = Integer.parseInt(cols[2].trim());
				String nombreGrupo = cols[3].trim();
				String cuenta = cols[5].trim();

				String clave = ordencat + "-" + ordengru;
				mapa.putIfAbsent(clave, new GrupoEstadoResultados(nombreGrupo, nombreCategoria, ordencat, ordengru));
				mapa.get(clave).addCuenta(cuenta);
			}
		} catch (IOException e) {
			throw new RuntimeException("Error leyendo archivo: " + nombreArchivo, e);
		}

		return mapa;
	}

	public List<LineaBalance> leerLineasBalanceDesdeCSV(String nombreArchivo, boolean tieneEncabezado,
			String separador) {
		Map<Integer, LineaBalance> mapa = new LinkedHashMap<>();

		String ruta = "src/main/resources/" + nombreArchivo;

		try (BufferedReader br = new BufferedReader(new FileReader(ruta))) {
			String linea;
			if (tieneEncabezado)
				br.readLine();
			
			while ((linea = br.readLine()) != null) {
				if (linea.isBlank())
					continue;
				String[] c = linea.split(Pattern.quote(separador), -1);

				int orden = Integer.parseInt(c[0].trim());
				String nombreLinea = c[1].trim();
				String sgn = c[2].trim();
				String codigo = c[3].trim();

				int signo = ("-".equals(sgn) || "-1".equals(sgn)) ? -1 : 1;

				mapa.computeIfAbsent(orden, k -> new LineaBalance(orden, nombreLinea))
						.add(new CuentaBalance(codigo, signo));
			}
		} catch (IOException e) {
			throw new RuntimeException("Error leyendo CSV: " + ruta, e);
		}

		return mapa.values().stream().sorted(Comparator.comparingInt(LineaBalance::getOrden))
				.collect(Collectors.toList());
	}

	public Map<GrupoClave, List<String>> leerCodigosDesdeCSVConOrdenYNombre(String archivo, boolean tieneCabecera,
			String separador) {

		Map<GrupoClave, List<String>> resultado = new LinkedHashMap<>();

		try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(archivo)) {
			if (inputStream == null) {
				throw new FileNotFoundException("No se encontró el archivo en resources: " + archivo);
			}

			try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
				String linea;
				boolean primera = true;

				while ((linea = br.readLine()) != null) {
					if (primera && tieneCabecera) {
						primera = false;
						continue;
					}

					String[] partes = linea.split(separador);

					String ordencat = partes[0].trim();
					String ordengru = partes[1].trim();
					String nombreGrupo = partes[2].trim();
					String cuenta = partes[5].trim(); // Ajusta índice si es diferente

					GrupoClave clave = new GrupoClave(ordencat, ordengru, nombreGrupo);

					resultado.computeIfAbsent(clave, k -> new ArrayList<>()).add(cuenta);
				}
			}
		} catch (IOException e) {
			throw new RuntimeException("Error leyendo archivo: " + archivo, e);
		}

		return resultado;
	}

	public Map<String, List<String>> leerCodigosDesdeCSVConGrupo(String nombreArchivo, boolean tieneEncabezado,
			String separador) {
		Map<String, List<String>> grupos = new LinkedHashMap<>();

		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(getClass().getClassLoader().getResourceAsStream(nombreArchivo)))) {

			if (tieneEncabezado) {
				br.readLine(); // Saltar encabezado
			}

			String linea;
			while ((linea = br.readLine()) != null) {
				String[] partes = linea.split(separador);
				if (partes.length >= 6) { // formato ESTADO_RESULTADOS
					String grupo = partes[3].trim();
					String cuenta = partes[5].trim();

					if (!cuenta.isEmpty()) {
						grupos.computeIfAbsent(grupo, k -> new ArrayList<>()).add(cuenta);
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return grupos;
	}

	public <T> List<T> leerCuentasDesdeCSV(String nombreArchivo, boolean tieneEncabezado, String separador,
			Class<T> tipo) {
		List<T> resultado = new ArrayList<>();

		try (BufferedReader br = new BufferedReader(
				new InputStreamReader(getClass().getClassLoader().getResourceAsStream(nombreArchivo)))) {

			if (tieneEncabezado)
				br.readLine(); // Saltar encabezado

			String linea;
			while ((linea = br.readLine()) != null) {
				String[] partes = linea.split(separador);

//	            if (tipo == CuentaEstadoResultados.class) {
//	                CuentaEstadoResultados cuenta = new CuentaEstadoResultados(
//	                        Integer.parseInt(partes[0]), partes[1],
//	                        Integer.parseInt(partes[2]), partes[3],
//	                        partes[4], partes[5], partes[6]);
//	                resultado.add(tipo.cast(cuenta));
//
//	            } else if (tipo == String.class) {
				// Asumimos estructura clase;grupo;cuenta;subcuenta
				if (partes.length >= 4) {
					String clase = String.format("%1s", partes[0]);
					String grupo = String.format("%01d", Integer.parseInt(partes[1]));
					String cuenta = String.format("%02d", Integer.parseInt(partes[2]));
					String subcuenta = String.format("%02d", Integer.parseInt(partes[3]));

					String codigoPUC = clase + grupo + cuenta + subcuenta;
					resultado.add(tipo.cast(String.valueOf(Integer.parseInt(codigoPUC))));
//	                }
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

		return resultado;
	}

//    public List<CuentaEstadoResultados> obtenerCuentasEstadoResultados() {
//        List<CuentaEstadoResultados> cuentas = new ArrayList<>();
//        System.out.println("Cargando archivo: " + getClass().getClassLoader().getResource("archEstadoResultados.csv"));
//        try (BufferedReader br = new BufferedReader(new InputStreamReader(
//                getClass().getClassLoader().getResourceAsStream("archEstadoResultados.csv")))) {
//            
//            cuentas = br.lines()
//                    .skip(1) // Omitir la cabecera
//                    .map(line -> {
//                        String[] partes = line.split(",");
//                        return new CuentaEstadoResultados(
//                                Integer.parseInt(partes[0]), partes[1], 
//                                Integer.parseInt(partes[2]), partes[3], 
//                                partes[4], partes[5], partes[6]);
//                    })
//                    .collect(Collectors.toList());
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return cuentas;
//    }

	public List<String> construirFiltroCodigosPUCDesdeCSV(String rutaCsv) {
		List<String> codigosPUC = new ArrayList<>();

		try (BufferedReader br = new BufferedReader(new FileReader(rutaCsv))) {
			String linea;
			// Saltar encabezado
			br.readLine();

			while ((linea = br.readLine()) != null) {
				String[] partes = linea.split(";");
				if (partes.length >= 4) {
					String clase = String.format("%1s", partes[0]); // texto de mínimo 1 carácter
					String grupo = String.format("%01d", Integer.parseInt(partes[1])); // sin ceros extra
					String cuenta = String.format("%02d", Integer.parseInt(partes[2])); // dos dígitos, con cero a la
																						// izquierda si es 1 dígito
					String subcuenta = String.format("%02d", Integer.parseInt(partes[3])); // dos dígitos, con cero a la
																							// izquierda

					String codigoPUC = clase + grupo + cuenta + subcuenta;
					codigosPUC.add(String.valueOf(Integer.parseInt(codigoPUC)));
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

		return codigosPUC;
	}
}
