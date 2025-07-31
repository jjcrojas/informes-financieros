package co.gov.sfc.infdinamicos.service;

import co.gov.sfc.infdinamicos.model.CuentaEstadoResultados;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EstadoResultadosService {
	
	public <T> List<T> leerCuentasDesdeCSV(String nombreArchivo, boolean tieneEncabezado, String separador, Class<T> tipo) {
	    List<T> resultado = new ArrayList<>();

	    try (BufferedReader br = new BufferedReader(new InputStreamReader(
	            getClass().getClassLoader().getResourceAsStream(nombreArchivo)))) {

	        if (tieneEncabezado) br.readLine(); // Saltar encabezado

	        String linea;
	        while ((linea = br.readLine()) != null) {
	            String[] partes = linea.split(separador);

	            if (tipo == CuentaEstadoResultados.class) {
	                CuentaEstadoResultados cuenta = new CuentaEstadoResultados(
	                        Integer.parseInt(partes[0]), partes[1],
	                        Integer.parseInt(partes[2]), partes[3],
	                        partes[4], partes[5], partes[6]);
	                resultado.add(tipo.cast(cuenta));

	            } else if (tipo == String.class) {
	                // Asumimos estructura clase;grupo;cuenta;subcuenta
	                if (partes.length >= 4) {
	                    String clase = String.format("%1s", partes[0]);
	                    String grupo = String.format("%01d", Integer.parseInt(partes[1]));
	                    String cuenta = String.format("%02d", Integer.parseInt(partes[2]));
	                    String subcuenta = String.format("%02d", Integer.parseInt(partes[3]));

	                    String codigoPUC = clase + grupo + cuenta + subcuenta;
	                    resultado.add(tipo.cast(String.valueOf(Integer.parseInt(codigoPUC))));
	                }
	            }
	        }

	    } catch (IOException e) {
	        e.printStackTrace();
	    }

	    return resultado;
	}

	

    public List<CuentaEstadoResultados> obtenerCuentasEstadoResultados() {
        List<CuentaEstadoResultados> cuentas = new ArrayList<>();
        System.out.println("Cargando archivo: " + getClass().getClassLoader().getResource("archEstadoResultados.csv"));
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                getClass().getClassLoader().getResourceAsStream("archEstadoResultados.csv")))) {
            
            cuentas = br.lines()
                    .skip(1) // Omitir la cabecera
                    .map(line -> {
                        String[] partes = line.split(",");
                        return new CuentaEstadoResultados(
                                Integer.parseInt(partes[0]), partes[1], 
                                Integer.parseInt(partes[2]), partes[3], 
                                partes[4], partes[5], partes[6]);
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            e.printStackTrace();
        }
        return cuentas;
    }
    
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
                	String cuenta = String.format("%02d", Integer.parseInt(partes[2])); // dos dígitos, con cero a la izquierda si es 1 dígito
                	String subcuenta = String.format("%02d", Integer.parseInt(partes[3])); // dos dígitos, con cero a la izquierda

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
