package controller;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ReporteController {
	private final TeradataService teradataService ;

    public ReporteController(TeradataService teradataService) {
        this.teradataService = teradataService;
    }
    
    @GetMapping("/reportes/financieros/datos")
    public String obtenerDatosInFFin(
            @RequestParam(name = "codigoEntidad") int codigoEntidad,
            @RequestParam(name = "fechaMayor") String fechaMayor,
            Model model) {

        List<Map<String, Object>> datosReporte = teradataService.obtenerReporteFinanciero(codigoEntidad, fechaMayor);
        model.addAttribute("datosReporte", datosReporte);
        model.addAttribute("codigoEntidad", codigoEntidad);
        model.addAttribute("fechaMayor", fechaMayor);

        return "reportes/financieros"; 
    }
    
	@GetMapping("/reportes/financieros")
    public String mostrarFormularioInfFin(Model model) {
        return "reportes/financieros"; // Nombre del archivo en /templates/reportes/financieros.html
    } 
    
    @GetMapping("/reportes/balance")
    public String mostrarFormularioBalance(Model model) {
        return "reportes/balance"; // Nombre del archivo en /templates/reportes/balance.html
    }

    
    @GetMapping("/reportes/balance/datos")
    public String obtenerDatosBalance(
            @RequestParam(name = "codigoEntidad") int codigoEntidad,
            @RequestParam(name = "fechaMayor") String fechaMayor,
            Model model) {

		List<Map<String, Object>> datosBalance = teradataService.obtenerBalance(codigoEntidad, fechaMayor);
        model.addAttribute("datosBalance", datosBalance);
        model.addAttribute("codigoEntidad", codigoEntidad);
        model.addAttribute("fechaMayor", fechaMayor);

        return "reportes/balance";
    }
}    
