package co.gov.sfc.infdinamicos.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import co.gov.sfc.infdinamicos.service.ServicioConsultasAlaBD;



@Controller
public class ControladorReportesAp {
	private final ServicioConsultasAlaBD consultasAlaBD ;

    public ControladorReportesAp(ServicioConsultasAlaBD consultasAlaBD) {
        this.consultasAlaBD = consultasAlaBD;
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

		List<Map<String, Object>> datosReporte = consultasAlaBD.obtenerReporteFinanciero(codigoEntidad, fechaMayor);
		List<Map<String, Object>> estadoResultados = consultasAlaBD.obtenerEstadoResultados(codigoEntidad, fechaMayor);
		
	    LocalDate f = LocalDate.parse(fechaMayor);
	 // Locale Colombia
	    Locale locCo = Locale.forLanguageTag("es-CO");
	    DateTimeFormatter fmtCab = DateTimeFormatter.ofPattern("MMM-yy", locCo);
	 // Cabeceras
	    String cabActual = YearMonth.from(f).atEndOfMonth().format(fmtCab).toLowerCase(locCo);
	    String cabT3     = YearMonth.from(f).minusMonths(3).atEndOfMonth().format(fmtCab).toLowerCase(locCo);
	    String cabT12    = YearMonth.from(f).minusYears(1).atEndOfMonth().format(fmtCab).toLowerCase(locCo);		
        model.addAttribute("datosReporte", datosReporte);
		model.addAttribute("estadoResultados", estadoResultados);
        model.addAttribute("codigoEntidad", codigoEntidad);
        model.addAttribute("fechaMayor", fechaMayor);
        model.addAttribute("cabActual", cabActual);
        model.addAttribute("cabT3", cabT3);
        model.addAttribute("cabT12", cabT12);
        
        return "/reportes/financieros";
	}    
    
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

        List<Map<String, Object>> datosBalance = consultasAlaBD.obtenerBalance(codigoEntidad, fechaMayor);

        String fechaMenor = consultasAlaBD.calcularFechaMenor(fechaMayor);

        model.addAttribute("datosBalance", datosBalance);
        model.addAttribute("codigoEntidad", codigoEntidad);
        model.addAttribute("fechaMayor", fechaMayor);
        model.addAttribute("fechaMenor", fechaMenor);

        return "reportes/cuif";
    }

}    
