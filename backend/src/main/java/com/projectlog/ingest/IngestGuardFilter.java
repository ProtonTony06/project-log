package com.projectlog.ingest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;
import com.projectlog.keys.ApiKeyService;
import com.projectlog.projects.dto.ProjectDto;
import com.projectlog.projects.repository.ProjectsRepository;

/**
 * C5 (R5.4, S5-12, D-1/D-5/D-6/D-8): guard ÚNICO de la superficie de
 * ingesta {@code /api/v1/ingest(/**)} — el orden contractual del Schema
 * §3.4 se resuelve en el FILTRO, antes de cualquier código MVC
 * (un delegante al service sería inversión de capas y el parse del body
 * del controller llegaría tarde — D-1):
 *
 * <ol>
 *   <li><b>Barrera 1 — TAMAÑO (413)</b>: {@code Content-Length} presente y
 *       &gt; límite → 413 sin tocar el cuerpo ni continuar (R-12: no
 *       drena; plan B del spec §2.6 solo si el smoke manifestase reset —
 *       el cuerpo/code del 413 no cambian).</li>
 *   <li><b>Barrera 2 — 413 chunked</b>: SIEMPRE se continúa con un
 *       {@code HttpServletRequestWrapper} cuyo {@code getInputStream()}
 *       cuenta bytes y, al cruzar el límite (frontera EXACTA: {@code
 *       limite} bytes LEGAL, {@code limite+1} NO), lanza {@link
 *       CuerpoDemasiadoGrandeException} — una <b>{@code RuntimeException}</b>
 *       por obligación (D-5): si fuese {@code IOException}, Jackson la
 *       envolvería en {@code HttpMessageNotReadableException} y el 413 se
 *       degradaría a 400. El catch del propio guard (alrededor de
 *       {@code chain.doFilter}) es el ÚNICO capturador.</li>
 *   <li><b>Autenticación (401)</b>: header {@code X-API-Key} →
 *       {@code ApiKeyService.sha256(header)} (String COMPLETO — sin trim
 *       de {@code plk_}, sin hex, sin salt; contrato C4 §0.2) →
 *       {@code ProjectsRepository.findByApiKeyHash(hash)} → con match, el
 *       {@code IngestPrincipal(projectId, status)} viaja como
 *       {@code Authentication} (patrón {@code JwtAuthFilter}); sin
 *       header/malformada/sin match/revocada → NO escribe respuesta y NO
 *       setea contexto: la regla {@code authenticated()} fallará y el
 *       {@code IngestAuthenticationEntryPoint} escribirá el 401
 *       {@code api_key_invalid}.</li>
 * </ol>
 *
 * <p><b>D-C5-1 (gate 2026-10-25)</b>: la igualdad de la key la resuelve el
 * {@code WHERE api_key_hash = ?} del SELECT por igualdad — SIN
 * {@code tiempoConstanteIgual} post-lookup (el {@code ProjectDto}/COLS no
 * porta el hash; divergencia declarada y ratificada en gate; R5.16).</p>
 *
 * <p>D-8 (shouldNotFilter): los beans {@code Filter} se auto-registran en
 * la servlet chain GLOBAL de Boot (como {@code JwtAuthFilter}, allí
 * benigno); el guard SOLO filtra {@code /api/v1/ingest} y
 * {@code /api/v1/ingest/**} — fuera de ese rango corre sin envoltorio ni
 * barreras (sin fugas 413/lookup al panel ni al ws). Higiene (R5.15): la
 * clase NO contiene logger y jamás loguea el header, la key ni el hash.</p>
 */
@Component
public class IngestGuardFilter extends OncePerRequestFilter {

    /** 413 (Schema §3.4; literal FIJO — no compone el valor configurado). */
    private static final String CODE_PAYLOAD_TOO_LARGE = "payload_too_large";
    private static final String MSG_PAYLOAD_TOO_LARGE =
            "La petición supera el límite de 1 MiB";

    // ObjecProvider RATIFICADO en gate (2026-10-25): los slices @WebMvcTest
    // de C1–C4 escanean este @Component sin tener ApiKeyService/
    // ProjectsRepository — con providers la construcción es posible en
    // cualquier contexto (resolución VACÍA en slices; en PROD los beans
    // existen) y la resolución efectiva es LAZY: solo al hacer request a
    // /api/v1/ingest/** (doFilterInternal).
    private final ObjectProvider<ApiKeyService> apiKeyServiceProvider;
    private final ObjectProvider<ProjectsRepository> projectsRepositoryProvider;
    private final ObjectMapper objectMapper;

    /** Límite de la petición en bytes, convertido UNA VEZ (fail-fast, D-6/S5-9). */
    private final long limite;

    public IngestGuardFilter(ObjectProvider<ApiKeyService> apiKeyServiceProvider,
            ObjectProvider<ProjectsRepository> projectsRepositoryProvider,
            ObjectMapper objectMapper,
            @Value("${projectlog.ingest.max-request-bytes:1MiB}") String limiteTexto) {
        this.apiKeyServiceProvider = apiKeyServiceProvider;
        this.projectsRepositoryProvider = projectsRepositoryProvider;
        this.objectMapper = objectMapper;
        this.limite = limiteBytes(limiteTexto);
    }

    /**
     * Normalizador del bind R-6 (D-6, cierre S5-9): el literal de
     * {@code INGEST_MAX_REQUEST_BYTES} es {@code 1MiB} (TRD §6.3,
     * {@code .env.example} INTOCABLE) y {@code DataSize.parse("1MiB")} de
     * spring-core 6.2.19 FALLA — el sufijo {@code iB} se normaliza quitando
     * la {@code i} ({@code 1MiB→1MB}, {@code 1KiB→1KB}, {@code 1GiB→1GB},
     * {@code 1TiB→1TB}; dígitos puros y {@code …B} quedan); los KB/MB/GB de
     * Spring SON binarios (1024-base): {@code DataSize.parse("1MB")} →
     * 1048576. Literal no parseable → excepción de arranque (fail-fast).
     * Package-private para el unit E5.34 (sin Spring).
     */
    static long limiteBytes(String limiteTexto) {
        String literal = limiteTexto;
        if (literal.endsWith("iB")) {
            literal = literal.substring(0, literal.length() - 2) + "B";
        }
        return DataSize.parse(literal).toBytes();
    }

    /** D-8/S5-12(a): el guard SOLO cubre el rango del matcher de la cadena 1. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !(uri.equals("/api/v1/ingest") || uri.startsWith("/api/v1/ingest/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // — Barrera 1 (R5.4.1): Content-Length > límite → 413 SIN leer el cuerpo.
        long contentLength = request.getContentLengthLong();
        if (contentLength >= 0 && contentLength > limite) {
            escribir413(response);
            return;
        }

        // — Barrera 2 (R5.4.2): envoltorio SIEMPRE presente; el conteo de
        // bytes es EL ÚNICO camino de lectura del cuerpo (determinismo R-13).
        HttpServletRequest envuelto = new HttpServletRequestWrapper(request) {
            @Override
            public ServletInputStream getInputStream() throws IOException {
                return new StreamContado(request.getInputStream());
            }

            @Override
            public BufferedReader getReader() throws IOException {
                // un solo conteo posible por diseño: getReader pasa por aquí
                return new BufferedReader(new InputStreamReader(
                        getInputStream(), StandardCharsets.UTF_8));
            }
        };

        // — Autenticación (R5.4.3): sin credencial válida → continuar VACÍO
        // (patrón JwtAuthFilter); el entry point escribirá el 401. Los
        // providers se resuelven AQUÍ (lazy: solo con request a la
        // superficie — gate 2026-10-25).
        String header = request.getHeader("X-API-Key");
        if (header != null) {
            byte[] hash = apiKeyServiceProvider.getObject().sha256(header);
            ProjectDto proyecto = projectsRepositoryProvider.getObject()
                    .findByApiKeyHash(hash)
                    .orElse(null);
            if (proyecto != null) {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new IngestPrincipal(proyecto.id(), proyecto.status()),
                                null, List.of()));
            }
        }

        // — Continuar SIEMPRE con el envoltorio (R5.4.4); los catches capturan
        // EXCLUSIVAMENTE la barrera 2 (nunca enmascaran otras; D-5).
        //
        // HALLAZGO del smoke E2E E5.28 (traza Tomcat + bytecode, 2026-10-25):
        // FrameworkServlet.processRequest (spring-webmvc 6.2.19) envuelve
        // cualquier Throwable que NO es ServletException/IOException — la
        // CuerpoDemasiadoGrandeException incluida — en
        // jakarta.servlet.ServletException("Request processing failed: …",
        // causa) ANTES de que la pila llegue de vuelta al guard, por lo que
        // el catch de tipo exacto nunca la ve en el camino HTTP: la
        // excepción escapaba hasta Tomcat, el error dispatch "/error" caía
        // en la cadena 3 y el chunked &gt;1MiB devolvía 401 en vez de 413.
        // Los tres catches siguientes DESENVUELVEN la barrera 2 de la
        // cadena de causas (el tipo de la excepción NO cambia: sigue siendo
        // RuntimeException, D-5) y escriben el mismo 413
        // {payload_too_large}; sin barrera 2 en la causa, re-lanzan la
        // excepción recibida INTACTA (cero enmascaramiento).
        try {
            filterChain.doFilter(envuelto, response);
        } catch (CuerpoDemasiadoGrandeException ex) {
            escribir413SiToca(response);
        } catch (ServletException ex) {
            if (contieneBarrera2(ex)) {
                escribir413SiToca(response);
            } else {
                throw ex;
            }
        } catch (IOException ex) {
            if (contieneBarrera2(ex)) {
                escribir413SiToca(response);
            } else {
                throw ex;
            }
        } catch (RuntimeException ex) {
            if (contieneBarrera2(ex)) {
                escribir413SiToca(response);
            } else {
                throw ex;
            }
        }
    }

    /** 413 (R5.9) solo si la respuesta aún no fue commitida. */
    private void escribir413SiToca(HttpServletResponse response)
            throws IOException {
        if (!response.isCommitted()) {
            escribir413(response);
        }
    }

    /**
     * Verdadero SÍ la cadena de causas (máx. 8 niveles: el envoltorio de
     * FrameworkServlet añade 1) contiene la barrera 2 (D-5). Tipo exacto,
     * sin reflejos.
     */
    private static boolean contieneBarrera2(Throwable ex) {
        Throwable causa = ex;
        for (int nivel = 0; causa != null && nivel < 8;
                causa = causa.getCause(), nivel++) {
            if (causa instanceof CuerpoDemasiadoGrandeException) {
                return true;
            }
        }
        return false;
    }

    /** 413 (R5.9): JSON sin oráculo de causa — sin field, sin index. */
    private void escribir413(HttpServletResponse response) throws IOException {
        response.setStatus(413);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                Map.of("code", CODE_PAYLOAD_TOO_LARGE, "message", MSG_PAYLOAD_TOO_LARGE));
    }

    /**
     * D-5: excepción de la barrera 2, ESTÁTICA anidada y
     * {@code extends RuntimeException} POR OBLIGACIÓN — una {@code
     * IOException} sería envuelta por Jackson en
     * {@code HttpMessageNotReadableException} y el 413 se degradaría a 400.
     * El guard es su ÚNICO capturador (catch específico de tipo).
     */
    static class CuerpoDemasiadoGrandeException extends RuntimeException {

        private static final long serialVersionUID = 1L;
    }

    /**
     * Decorador propio de conteo (R5.2: commons-io NO está en el classpath).
     * Sin caché ni doble lectura: el body se lee UNA vez (por el parser de
     * Jackson); el filtro nunca lo consume. Frontera EXACTA: {@code limite}
     * bytes LEGAL; el byte {@code limite+1} dispara
     * {@link CuerpoDemasiadoGrandeException}.
     */
    private class StreamContado extends ServletInputStream {

        private final ServletInputStream original;
        private long contador = 0;

        StreamContado(ServletInputStream original) {
            this.original = original;
        }

        @Override
        public int read() throws IOException {
            int leido = original.read();
            if (leido != -1 && ++contador > limite) {
                throw new CuerpoDemasiadoGrandeException();
            }
            return leido;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = original.read(b, off, len);
            if (n > 0 && (contador += n) > limite) {
                throw new CuerpoDemasiadoGrandeException();
            }
            return n;
        }

        @Override
        public int available() throws IOException {
            return original.available();
        }

        @Override
        public boolean isFinished() {
            return original.isFinished();
        }

        @Override
        public boolean isReady() {
            return original.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException(
                    "sin async en la superficie de ingesta (R5.4)");
        }
    }
}