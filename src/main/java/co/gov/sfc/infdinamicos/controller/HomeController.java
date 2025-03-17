package co.gov.sfc.infdinamicos.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class HomeController {
    
    @GetMapping
    public String home() {
        return "Bienvenido a la aplicación de reportes financieros!";
    }
}
