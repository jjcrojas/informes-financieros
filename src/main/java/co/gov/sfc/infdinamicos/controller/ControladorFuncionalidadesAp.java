package co.gov.sfc.infdinamicos.controller;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.gov.sfc.infdinamicos.service.ServicioConsultasAlaBD;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/consulta") // Asegura que este path es correcto
public class ControladorFuncionalidadesAp {

	private final ServicioConsultasAlaBD servicioDeConsultas;

	public ControladorFuncionalidadesAp(ServicioConsultasAlaBD servicioDeConsultas) {
		this.servicioDeConsultas = servicioDeConsultas;
	}

	@GetMapping("/test") // Ruta correcta para la prueba
	public List<Map<String, Object>> testConnection() {
		return servicioDeConsultas.obtenerDatosPrueba();
	}

	@GetMapping("/balance-estado-resultados")
	public ResponseEntity<Map<String, Object>> obtenerBalanceYEstadoResultados(
			@RequestParam(name = "codigoEntidad") int codigoEntidad, 
	        @RequestParam(name = "fechaMayor") String fechaMayor) {

	    List<Map<String, Object>> datosReporte = servicioDeConsultas.obtenerReporteFinanciero(codigoEntidad,fechaMayor);
	    List<Map<String, Object>> estadoResultados = servicioDeConsultas.obtenerEstadoResultados(codigoEntidad,fechaMayor);

	    Map<String, Object> response = new HashMap<>();
	    response.put("balance", datosReporte);
	    response.put("estadoResultados", estadoResultados);

	    return ResponseEntity.ok(response);
	}

	
	@GetMapping("/cuif")
	public List<Map<String, Object>> obtenerBalance(@RequestParam(name = "codigoEntidad") int codigoEntidad,
			@RequestParam(name = "fechaMayor") String fechaMayor) {
		return servicioDeConsultas.obtenerBalance(codigoEntidad, fechaMayor);
	}
	
	@GetMapping("/balance/informe_fin")
	public List<Map<String, Object>> obtenerReporteFinanciero(@RequestParam(name = "codigoEntidad") int codigoEntidad,
			@RequestParam(name = "fechaMayor") String fechaMayor) {
		return servicioDeConsultas.obtenerReporteFinanciero(codigoEntidad, fechaMayor);
	}
	
	@GetMapping("/reporte/excel")
    public ResponseEntity<byte[]> descargarInfFinExcel(
            @RequestParam(name = "codigoEntidad") int codigoEntidad,
            @RequestParam(name = "fechaMayor") String fechaMayor) {

        List<Map<String, Object>> datosReporte = servicioDeConsultas.obtenerReporteFinanciero(codigoEntidad, fechaMayor);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Reporte Financiero");

            // Crear el encabezado
            Row headerRow = sheet.createRow(0);
            String[] columnHeaders = {"Nombre Cuenta", "Código PUC", "Valor Actual (Millones)", 
                                      "Valor Anterior (Millones)", "% Participación", "Variación Anual"};
            for (int i = 0; i < columnHeaders.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columnHeaders[i]);
                cell.setCellStyle(createHeaderStyle(workbook));
            }

            // Llenar los datos en el archivo Excel
            int rowNum = 1;
            for (Map<String, Object> fila : datosReporte) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(fila.get("Nombre_Cuenta").toString());
                row.createCell(1).setCellValue(fila.get("Codigo").toString());
                row.createCell(2).setCellValue(Double.parseDouble(fila.get("Valor_Actual_Millones").toString()));
                row.createCell(3).setCellValue(Double.parseDouble(fila.get("Valor_Anterior_Millones").toString()));
                row.createCell(4).setCellValue(Double.parseDouble(fila.get("Porcentaje_Participacion_Actual").toString()));
                row.createCell(5).setCellValue(Double.parseDouble(fila.get("Variacion_Anual").toString()));
            }

            // Ajustar el tamaño de las columnas automáticamente
            for (int i = 0; i < columnHeaders.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Convertir el archivo a bytes
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);

            // Configurar la respuesta HTTP con el archivo Excel
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Disposition", "attachment; filename=Reporte_Financiero.xlsx");
            return new ResponseEntity<>(outputStream.toByteArray(), headers, HttpStatus.OK);

        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle headerStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);
        return headerStyle;
    }


	@GetMapping("/cuif/excel")
	public ResponseEntity<byte[]> descargarReporteExcel(@RequestParam(name = "codigoEntidad") int codigoEntidad,
			@RequestParam(name = "fechaMayor") String fechaMayor) {

		try {
			byte[] excelBytes = servicioDeConsultas.generarReporteExcel(codigoEntidad, fechaMayor);

			return ResponseEntity.ok()
					.header(HttpHeaders.CONTENT_DISPOSITION,
							"attachment; filename=Balance_" + codigoEntidad + "_" + fechaMayor + ".xlsx")
					.contentType(MediaType.APPLICATION_OCTET_STREAM).body(excelBytes);

		} catch (IOException e) {
			return ResponseEntity.internalServerError().build();
		}
	}

}