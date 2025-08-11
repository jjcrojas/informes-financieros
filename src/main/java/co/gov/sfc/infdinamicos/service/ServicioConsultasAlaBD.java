package co.gov.sfc.infdinamicos.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import co.gov.sfc.infdinamicos.model.GrupoEstadoResultados;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ServicioConsultasAlaBD {

	private final JdbcTemplate jdbcTemplate;

	@Autowired
	private ServicioCuentasDesdeArchivo cuentasDesdeArchivo;

	public ServicioConsultasAlaBD(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<Map<String, Object>> obtenerDatosPrueba() {
		String sql = "SELECT TOP 10 * FROM PROD_DWH_CONSULTA.ENTIDADES";
		return jdbcTemplate.queryForList(sql);
	}
	
	public List<Map<String, Object>> obtenerEstadoResultados(int codigoEntidad, String fecha) {

	    Logger log = LoggerFactory.getLogger(getClass());

	    Map<String, GrupoEstadoResultados> gruposCuentas =
	            cuentasDesdeArchivo.armarGruposDeCuentas(
	                    "archEstadoResultados.csv", true, ";");

	    List<Map<String, Object>> filasDeEstadoResultados = new ArrayList<>();

	    // Guardar totales
	    double totalIngresos = 0.0;
	    double totalGastos = 0.0;
	    
	    Integer ordencatActual = null;
	    int posInicioSeccion   = 0; // para insertar total al inicio de cada sección
	    
	    for (Map.Entry<String, GrupoEstadoResultados> entry : gruposCuentas.entrySet()) {
	        GrupoEstadoResultados g = entry.getValue();
	        if (ordencatActual != null && g.getOrdencat() != ordencatActual) {
	            if (ordencatActual == 1) {
	                Map<String, Object> totalIng = new HashMap<>();
	                totalIng.put("Grupo", "TOTAL INGRESOS");
	                totalIng.put("total_saldo", totalIngresos);
	                filasDeEstadoResultados.add(posInicioSeccion, totalIng);
	            } else if (ordencatActual == 2) {
	                Map<String, Object> totalGas = new HashMap<>();
	                totalGas.put("Grupo", "TOTAL GASTOS");
	                totalGas.put("total_saldo", totalGastos);
	                filasDeEstadoResultados.add(posInicioSeccion, totalGas);
	            }
	            // nueva sección comienza aquí
	            posInicioSeccion = filasDeEstadoResultados.size();
	        }

	        ordencatActual = g.getOrdencat();

	        // Crear placeholders dinámicos
	        String placeholders = g.getCuentas().stream().map(c -> "?").collect(Collectors.joining(", "));

	        // Query
	        String query = "SELECT '" + g.getNombreGrupo() + "' AS Grupo, " +
	                "SUM(ef.Saldo_Sincierre_Total_Moneda_0)/1000000 AS total_saldo " +
	                "FROM prod_dwh_consulta.estfin_indiv ef " +
	                "JOIN prod_dwh_consulta.TIEMPO T ON T.Tie_ID = ef.Tie_ID " +
	                "JOIN prod_dwh_consulta.ENTIDADES E ON E.Ent_ID = ef.Ent_ID " +
	                "JOIN prod_dwh_consulta.PUC P ON P.Puc_ID = ef.Puc_ID " +
	                "WHERE T.Fecha = ? " +
	                "AND E.Tipo_Entidad = 23 " +
	                "AND E.Codigo_Entidad = ? " +
	                "AND ef.Tipo_Informe = 0 " +
	                "AND P.codigo IN (" + placeholders + ")";

	        // Parámetros
	        List<Object> params = new ArrayList<>();
	        params.add(fecha);
	        params.add(codigoEntidad);
	        params.addAll(g.getCuentas());
	        
	        if (log.isDebugEnabled()) {
            String sqlDebug = query;
            for (Object param : params) {
                String value = (param instanceof String) ? "'" + param + "'" : String.valueOf(param);
                sqlDebug = sqlDebug.replaceFirst("\\?", value);
            }
            log.debug("ER grupo='{}' cat='{}' ordencat={} ->\n{}", g.getNombreGrupo(), g.getCategoria(), g.getOrdencat(), sqlDebug);
        }
	        
	        Map<String, Object> fila = jdbcTemplate.queryForMap(query, params.toArray());
	        
	        double saldo = fila.get("total_saldo") != null ? ((Number) fila.get("total_saldo")).doubleValue() : 0.0;
	        
	        if (g.getOrdencat() == 1)  {
	            totalIngresos += saldo;
	        } else if (g.getOrdencat() == 2) {
	            totalGastos += saldo;
	        } else {
	            log.warn("Categoría desconocida en CSV: '{}'", g.getCategoria());
	        }

	        filasDeEstadoResultados.add(fila);
	    }

	    if (ordencatActual != null) {
	        Map<String, Object> total = new HashMap<>();
	        if (ordencatActual == 1) {
	            total.put("Grupo", "TOTAL INGRESOS");
	            total.put("total_saldo", totalIngresos);
	        } else {
	            total.put("Grupo", "TOTAL GASTOS");
	            total.put("total_saldo", totalGastos);
	        }
	        filasDeEstadoResultados.add(posInicioSeccion, total);
	    }

	    return filasDeEstadoResultados;
	}


//	public List<Map<String, Object>> obtenerEstadoResultados(int codigoEntidad, String fecha) {
//		
//
//		Logger log = LoggerFactory.getLogger(getClass());
//		
//		Map<GrupoClave, List<String>> gruposCuentas =
//			    cuentasDesdeArchivo.leerCodigosDesdeCSVConOrdenYNombre(
//			        "archEstadoResultados.csv", true, ";"
//			    );
//	    
//		// Ordenar por categoría y grupo
//		List<Map.Entry<GrupoClave, List<String>>> listaOrdenada = gruposCuentas.entrySet()
//		    .stream()
//		    .sorted(Comparator
//		        .comparing((Map.Entry<GrupoClave, List<String>> e) -> e.getKey().ordencat)
//		        .thenComparing(e -> e.getKey().ordengru))
//		    .toList();
//
//		List<Map<String, Object>> filasDeEstadoResultados = new ArrayList<>();
//
//		for (Map.Entry<GrupoClave, List<String>> grupo : listaOrdenada) {
//			// Crear placeholders dinámicos: ?, ?, ?, ...
//			String placeholders = grupo.getValue().stream().map(c -> "?").collect(Collectors.joining(", "));
//
//			// Query con placeholders
//	        String query = "SELECT '" + grupo.getKey() + "' AS Grupo, " +
//	                "SUM(ef.Saldo_Sincierre_Total_Moneda_0) AS total_saldo " +
//	                "FROM prod_dwh_consulta.estfin_indiv ef " +
//	                "JOIN prod_dwh_consulta.TIEMPO T ON T.Tie_ID = ef.Tie_ID " +
//	                "JOIN prod_dwh_consulta.ENTIDADES E ON E.Ent_ID = ef.Ent_ID " +
//	                "JOIN prod_dwh_consulta.PUC P ON P.Puc_ID = ef.Puc_ID " +
//	                "WHERE T.Fecha = ? " +
//	                "AND E.Tipo_Entidad = 23 " +
//	                "AND E.Codigo_Entidad = ? " +
//	                "AND ef.Tipo_Informe = 0 " +
//	                "AND P.codigo IN (" + placeholders + ")";
//	        
//	        // Armar parámetros en el mismo orden que los placeholders
//	        List<Object> params = new ArrayList<>();
//	        params.add(fecha);           
//	        params.add(codigoEntidad);   
//	        params.addAll(grupo.getValue()); 	   
//	        
////	        System.out.println("QUERY Estado Resultados:\n" + query);
//	        // 💡 Construir SQL de depuración sustituyendo cada "?"
//	        if (log.isDebugEnabled()) {
//	            String sqlDebug = query;
//	            for (Object param : params) {
//	                String value = (param instanceof String) ? "'" + param + "'" : String.valueOf(param);
//	                sqlDebug = sqlDebug.replaceFirst("\\?", value);
//	            }
//	            log.debug("QUERY Estado Resultados DEBUG:\n{}", sqlDebug);
//	        }
//
//	        List<Map<String, Object>> resultadoQuery = jdbcTemplate.queryForList(query, params.toArray());
//			filasDeEstadoResultados.addAll(resultadoQuery);
//		}
//
//		return filasDeEstadoResultados;
//	}

	public List<Map<String, Object>> obtenerReporteFinanciero(int codigoEntidad, String fechaMayor) {
		String fechaMenor = calcularFechaMenor(fechaMayor);

//		// Leer lista de códigos desde el CSV
//		List<String> codigosPUC = cuentasDesdeArchivo
//				.construirFiltroCodigosPUCDesdeCSV("src/main/resources/archCuentasBalance.csv");
		
		List<String> codigosPUC = cuentasDesdeArchivo
			    .leerCuentasDesdeCSV("archCuentasBalance.csv", true, ";", String.class);


		
		// Validar lista no vacía
	    if (codigosPUC.isEmpty()) {
	        throw new RuntimeException("La lista de códigos PUC está vacía.");
	    }
		// Crear placeholders dinámicos: ?, ?, ?, ...
		String placeholders = codigosPUC.stream().map(c -> "?").collect(Collectors.joining(", "));

		// Construir SQL con los códigos insertados en el IN (...)
		String sql = """
				WITH ActivoValores AS (
				    SELECT
				        tie.Fecha,
				        MAX(estfin.Saldo_Sincierre_Total_Moneda_0) AS Valor_Activo
				    FROM PROD_DWH_CONSULTA.ESTFIN_INDIV estfin
				    JOIN PROD_DWH_CONSULTA.ENTIDADES ent ON estfin.Ent_ID = ent.Ent_ID
				    JOIN PROD_DWH_CONSULTA.TIEMPO tie ON estfin.Tie_ID = tie.Tie_ID
				    JOIN PROD_DWH_CONSULTA.PUC puc ON estfin.Puc_ID = puc.Puc_ID
				    WHERE ent.Tipo_Entidad = 23
				      AND ent.Codigo_Entidad = ?
				      AND tie.Fecha = ?
				      AND estfin.Tipo_Informe = 0
				    GROUP BY tie.Fecha
				),
				ValoresAnteriores AS (
				    SELECT
				        estfin.Ent_ID,
				        estfin.Puc_ID,
				        puc.Codigo,
				        MAX(estfin.Saldo_Sincierre_Total_Moneda_0) AS Valor_Anterior
				    FROM PROD_DWH_CONSULTA.ESTFIN_INDIV estfin
				    JOIN PROD_DWH_CONSULTA.TIEMPO tie ON estfin.Tie_ID = tie.Tie_ID
				    JOIN PROD_DWH_CONSULTA.PUC puc ON estfin.Puc_ID = puc.Puc_ID
				    JOIN PROD_DWH_CONSULTA.ENTIDADES ent ON estfin.Ent_ID = ent.Ent_ID
				    WHERE tie.Fecha = ?
				      AND ent.Tipo_Entidad = 23
				      AND ent.Codigo_Entidad = ?
				      AND estfin.Tipo_Informe = 0
				    GROUP BY estfin.Ent_ID, estfin.Puc_ID, puc.Codigo
				)
				SELECT
				    puc.Nombre AS Nombre_Cuenta,
				    puc.Codigo,

				    ROUND(MAX(CASE
				        WHEN tie.Fecha = ?
				        THEN estfin.Saldo_Sincierre_Total_Moneda_0 / 1000000
				    END), 2) AS Valor_Actual_Millones,

				    ROUND(val_ant.Valor_Anterior / 1000000, 2) AS Valor_Anterior_Millones,

				    ROUND(MAX(CASE
				        WHEN tie.Fecha = ?
				        THEN (estfin.Saldo_Sincierre_Total_Moneda_0 / act.Valor_Activo) * 100
				    END), 1) AS Porcentaje_Participacion_Actual,

				    ROUND(CASE
				        WHEN val_ant.Valor_Anterior IS NULL OR val_ant.Valor_Anterior = 0
				        THEN NULL
				        ELSE ((MAX(CASE WHEN tie.Fecha = ? THEN estfin.Saldo_Sincierre_Total_Moneda_0 END) - val_ant.Valor_Anterior)
				              / val_ant.Valor_Anterior) * 100
				    END, 1) AS Variacion_Anual

				FROM PROD_DWH_CONSULTA.PUC puc
				LEFT JOIN PROD_DWH_CONSULTA.ESTFIN_INDIV estfin ON estfin.Puc_ID = puc.Puc_ID
				LEFT JOIN PROD_DWH_CONSULTA.TIEMPO tie ON estfin.Tie_ID = tie.Tie_ID
				LEFT JOIN PROD_DWH_CONSULTA.ENTIDADES ent ON estfin.Ent_ID = ent.Ent_ID
				LEFT JOIN ActivoValores act ON tie.Fecha = ?
				LEFT JOIN ValoresAnteriores val_ant ON puc.Puc_ID = val_ant.Puc_ID
				WHERE ent.Tipo_Entidad = 23
				  AND ent.Codigo_Entidad = ?
				  AND tie.Fecha IN (?, ?)
				  """
				+ "AND puc.Codigo IN (" + placeholders + ") " + """
						      AND (tie.Fecha IS NOT NULL OR estfin.Puc_ID IS NULL)
						    GROUP BY puc.Nombre, puc.Codigo, val_ant.Valor_Anterior
						    ORDER BY puc.Codigo
						""";

		// Armar parámetros de forma ordenada
	    List<Object> parametros = new ArrayList<>();
	    parametros.add(codigoEntidad);
	    parametros.add(fechaMayor);
	    parametros.add(fechaMenor);
	    parametros.add(codigoEntidad);
	    parametros.add(fechaMayor);
	    parametros.add(fechaMayor);
	    parametros.add(fechaMayor);
	    parametros.add(fechaMayor);
	    parametros.add(codigoEntidad);
	    parametros.add(fechaMayor);
	    parametros.add(fechaMenor);

	    // Agregar códigos del IN
	    parametros.addAll(codigosPUC);
	    
		System.out.println("QUERY:\n" + sql);

	    return jdbcTemplate.queryForList(sql, parametros.toArray());
	}

	public List<Map<String, Object>> obtenerBalance(int codigoEntidad, String fechaMayor) {
		// Calcular la fecha menor (tres meses antes de la fecha mayor)
		String fechaMenor = calcularFechaMenor(fechaMayor);

		String sql = """
				    WITH ActivoValores AS (
				        SELECT
				            tie.Fecha,
				            MAX(estfin.Saldo_Sincierre_Total_Moneda_0) AS Valor_Activo
				        FROM PROD_DWH_CONSULTA.ESTFIN_INDIV estfin
				        JOIN PROD_DWH_CONSULTA.ENTIDADES ent
				            ON estfin.Ent_ID = ent.Ent_ID
				        JOIN PROD_DWH_CONSULTA.TIEMPO tie
				            ON estfin.Tie_ID = tie.Tie_ID
				        JOIN PROD_DWH_CONSULTA.PUC puc
				            ON estfin.Puc_ID = puc.Puc_ID
				        WHERE
				            ent.Tipo_Entidad = 23
				            AND ent.Codigo_Entidad = ?
				            AND tie.Fecha = ?
				            AND estfin.Tipo_Informe = 0
				        GROUP BY tie.Fecha
				    ),
				    ValoresAnteriores AS (
				        SELECT
				            estfin.Ent_ID,
				            estfin.Puc_ID,
				            puc.Clase,
				            puc.Grupo,
				            puc.Cuenta,
				            puc.Subcuenta,
				            MAX(estfin.Saldo_Sincierre_Total_Moneda_0) AS Valor_Anterior
				        FROM PROD_DWH_CONSULTA.ESTFIN_INDIV estfin
				        JOIN PROD_DWH_CONSULTA.TIEMPO tie
				            ON estfin.Tie_ID = tie.Tie_ID
				        JOIN PROD_DWH_CONSULTA.PUC puc
				            ON estfin.Puc_ID = puc.Puc_ID
				        JOIN PROD_DWH_CONSULTA.ENTIDADES ent
				            ON estfin.Ent_ID = ent.Ent_ID
				        WHERE
				            tie.Fecha = ?
				            AND ent.Tipo_Entidad = 23
				            AND ent.Codigo_Entidad = ?
				            AND estfin.Tipo_Informe =0
				        GROUP BY estfin.Ent_ID, estfin.Puc_ID, puc.Clase, puc.Grupo, puc.Cuenta, puc.Subcuenta
				    )
				    SELECT
				        puc.Nombre AS Nombre_Cuenta,
				        puc.Clase,
				        puc.Grupo,
				        puc.Cuenta,
				        puc.Subcuenta,
				        COALESCE(ROUND(MAX(CASE
				            WHEN tie.Fecha = ?
				            THEN estfin.Saldo_Sincierre_Total_Moneda_0 / 1000000
				        END), 2), 0.00) AS Valor_Actual_Millones,
				        COALESCE(ROUND(val_ant.Valor_Anterior / 1000000, 2), 0.00) AS Valor_Anterior_Millones
				    FROM PROD_DWH_CONSULTA.PUC puc
				    LEFT JOIN PROD_DWH_CONSULTA.ESTFIN_INDIV estfin
				        ON estfin.Puc_ID = puc.Puc_ID
				    LEFT JOIN PROD_DWH_CONSULTA.TIEMPO tie
				        ON estfin.Tie_ID = tie.Tie_ID
				    LEFT JOIN PROD_DWH_CONSULTA.ENTIDADES ent
				        ON estfin.Ent_ID = ent.Ent_ID
				    LEFT JOIN ActivoValores act2024
				        ON tie.Fecha = ?
				    LEFT JOIN ValoresAnteriores val_ant
				        ON puc.Puc_ID = val_ant.Puc_ID
				    WHERE
				        ent.Tipo_Entidad = 23
				        AND ent.Codigo_Entidad = ?
				        AND tie.Fecha IN (?, ?)
				    GROUP BY puc.Nombre, puc.Clase, puc.Grupo, puc.Cuenta, puc.Subcuenta, val_ant.Valor_Anterior
				    ORDER BY puc.Clase, puc.Grupo, puc.Cuenta, puc.Subcuenta
				""";

//        System.out.println("SQL: " + sql);
//        System.out.println("Parámetros : " + " " + codigoEntidad + " " + fechaMayor + " " + 
//        		fechaMenor + " " + codigoEntidad + " " + fechaMayor + " " + fechaMayor + " " + 
//        		codigoEntidad + " " + fechaMayor + " " + fechaMenor);     

		return jdbcTemplate.queryForList(sql, codigoEntidad, fechaMayor, fechaMenor, codigoEntidad, fechaMayor,
				fechaMayor, codigoEntidad, fechaMayor, fechaMenor);

	}

	// Método auxiliar para calcular la fecha menor (tres meses antes)
	public String calcularFechaMenor(String fechaMayorStr) {
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		LocalDate fechaMayor = LocalDate.parse(fechaMayorStr, formatter);

		// Restar 3 meses
		LocalDate fechaMenor = fechaMayor.minusMonths(3);

		// Ajustar al último día del mes
		YearMonth yearMonth = YearMonth.of(fechaMenor.getYear(), fechaMenor.getMonth());
		LocalDate fechaMenorAjustada = yearMonth.atEndOfMonth();

		return fechaMenorAjustada.format(formatter);
	}

//    import org.apache.poi.ss.usermodel.*;
//    import org.apache.poi.xssf.usermodel.XSSFWorkbook;
//    import java.io.ByteArrayOutputStream;
//    import java.io.IOException;
//    import java.util.List;
//    import java.util.Map;

	public byte[] generarReporteExcel(int codigoEntidad, String fechaMayor) throws IOException {
		List<Map<String, Object>> datosBalance = obtenerBalance(codigoEntidad, fechaMayor);

		// Crear un nuevo libro de Excel
		XSSFWorkbook workbook = new XSSFWorkbook();
		XSSFSheet sheet = workbook.createSheet("Balance");

		// Crear encabezados
		Row headerRow = sheet.createRow(0);
		String[] columnas = { "Nombre Cuenta", "Clase", "Grupo", "Cuenta", "Subcuenta", "Valor Actual (Millones)",
				"Valor Anterior (Millones)" };

		for (int i = 0; i < columnas.length; i++) {
			Cell cell = headerRow.createCell(i);
			cell.setCellValue(columnas[i]);
			cell.setCellStyle(estiloEncabezado(workbook));
		}

		// Llenar el Excel con los datos del balance
		int rowNum = 1;
		for (Map<String, Object> fila : datosBalance) {
			Row row = sheet.createRow(rowNum++);
			row.createCell(0).setCellValue(fila.get("Nombre_Cuenta").toString());
			row.createCell(1).setCellValue(fila.get("Clase").toString());
			row.createCell(2).setCellValue(fila.get("Grupo").toString());
			row.createCell(3).setCellValue(fila.get("Cuenta").toString());
			row.createCell(4).setCellValue(fila.get("Subcuenta").toString());
			row.createCell(5).setCellValue(Double.parseDouble(fila.get("Valor_Actual_Millones").toString()));
			row.createCell(6).setCellValue(Double.parseDouble(fila.get("Valor_Anterior_Millones").toString()));
		}

		// Ajustar tamaño de columnas automáticamente
		for (int i = 0; i < columnas.length; i++) {
			sheet.autoSizeColumn(i);
		}

		// Convertir a byte array para descargar
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		workbook.write(outputStream);
		workbook.close();
		return outputStream.toByteArray();
	}

	// Método para dar estilo a los encabezados
	private CellStyle estiloEncabezado(Workbook workbook) {
		CellStyle style = workbook.createCellStyle();
		Font font = workbook.createFont();
		font.setBold(true);
		style.setFont(font);
		return style;
	}

}
