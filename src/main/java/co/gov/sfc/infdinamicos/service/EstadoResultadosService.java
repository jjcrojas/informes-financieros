package co.gov.sfc.infdinamicos.service;

import co.gov.sfc.infdinamicos.model.CuentaEstadoResultados;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EstadoResultadosService {

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
}
