package com.kimsooin77.sync;
import com.kimsooin77.sync.api.auth.CsrfResponse;
import com.kimsooin77.sync.api.auth.LoginRequest;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;

public record AdminHttpSession(RestClient client, RestClient noCsrfClient, CsrfResponse csrf,
                               String sessionIdBeforeLogin, String sessionIdAfterLogin) {
    public static AdminHttpSession login(String baseUrl) {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient transport = HttpClient.newBuilder().cookieHandler(cookies).build();
        RestClient client = RestClient.builder().baseUrl(baseUrl)
                .requestFactory(new JdkClientHttpRequestFactory(transport)).build();
        CsrfResponse beforeLogin = client.get().uri("/api/auth/csrf").retrieve().body(CsrfResponse.class);
        String beforeSession = sessionId(cookies);
        var login = client.post().uri("/api/auth/login").header(beforeLogin.headerName(), beforeLogin.token())
                .body(new LoginRequest("test-admin", "password")).retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> { }).toEntity(String.class);
        if (!login.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("test administrator login failed: " + login.getStatusCode()
                    + " " + login.getBody());
        }
        CsrfResponse afterLogin = client.get().uri("/api/auth/csrf").retrieve().body(CsrfResponse.class);
        RestClient csrfClient = RestClient.builder().baseUrl(baseUrl)
                .requestFactory(new JdkClientHttpRequestFactory(transport))
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().set(afterLogin.headerName(), afterLogin.token());
                    return execution.execute(request, body);
                }).build();
        return new AdminHttpSession(csrfClient, client, afterLogin, beforeSession, sessionId(cookies));
    }
    private static String sessionId(CookieManager cookies) {
        return cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("JSESSIONID"))
                .map(java.net.HttpCookie::getValue).findFirst().orElse(null);
    }
}
