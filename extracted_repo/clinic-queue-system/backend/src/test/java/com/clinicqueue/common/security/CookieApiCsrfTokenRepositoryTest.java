package com.clinicqueue.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;

import static org.assertj.core.api.Assertions.assertThat;

class CookieApiCsrfTokenRepositoryTest {

    private final CookieApiCsrfTokenRepository repository = new CookieApiCsrfTokenRepository();

    @Test
    void saveWritesCookieAndLoadReadsItBack() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        CsrfToken token = repository.generateToken(request);

        repository.saveToken(token, request, response);

        assertThat(response.getHeader("Set-Cookie")).contains("XSRF-TOKEN=" + token.getToken());
        MockHttpServletRequest next = new MockHttpServletRequest();
        next.setCookies(new jakarta.servlet.http.Cookie("XSRF-TOKEN", token.getToken()));
        CsrfToken loaded = repository.loadToken(next);
        assertThat(loaded.getToken()).isEqualTo(token.getToken());
        assertThat(loaded.getHeaderName()).isEqualTo("X-XSRF-TOKEN");
    }

    @Test
    void loadReturnsNullWhenCookieMissingOrBlank() {
        assertThat(repository.loadToken(new MockHttpServletRequest())).isNull();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie("XSRF-TOKEN", "  "));
        assertThat(repository.loadToken(request)).isNull();
    }

    @Test
    void saveTokenNullClearsCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        repository.saveToken(null, new MockHttpServletRequest(), response);

        assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0");
    }

    @Test
    void generatedTokensAreUnique() {
        assertThat(repository.generateToken(new MockHttpServletRequest()).getToken())
                .isNotEqualTo(repository.generateToken(new MockHttpServletRequest()).getToken());
    }
}
