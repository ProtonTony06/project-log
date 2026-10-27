package com.projectlog.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projectlog.ingest.IngestAuthenticationEntryPoint;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * D1 (C8) — charset del 401: ambos entry points emiten su cuerpo JSON en
 * UTF-8 (@code setCharacterEncoding("UTF-8") ANTES de @code getWriter(),
 * D-C8-14). Unit puro SIN Spring context: la API pública de ambos handlers
 * ignora request y exception (sólo usan response), así que se invocan con
 * @code (null, resp, null) y un @code ObjectMapper real.
 *
 * <p>Asertos byte-exactos por clase (R8.45/R8.46/R8.48 — E8.48/E8.49):
 * (a) Content-Type compuesto con @code charset=UTF-8; (b) los bytes del
 * acento llegan como UTF-8 multibyte y JAMÁS como byte Latin-1 aislado
 * (@code ó = 0xC3 0xB3 en el panel; @code á = 0xC3 0xA1 en ingesta;
 * nunca 0xF3 aislado); (c) relectura UTF-8 sin @code U+FFFD.</p>
 */
class EntryPointCharsetTest {

    /** Byte 0xF3: 'ó' de Latin-1 — JAMÁS puede aparecer aislado en UTF-8. */
    private static final byte LATIN1_O = (byte) 0xF3;

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Nested
    @DisplayName("401 del panel (RestAuthenticationEntryPoint)")
    class Panel401 {

        @Test
        void el401SaleEnUtf8ConHeaderCharsetYLaOCorrecta() throws Exception {
            RestAuthenticationEntryPoint ep =
                    new RestAuthenticationEntryPoint(new ObjectMapper());
            MockHttpServletResponse resp = new MockHttpServletResponse();

            ep.commence(null, resp, null);

            assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            // (a) header con charset UTF-8 (Tomcat real compone idéntico)
            assertThat(resp.getContentType())
                    .isEqualTo("application/json;charset=UTF-8");
            assertThat(resp.getCharacterEncoding())
                    .isEqualToIgnoringCase("UTF-8");

            byte[] bytes = resp.getContentAsByteArray();
            String hex = hex(bytes);
            // (b) 'ó' de "Autenticación requerida" = UTF-8 0xC3 0xB3 ...
            assertThat(hex).contains("c3b3");
            assertThat(hex).doesNotContain("f3"); // ... nunca Latin-1 aislado
            // (c) relectura UTF-8 SIN U+FFFD (mojibake) y con la 'ó' literal
            String cuerpo = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            assertThat(cuerpo).doesNotContain("�");
            assertThat(cuerpo).contains("Autenticación requerida");
            assertThat(cuerpo).contains("\"code\":\"invalid_token\"");
        }

        @Test
        void accessDeniedHandlerMismoCaminoMismoCharset() throws Exception {
            // denyAll() de las cadenas 1 y 3 escribe por handle(...) (CD-1):
            // mismo cuerpo y mismo charset que commence(...).
            RestAuthenticationEntryPoint ep =
                    new RestAuthenticationEntryPoint(new ObjectMapper());
            MockHttpServletResponse resp = new MockHttpServletResponse();

            ep.handle(null, resp, null);

            assertThat(resp.getContentType())
                    .isEqualTo("application/json;charset=UTF-8");
            assertThat(hex(resp.getContentAsByteArray())).contains("c3b3");
            assertThat(hex(resp.getContentAsByteArray())).doesNotContain("f3");
        }
    }

    @Nested
    @DisplayName("401 de ingesta (IngestAuthenticationEntryPoint) — simetría")
    class Ingesta401 {

        @Test
        void el401DeIngestaSaleEnUtf8ConLaACorrecta() throws Exception {
            IngestAuthenticationEntryPoint ep =
                    new IngestAuthenticationEntryPoint(new ObjectMapper());
            MockHttpServletResponse resp = new MockHttpServletResponse();

            ep.commence(null, resp, null);

            assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
            // (a) header con charset UTF-8 (idempéndico D1 — simetría R8.46)
            assertThat(resp.getContentType())
                    .isEqualTo("application/json;charset=UTF-8");
            assertThat(resp.getCharacterEncoding())
                    .isEqualToIgnoringCase("UTF-8");

            byte[] bytes = resp.getContentAsByteArray();
            String hex = hex(bytes);
            // (b) 'á' de "API key de ingesta ausente o inválida" = UTF-8 0xC3 0xA1
            assertThat(hex).contains("c3a1");
            assertThat(hex).doesNotContain("f3");
            // (c) relectura UTF-8 SIN U+FFFD
            String cuerpo = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            assertThat(cuerpo).doesNotContain("�");
            assertThat(cuerpo).contains("API key de ingesta ausente o inválida");
            assertThat(cuerpo).contains("\"code\":\"api_key_invalid\"");
        }
    }
}