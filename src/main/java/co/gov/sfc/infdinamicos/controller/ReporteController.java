package co.gov.sfc.infdinamicos.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import co.gov.sfc.infdinamicos.service.TeradataService;



@Controller
public class ReporteController {
	private final TeradataService teradataService ;

    public ReporteController(TeradataService teradataService) {
        this.teradataService = teradataService;
    }
    
    //Pide código de entidad y fecha de Estado de Resultados
    @GetMapping("/reportes/financieros-estado-resultados")
    public String mostrarFormularioBalanceYEstadoResultados(Model model) {
        return "reportes/financieros"; // Nombre del archivo en /templates/reportes/balance.html
    }

    /**/
    //Muestra datos de Estado de Resultados según código de entidad y fecha
	@GetMapping("/reportes/financieros-estado-resultados/datos")
	public String obtenerDatosBalanceYEstadoResultados(
            @RequestParam(name = "codigoEntidad") int codigoEntidad,
            @RequestParam(name = "fechaMayor") String fechaMayor,
            Model model) {

		List<Map<String, Object>> datosReporte = teradataService.obtenerReporteFinanciero(codigoEntidad, fechaMayor);
		List<Map<String, Object>> estadoResultados = teradataService.obtenerEstadoResultados(codigoEntidad, fechaMayor);
        model.addAttribute("datosReporte", datosReporte);
		model.addAttribute("estadoResultados", estadoResultados);
        model.addAttribute("codigoEntidad", codigoEntidad);
        
        String fechaMenorStr = teradataService.calcularFechaMenor(fechaMayor);  
        LocalDate fechaMenor = LocalDate.parse(fechaMenorStr); // Convertir a LocalDate
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM-yy", new Locale("es"));
        String fechaMenorFormateada = fechaMenor.format(formatter);
        model.addAttribute("fechaMenorFormateada", fechaMenorFormateada);
        
        LocalDate fecha = LocalDate.parse(fechaMayor);
        String fechaFormateada = fecha.format(formatter);
        model.addAttribute("fechaFormateada", fechaFormateada); // nuevo atributo
        return "/reportes/financieros";
	}    
    /**/
    
    
	/*
	 * @GetMapping("/reportes/financieros/datos") public String obtenerDatosInFFin(
	 * 
	 * @RequestParam(name = "codigoEntidad") int codigoEntidad,
	 * 
	 * @RequestParam(name = "fechaMayor") String fechaMayor, Model model) {
	 * 
	 * List<Map<String, Object>> datosReporte =
	 * teradataService.obtenerReporteFinanciero(codigoEntidad, fechaMayor);
	 * model.addAttribute("datosReporte", datosReporte);
	 * model.addAttribute("codigoEntidad", codigoEntidad);
	 * model.addAttribute("fechaMayor", fechaMayor);
	 * 
	 * return "reportes/financieros"; }
	 */
    
    //
	/*
	 * @GetMapping("/reportes/financieros") public String
	 * mostrarFormularioInfFin(Model model) { return "reportes/financieros"; //
	 * Nombre del archivo en /templates/reportes/financieros.html }
	 */
    
	//Pide código de entidad y fecha
    @GetMapping("/reportes/cuif")
    public String mostrarFormularioBalance(Model model) {
        return "reportes/cuif"; // Nombre del archivo en /templates/reportes/balance.html
    }

    //Muestra datos según código de entidad y fecha
    @GetMapping("/reportes/cuif/datos")
    public String obtenerDatosBalance(
            @RequestParam(name = "codigoEntidad") int codigoEntidad,
            @RequestParam(name = "fechaMayor") String fechaMayor,
            Model model) {

        List<Map<String, Object>> datosBalance = teradataService.obtenerBalance(codigoEntidad, fechaMayor);

        String fechaMenor = teradataService.calcularFechaMenor(fechaMayor);

        model.addAttribute("datosBalance", datosBalance);
        model.addAttribute("codigoEntidad", codigoEntidad);
        model.addAttribute("fechaMayor", fechaMayor);
        model.addAttribute("fechaMenor", fechaMenor);

        return "reportes/cuif";
    }

}    
