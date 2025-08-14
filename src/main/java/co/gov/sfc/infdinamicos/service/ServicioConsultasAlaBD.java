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
import co.gov.sfc.infdinamicos.model.LineaBalance;
import co.gov.sfc.infdinamicos.model.CuentaBalance;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
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

	// --- Helpers de fechas ---
	private String finDeMesMenosMeses(String fechaISOyyyyMMdd, int meses) {
	    LocalDate f = LocalDate.parse(fechaISOyyyyMMdd);
	    YearMonth ym = YearMonth.from(f).minusMonths(meses);
	    return ym.atEndOfMonth().toString(); // yyyy-MM-dd
	}

	private String finDeMesMenosAnios(String fechaISOyyyyMMdd, int anios) {
	    LocalDate f = LocalDate.parse(fechaISOyyyyMMdd);
	    YearMonth ym = YearMonth.from(f).minusYears(anios);
	    return ym.atEndOfMonth().toString(); // yyyy-MM-dd
	}

	private double asDouble(Object v) {
	    return (v == null) ? 0.0 : ((Number) v).doubleValue();
	}


	
	
	public List<Map<String, Object>> obtenerEstadoResultados(int codigoEntidad, String fecha) {

		Logger log = LoggerFactory.getLogger(getClass());
		
	    // Fechas comparativas
	    String fechaT3  = finDeMesMenosMeses(fecha, 3);  // tres meses antes
	    String fechaT12 = finDeMesMenosAnios(fecha, 1);  // un año antes

		Map<String, GrupoEstadoResultados> gruposCuentas = cuentasDesdeArchivo
				.armarGruposDeCuentas("archEstadoResultados.csv", true, ";");

		List<Map<String, Object>> filasDeEstadoResultados = new ArrayList<>();

		// Guardar totales
		double totalIngresosActual = 0.0;
		double totalGastosActual = 0.0;

		Integer ordencatActual = null;
		int posInicioSeccion = 0; // para insertar total al inicio de cada sección

		for (Map.Entry<String, GrupoEstadoResultados> entry : gruposCuentas.entrySet()) {
			GrupoEstadoResultados g = entry.getValue();
			
	        // Si cambia la categoría, cierra la anterior insertando el total UNA VEZ
	        if (ordencatActual != null && g.getOrdencat() != ordencatActual) {
	            if (ordencatActual == 1) {
	                Map<String,Object> totIng = new HashMap<>();
	                totIng.put("Grupo", "TOTAL INGRESOS");
	                totIng.put("actual", totalIngresosActual);
	                totIng.put("tresMeses", null);
	                totIng.put("unAnio", null);
	                totIng.put("varTrim", null);
	                totIng.put("varAnual", null);
	                totIng.put("esTotal", true);
	                filasDeEstadoResultados.add(posInicioSeccion, totIng);
	            } else if (ordencatActual == 2) {
	                Map<String,Object> totGas = new HashMap<>();
	                totGas.put("Grupo", "TOTAL GASTOS");
	                totGas.put("actual", totalGastosActual);
	                totGas.put("tresMeses", null);
	                totGas.put("unAnio", null);
	                totGas.put("varTrim", null);
	                totGas.put("varAnual", null);
	                totGas.put("esTotal", true);
	                filasDeEstadoResultados.add(posInicioSeccion, totGas);
	            }
	            posInicioSeccion = filasDeEstadoResultados.size();
	        }
	        ordencatActual = g.getOrdencat();

			// Crear placeholders dinámicos
			String placeholders = g.getCuentas().stream().map(c -> "?").collect(Collectors.joining(", "));

			// Query
			String query = "SELECT '" + g.getNombreGrupo() + "' AS Grupo, "
					+ "ROUND(SUM(CASE WHEN T.Fecha = ? THEN ef.Saldo_Sincierre_Total_Moneda_0 ELSE 0 END)/1000000,2) AS actual, " 
					+ "ROUND(SUM(CASE WHEN T.Fecha = ? THEN ef.Saldo_Sincierre_Total_Moneda_0 ELSE 0 END)/1000000,2) AS tresMeses, " 
					+ "ROUND(SUM(CASE WHEN T.Fecha = ? THEN ef.Saldo_Sincierre_Total_Moneda_0 ELSE 0 END)/1000000,2) AS unAnio " 
					+ "FROM prod_dwh_consulta.estfin_indiv ef "
					+ "JOIN prod_dwh_consulta.TIEMPO T ON T.Tie_ID = ef.Tie_ID "
					+ "JOIN prod_dwh_consulta.ENTIDADES E ON E.Ent_ID = ef.Ent_ID "
					+ "JOIN prod_dwh_consulta.PUC P ON P.Puc_ID = ef.Puc_ID " 
					+ "WHERE E.Tipo_Entidad = 23 " + "AND E.Codigo_Entidad = ? " + "AND ef.Tipo_Informe = 0 "
					+ "AND P.codigo IN (" + placeholders + ") " 
					+ "AND   T.Fecha IN (?, ?, ?)";

			// Parámetros
			List<Object> params = new ArrayList<>();
	        params.add(fecha);
	        params.add(fechaT3);
	        params.add(fechaT12);
			params.add(codigoEntidad);
			params.addAll(g.getCuentas());
	        params.add(fecha);
	        params.add(fechaT3);
	        params.add(fechaT12);			

			if (log.isDebugEnabled()) {
				String sqlDebug = query;
				for (Object param : params) {
					String value = (param instanceof String) ? "'" + param + "'" : String.valueOf(param);
					sqlDebug = sqlDebug.replaceFirst("\\?", value);
				}
				log.debug("ER grupo='{}' cat='{}' ordencat={} ->\n{}", g.getNombreGrupo(), g.getCategoria(),
						g.getOrdencat(), sqlDebug);
			}

			Map<String, Object> r = jdbcTemplate.queryForMap(query, params.toArray());

			double actual = r.get("actual") != null ? ((Number) r.get("actual")).doubleValue() : 0.0;
			double tresMeses = r.get("tresMeses") != null ? ((Number) r.get("tresMeses")).doubleValue() : 0.0;
			double unAnio = r.get("unAnio") != null ? ((Number) r.get("unAnio")).doubleValue() : 0.0;
			

	        // Variaciones (%)
	        Double varTrim  = (tresMeses == 0.0) ? null : ((actual - tresMeses) / tresMeses) * 100.0;
	        Double varAnual = (unAnio    == 0.0) ? null : ((actual - unAnio)    / unAnio)    * 100.0;
			
			if (g.getOrdencat() == 1) {
				totalIngresosActual += actual;
			} else if (g.getOrdencat() == 2) {
				totalGastosActual += actual;
			} else {
				log.warn("Categoría desconocida en CSV: '{}'", g.getCategoria());
			}
			
	        Map<String,Object> fila = new HashMap<>();
	        fila.put("Grupo", g.getNombreGrupo());
	        fila.put("actual", actual);
	        fila.put("tresMeses", tresMeses);
	        fila.put("unAnio", unAnio);
	        fila.put("varTrim", varTrim);
	        fila.put("varAnual", varAnual);
	        fila.put("esTotal", false);

			filasDeEstadoResultados.add(fila);
		}
	    // Cierra la última sección
	    if (ordencatActual != null) {
	        if (ordencatActual == 1) {
	            Map<String,Object> totIng = new HashMap<>();
	            totIng.put("Grupo", "TOTAL INGRESOS");
	            totIng.put("actual", totalIngresosActual);
	            totIng.put("tresMeses", null);
	            totIng.put("unAnio", null);
	            totIng.put("varTrim", null);
	            totIng.put("varAnual", null);
	            totIng.put("esTotal", true);
	            filasDeEstadoResultados.add(posInicioSeccion, totIng);
	        } else if (ordencatActual == 2) {
	            Map<String,Object> totGas = new HashMap<>();
	            totGas.put("Grupo", "TOTAL GASTOS");
	            totGas.put("actual", totalGastosActual);
	            totGas.put("tresMeses", null);
	            totGas.put("unAnio", null);
	            totGas.put("varTrim", null);
	            totGas.put("varAnual", null);
	            totGas.put("esTotal", true);
	            filasDeEstadoResultados.add(posInicioSeccion, totGas);
	        }
	    }
		return filasDeEstadoResultados;
	}

	public List<Map<String, Object>> obtenerReporteFinanciero(int codigoEntidad, String fechaMayor) {
		
//		Logger log = LoggerFactory.getLogger(getClass());
		
		String fechaMenor = calcularFechaMenor(fechaMayor);
		
		List<LineaBalance> lineas = cuentasDesdeArchivo
                .leerLineasBalanceDesdeCSV("archCuentasBalance.csv", true, ";");
		
		Set<String> codigosUnicos = lineas.stream()
                .flatMap(l -> l.getCuentas().stream().map(CuentaBalance::getCodigo))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Trae valores por código en una sola query (para ambas fechas)
        Map<String, Map<String,Object>> porCodigo =
                consultarValoresPorCodigo(codigoEntidad, fechaMayor, fechaMenor, codigosUnicos);

        // Denominador para % participación 
        Double activoTotal = obtenerActivoTotal(codigoEntidad, fechaMayor);
        
        List<Map<String,Object>> out = new ArrayList<>();
        //lineas son las filas del archivo
        for (LineaBalance l : lineas) {
            double actual = 0.0, anterior = 0.0;

            //Opera las cuentas que haya al hacer l.getCuentas
            for (CuentaBalance t : l.getCuentas()) {
                Map<String,Object> r = porCodigo.get(t.getCodigo());
                double va = (r != null && r.get("valor_actual") != null)   ? ((Number) r.get("valor_actual")).doubleValue()   : 0.0;
                double vp = (r != null && r.get("valor_anterior") != null) ? ((Number) r.get("valor_anterior")).doubleValue() : 0.0;

                actual   += t.getSigno() * va;
                anterior += t.getSigno() * vp;
            }

            Double porcentaje = (activoTotal != null && activoTotal != 0)
                    ? (actual / activoTotal) * 100.0
                    : null;

            Double variacion = (anterior != 0)
                    ? ((actual - anterior) / anterior) * 100.0
                    : null;

            Map<String,Object> fila = new LinkedHashMap<>();
            fila.put("Nombre_Cuenta", l.getLinea());          // etiqueta de la fila (del CSV)
            String codigoPUC = l.getCodigoPrincipal().orElse("");
            fila.put("Codigo", codigoPUC);                            
            fila.put("Valor_Actual_Millones", formatoConParentesis(actual));
            fila.put("Valor_Anterior_Millones", formatoConParentesis(anterior));
            fila.put("Porcentaje_Participacion_Actual", porcentaje);
            fila.put("Variacion_Anual", variacion);

            out.add(fila);
        }

        return out;

//		List<String> codigosPUC = cuentasDesdeArchivo.leerCuentasDesdeCSV("archCuentasBalance.csv", true, ";",
//				String.class);
//
//		// Validar lista no vacía
//		if (codigosPUC.isEmpty()) {
//			throw new RuntimeException("La lista de códigos PUC está vacía.");
//		}
//		// Crear placeholders dinámicos: ?, ?, ?, ...
//		String placeholders = codigosPUC.stream().map(c -> "?").collect(Collectors.joining(", "));
//
//		// Construir SQL con los códigos insertados en el IN (...)
//		String sql = """
//				WITH ActivoValores AS (
//				    SELECT
//				        tie.Fecha,
//				        SUM(estfin.Saldo_Sincierre_Total_Moneda_0) AS Valor_Activo
//				    FROM PROD_DWH_CONSULTA.ESTFIN_INDIV estfin
//				    JOIN PROD_DWH_CONSULTA.ENTIDADES ent ON estfin.Ent_ID = ent.Ent_ID
//				    JOIN PROD_DWH_CONSULTA.TIEMPO tie ON estfin.Tie_ID = tie.Tie_ID
//				    JOIN PROD_DWH_CONSULTA.PUC puc ON estfin.Puc_ID = puc.Puc_ID
//				    WHERE ent.Tipo_Entidad = 23
//				      AND ent.Codigo_Entidad = ?
//				      AND tie.Fecha = ?
//				      AND estfin.Tipo_Informe = 0
//				      AND puc.codigo = 100000
//				    GROUP BY tie.Fecha
//				),
//				ValoresAnteriores AS (
//				    SELECT
//				        estfin.Ent_ID,
//				        estfin.Puc_ID,
//				        puc.Codigo,
//				        MAX(estfin.Saldo_Sincierre_Total_Moneda_0) AS Valor_Anterior
//				    FROM PROD_DWH_CONSULTA.ESTFIN_INDIV estfin
//				    JOIN PROD_DWH_CONSULTA.TIEMPO tie ON estfin.Tie_ID = tie.Tie_ID
//				    JOIN PROD_DWH_CONSULTA.PUC puc ON estfin.Puc_ID = puc.Puc_ID
//				    JOIN PROD_DWH_CONSULTA.ENTIDADES ent ON estfin.Ent_ID = ent.Ent_ID
//				    WHERE tie.Fecha = ?
//				      AND ent.Tipo_Entidad = 23
//				      AND ent.Codigo_Entidad = ?
//				      AND estfin.Tipo_Informe = 0
//				    GROUP BY estfin.Ent_ID, estfin.Puc_ID, puc.Codigo
//				)
//				SELECT
//				    puc.Nombre AS Nombre_Cuenta,
//				    puc.Codigo,
//
//				    ROUND(MAX(CASE
//				        WHEN tie.Fecha = ?
//				        THEN estfin.Saldo_Sincierre_Total_Moneda_0 / 1000000
//				    END), 2) AS Valor_Actual_Millones,
//
//				    ROUND(val_ant.Valor_Anterior / 1000000, 2) AS Valor_Anterior_Millones,
//
//				    ROUND(MAX(CASE
//				        WHEN tie.Fecha = ?
//				        THEN (estfin.Saldo_Sincierre_Total_Moneda_0 / act.Valor_Activo) * 100
//				    END), 1) AS Porcentaje_Participacion_Actual,
//
//				    ROUND(CASE
//				        WHEN val_ant.Valor_Anterior IS NULL OR val_ant.Valor_Anterior = 0
//				        THEN NULL
//				        ELSE ((MAX(CASE WHEN tie.Fecha = ? THEN estfin.Saldo_Sincierre_Total_Moneda_0 END) - val_ant.Valor_Anterior)
//				              / val_ant.Valor_Anterior) * 100
//				    END, 1) AS Variacion_Anual
//
//				FROM PROD_DWH_CONSULTA.PUC puc
//				LEFT JOIN PROD_DWH_CONSULTA.ESTFIN_INDIV estfin ON estfin.Puc_ID = puc.Puc_ID
//				LEFT JOIN PROD_DWH_CONSULTA.TIEMPO tie ON estfin.Tie_ID = tie.Tie_ID
//				LEFT JOIN PROD_DWH_CONSULTA.ENTIDADES ent ON estfin.Ent_ID = ent.Ent_ID
//				LEFT JOIN ActivoValores act ON tie.Fecha = ?
//				LEFT JOIN ValoresAnteriores val_ant ON puc.Puc_ID = val_ant.Puc_ID
//				WHERE ent.Tipo_Entidad = 23
//				  AND ent.Codigo_Entidad = ?
//				  AND tie.Fecha IN (?, ?)
//				  """
//				+ "AND puc.Codigo IN (" + placeholders + ") " + """
//						      AND (tie.Fecha IS NOT NULL OR estfin.Puc_ID IS NULL)
//						    GROUP BY puc.Nombre, puc.Codigo, val_ant.Valor_Anterior
//						    ORDER BY puc.Codigo
//						""";
//
//		// Armar parámetros de forma ordenada
//		List<Object> parametros = new ArrayList<>();
//		parametros.add(codigoEntidad);
//		parametros.add(fechaMayor);
//		parametros.add(fechaMenor);
//		parametros.add(codigoEntidad);
//		parametros.add(fechaMayor);
//		parametros.add(fechaMayor);
//		parametros.add(fechaMayor);
//		parametros.add(fechaMayor);
//		parametros.add(codigoEntidad);
//		parametros.add(fechaMayor);
//		parametros.add(fechaMenor);
//
//		// Agregar códigos del IN
//		parametros.addAll(codigosPUC);
//
//	    if (log.isDebugEnabled()) {
//	        log.debug("QUERY DEBUG Balance:\n{}", buildDebugSql(sql, parametros));
//	    }
//
//		return jdbcTemplate.queryForList(sql, parametros.toArray());
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

		return jdbcTemplate.queryForList(sql, codigoEntidad, fechaMayor, fechaMenor, codigoEntidad, fechaMayor,
				fechaMayor, codigoEntidad, fechaMayor, fechaMenor);

	}
	
    private Double obtenerActivoTotal(int codigoEntidad, String fechaMayor) {
        String sql = """
            SELECT ROUND(MAX(EF.Saldo_Sincierre_Total_Moneda_0)/1000000, 2) AS activo
            FROM PROD_DWH_CONSULTA.ESTFIN_INDIV EF
            JOIN PROD_DWH_CONSULTA.ENTIDADES E ON E.Ent_ID = EF.Ent_ID
            JOIN PROD_DWH_CONSULTA.TIEMPO T ON T.Tie_ID = EF.Tie_ID
            JOIN PROD_DWH_CONSULTA.PUC PUC ON EF.Puc_ID = PUC.Puc_ID
            WHERE E.Tipo_Entidad = 23
              AND E.Codigo_Entidad = ?
              AND T.Fecha = ?
              AND EF.Tipo_Informe = 0  
              AND PUC.codigo = 100000
        """;
        List<Map<String,Object>> r = jdbcTemplate.queryForList(sql, codigoEntidad, fechaMayor);
        if (r.isEmpty() || r.get(0).get("activo") == null) return null;
        return ((Number) r.get(0).get("activo")).doubleValue();
    }
	
	private Map<String, Map<String,Object>> consultarValoresPorCodigo(
            int codigoEntidad, String fechaMayor, String fechaMenor, Set<String> codigos) {

        if (codigos.isEmpty()) return Collections.emptyMap();
        
		Logger log = LoggerFactory.getLogger(getClass());

        String placeholders = codigos.stream().map(c -> "?").collect(Collectors.joining(","));
        String sql = """
            SELECT P.Codigo,
                   ROUND(MAX(CASE WHEN T.Fecha = ? THEN EF.Saldo_Sincierre_Total_Moneda_0 END)/1000000, 2) AS valor_actual,
                   ROUND(MAX(CASE WHEN T.Fecha = ? THEN EF.Saldo_Sincierre_Total_Moneda_0 END)/1000000, 2) AS valor_anterior
            FROM PROD_DWH_CONSULTA.ESTFIN_INDIV EF
            JOIN PROD_DWH_CONSULTA.TIEMPO T ON T.Tie_ID = EF.Tie_ID
            JOIN PROD_DWH_CONSULTA.ENTIDADES E ON E.Ent_ID = EF.Ent_ID
            JOIN PROD_DWH_CONSULTA.PUC P ON P.Puc_ID = EF.Puc_ID
            WHERE E.Tipo_Entidad = 23
              AND E.Codigo_Entidad = ?
              AND T.Fecha IN (?, ?)
              AND EF.Tipo_Informe = 0
              AND P.Codigo IN (""" + placeholders + ") " + """
            GROUP BY P.Codigo
        """;

        List<Object> params = new ArrayList<>();
        params.add(fechaMayor);   // para valor_actual
        params.add(fechaMenor);   // para valor_anterior
        params.add(codigoEntidad);
        params.add(fechaMayor);
        params.add(fechaMenor);
        params.addAll(codigos);

        if (log.isDebugEnabled()) log.debug("BALANCE SQL BASE:\n{}", buildDebugSql(sql, params));

        List<Map<String,Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
        Map<String, Map<String,Object>> porCodigo = new HashMap<>();
        for (Map<String,Object> r : rows) {
            porCodigo.put(String.valueOf(r.get("Codigo")), r);
        }
        return porCodigo;
    }
	
	
	private String buildDebugSql(String sql, List<Object> params) {
	    String debug = sql;
	    for (Object p : params) {
	        String rep;
	        if (p == null) {
	            rep = "NULL";
	        } else if (p instanceof Number) {
	            rep = p.toString();
	        } else {
	            // texto/fecha: comilla simple y escapar comillas internas
	            rep = "'" + p.toString().replace("'", "''") + "'";
	        }
	        // Reemplaza el primer '?' por el valor, cuidando regex y backrefs
	        debug = debug.replaceFirst("\\?", Matcher.quoteReplacement(rep));
	    }
	    return debug;
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

	private String formatoConParentesis(double valor) {
	    if (valor < 0) {
	        return "(" + String.format("%,.2f", Math.abs(valor)) + ")";
	    } else {
	        return String.format("%,.2f", valor);
	    }
	}
	
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
