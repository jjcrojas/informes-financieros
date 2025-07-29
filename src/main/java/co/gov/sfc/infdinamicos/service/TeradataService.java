package co.gov.sfc.infdinamicos.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import co.gov.sfc.infdinamicos.model.CuentaEstadoResultados;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TeradataService {

    private final JdbcTemplate jdbcTemplate;
    
    @Autowired
    private EstadoResultadosService estadoResultadosService;

    public TeradataService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> obtenerDatosPrueba() {
        String sql = "SELECT TOP 10 * FROM PROD_DWH_CONSULTA.ENTIDADES";
        return jdbcTemplate.queryForList(sql);
    }
    

    public List<Map<String, Object>> obtenerEstadoResultados(int codigoEntidad,String fecha) {
        List<CuentaEstadoResultados> cuentas = estadoResultadosService.obtenerCuentasEstadoResultados();

        Map<String, List<String>> gruposCuentas = cuentas.stream()
                .collect(Collectors.groupingBy(CuentaEstadoResultados::getGrupo, 
                        Collectors.mapping(CuentaEstadoResultados::getCuenta, Collectors.toList())));

        List<Map<String, Object>> resultadoFinal = new ArrayList<>();

        for (Map.Entry<String, List<String>> grupo : gruposCuentas.entrySet()) {
            String listaCuentas = grupo.getValue().stream()
                    .map(c -> "'" + c + "'")
                    .collect(Collectors.joining(", "));

            String query = "SELECT '" + grupo.getKey() + "' AS Grupo, " +
                           "SUM(ef.Saldo_Sincierre_Total_Moneda_0) AS total_saldo " +
                           "FROM prod_dwh_consulta.estfin_indiv ef " +
                           "JOIN prod_dwh_consulta.TIEMPO T ON T.Tie_ID = ef.Tie_ID " +
                           "JOIN prod_dwh_consulta.ENTIDADES E ON E.Ent_ID = ef.Ent_ID " +
                           "JOIN prod_dwh_consulta.PUC P ON P.Puc_ID = ef.Puc_ID " +
                           "WHERE T.Fecha = '" + fecha + "' " +
                           "AND E.Tipo_Entidad = 23 " +
                           "AND E.Codigo_Entidad = " + codigoEntidad + " " +
                           "AND ef.Tipo_Informe = 0 " +
                           "AND P.codigo IN (" + listaCuentas + ")";

            List<Map<String, Object>> resultadoQuery = jdbcTemplate.queryForList(query);
            resultadoFinal.addAll(resultadoQuery);
        }

        return resultadoFinal;
    }

    
    public List<Map<String, Object>> obtenerReporteFinanciero(int codigoEntidad, String fechaMayor) {
        String fechaMenor = calcularFechaMenor(fechaMayor);

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
              AND puc.Codigo IN (
                    100000, 110000, 130000, 160000, 180000, 190000, 200000, 210000,
                    220000, 243500, 250000, 270000, 280000, 290000, 300000, 310000,
                    320000, 380000, 390500, 391000, 391500
              )
              AND (tie.Fecha IS NOT NULL OR estfin.Puc_ID IS NULL)
            GROUP BY puc.Nombre, puc.Codigo, val_ant.Valor_Anterior
            ORDER BY puc.Codigo
        """;

        System.out.println("QUERY:\n" + sql);

        return jdbcTemplate.queryForList(
            sql,
            codigoEntidad,       // Para act (fecha mayor)
            fechaMayor,
            fechaMenor,          // Para valores anteriores
            codigoEntidad,
            fechaMayor,          // Para Valor_Actual_Millones
            fechaMayor,          // Para Porcentaje_Participacion_Actual
            fechaMayor,          // Para Variacion_Anual
            fechaMayor,          // Para act join
            codigoEntidad,       // Para filtro WHERE
            fechaMayor, fechaMenor
        );
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
        
        return jdbcTemplate.queryForList(sql, codigoEntidad, fechaMayor, 
        		fechaMenor, codigoEntidad, fechaMayor, fechaMayor, 
        		codigoEntidad, fechaMayor, fechaMenor);

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
        String[] columnas = {"Nombre Cuenta", "Clase", "Grupo", "Cuenta", "Subcuenta", "Valor Actual (Millones)", "Valor Anterior (Millones)"};

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
