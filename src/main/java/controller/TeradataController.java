package controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teradata") // Asegura que este path es correcto
public class TeradataController {

	private final TeradataService teradataService;

	public TeradataController(TeradataService teradataService) {
		this.teradataService = teradataService;
	}

	@GetMapping("/test") // Ruta correcta para la prueba
	public List<Map<String, Object>> testConnection() {
		return teradataService.obtenerDatosPrueba();
	}

	@GetMapping("/balance")
	public List<Map<String, Object>> obtenerBalance(
	        @RequestParam(name = "codigoEntidad") int codigoEntidad, 
	        @RequestParam(name = "fechaMayor") String fechaMayor) {
	    return teradataService.obtenerBalance(codigoEntidad, fechaMayor);
	}
	
	@GetMapping("/balance/excel")
	public ResponseEntity<byte[]> descargarReporteExcel(
	        @RequestParam(name = "codigoEntidad") int codigoEntidad, 
	        @RequestParam(name = "fechaMayor") String fechaMayor) {

	    try {
	        byte[] excelBytes = teradataService.generarReporteExcel(codigoEntidad, fechaMayor);

	        return ResponseEntity.ok()
	                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Balance_" + codigoEntidad + "_" + fechaMayor + ".xlsx")
	                .contentType(MediaType.APPLICATION_OCTET_STREAM)
	                .body(excelBytes);

	    } catch (IOException e) {
	        return ResponseEntity.internalServerError().build();
	    }
	}

}