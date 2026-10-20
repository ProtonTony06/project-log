package com.projectlog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada del backend Project_Log.
 *
 * <p>Esqueleto del corte C1 (fase 0 de pila): sin lógica de negocio. La raíz
 * del paquete {@code com.projectlog} es el nivel que escanea los 8 paquetes
 * package-by-feature del TRD §2.1.</p>
 */
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}