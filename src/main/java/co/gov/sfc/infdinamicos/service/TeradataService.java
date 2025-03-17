package co.gov.sfc.infdinamicos.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
public class TeradataService {

    private final JdbcTemplate jdbcTemplate;

    public TeradataService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> obtenerDatosPrueba() {
        String sql = "SELECT TOP 10 * FROM PROD_DWH_CONSULTA.ENTIDADES";
        return jdbcTemplate.queryForList(sql);
    }
    
    public List<Map<String, Object>> obtenerReporteFinanciero(int codigoEntidad, String fechaMayor) {
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
        puc.Codigo,
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
    GROUP BY estfin.Ent_ID, estfin.Puc_ID, puc.Codigo 
)
SELECT 
    puc.Nombre AS Nombre_Cuenta,
    puc.Codigo, 
    COALESCE(ROUND(MAX(CASE 
        WHEN tie.Fecha = ? 
        THEN estfin.Saldo_Sincierre_Total_Moneda_0 / 1000000
    END), 2), 0.00) AS Valor_Actual_Millones,
    COALESCE(ROUND(val_ant.Valor_Anterior / 1000000, 2), 0.00) AS Valor_Anterior_Millones,
    COALESCE(ROUND(MAX(CASE 
        WHEN tie.Fecha = ? 
        THEN (estfin.Saldo_Sincierre_Total_Moneda_0 / act2024.Valor_Activo) * 100
    END), 1), 0.0) AS Porcentaje_Participacion_Actual,
    COALESCE(ROUND(CASE 
        WHEN val_ant.Valor_Anterior IS NULL OR val_ant.Valor_Anterior = 0 
        THEN 0  
        ELSE ((MAX(CASE WHEN tie.Fecha = ? THEN estfin.Saldo_Sincierre_Total_Moneda_0 END) - val_ant.Valor_Anterior) 
              / val_ant.Valor_Anterior) * 100
    END, 1), 0.0) AS Variacion_Anual
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
    AND (
        (puc.Codigo = 100000)  
        OR (puc.Codigo = 110000)  
        OR (puc.Codigo = 130000)  
        OR (puc.Codigo = 160000)  
        OR (puc.Codigo = 180000)  
        OR (puc.Codigo = 190000)  
        OR (puc.Codigo = 200000)  
        OR (puc.Codigo = 210000)  
        OR (puc.Codigo = 220000)  
        OR (puc.Codigo = 243500)
        OR (puc.Codigo = 250000)
        OR (puc.Codigo = 270000)
        OR (puc.Codigo = 280000)
        OR (puc.Codigo = 290000)
        OR (puc.Codigo = 300000)
        OR (puc.Codigo = 310000)
        OR (puc.Codigo = 320000)
        OR (puc.Codigo = 380000)
        OR (puc.Codigo = 390500)
        OR (puc.Codigo = 391000)
        OR (puc.Codigo = 391500)
    )
    AND (tie.Fecha IS NOT NULL OR estfin.Puc_ID IS NULL) -- Asegurar que se incluyan cuentas sin valores
GROUP BY puc.Nombre, puc.Codigo, val_ant.Valor_Anterior
ORDER BY puc.Codigo
        """;

        return jdbcTemplate.queryForList(sql, codigoEntidad, 
        		fechaMayor, fechaMenor, codigoEntidad, fechaMayor, 
        		fechaMayor, fechaMayor, fechaMayor, codigoEntidad, fechaMayor, fechaMenor);
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

        return jdbcTemplate.queryForList(sql, codigoEntidad, fechaMayor, 
        		fechaMenor, codigoEntidad, fechaMayor, fechaMayor, 
        		codigoEntidad, fechaMayor, fechaMenor);
    }

    // Método auxiliar para calcular la fecha menor (tres meses antes)
    private String calcularFechaMenor(String fechaMayor) {
        LocalDate fecha = LocalDate.parse(fechaMayor);
        LocalDate fechaMenor = fecha.minusMonths(3);
        return fechaMenor.toString();
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
