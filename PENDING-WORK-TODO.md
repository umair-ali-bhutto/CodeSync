

fix in other htmls
<a th:href="@{/admin/dashboard}">Go to Dashboard</a>



fix this as well 
package com.cs.config;

import java.io.IOException;
import java.io.Serializable;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Custom Spring Security authentication entry point used to handle unauthorized
 * access attempts.
 * <p>
 * Instead of returning the default JSON or plain 401 response, this class
 * returns a user-friendly HTML error page explaining how to correctly access
 * the application endpoints.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint, Serializable {

	private static final long serialVersionUID = -7858869558953243875L;

	/**
	 * Triggered automatically by Spring Security when an unauthenticated user
	 * attempts to access a protected resource.
	 * <p>
	 * This method delegates the response rendering to
	 * {@link CodeSyncUtil#getHtmlErrorPage(HttpServletResponse)} to return a custom
	 * HTML error page with usage instructions.
	 *
	 * @param request       the incoming HTTP request
	 * @param response      the HTTP response to be written
	 * @param authException the exception that caused the authentication failure
	 * @throws IOException if writing the response fails
	 */
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {

		String acceptHeader = request.getHeader("Accept");
		String requestedWith = request.getHeader("X-Requested-With");
		String uri = request.getRequestURI();

		// ✅ Don't redirect if already on login page — breaks the loop
		boolean isLoginPage = uri.contains("/login");

		boolean isBrowserRequest = acceptHeader != null && acceptHeader.contains("text/html")
				&& !"XMLHttpRequest".equals(requestedWith);

		boolean isApiRequest = uri.startsWith("/api/") || uri.startsWith("/logsService") || uri.startsWith("/share/");

		if (isBrowserRequest && !isApiRequest && !isLoginPage) {
			response.sendRedirect(request.getContextPath() + "/login");
		} else {
			CodeSyncUtil.getHtmlErrorPage(response);
		}
	}

}



































Now that the dependencies are resolved, you can configure Resilience4j patterns (like **Circuit Breaker**, **Retry**, and **Rate Limiter**) using your `application.yml` and use them in your code via annotations.

### 1. Configuration (`src/main/resources/application.yml`)
You define "instances" of your resilience patterns. For example, if you have a service that calls an external API, you might name that instance `externalApiService`.

```yaml
resilience4j:
  # 1. Circuit Breaker Configuration
  circuitbreaker:
    instances:
      externalApiService:
        registerHealthIndicator: true
        slidingWindowSize: 10              # Look at last 10 calls
        failureRateThreshold: 50           # Open circuit if 50% fail
        waitDurationInOpenState: 10000ms   # Wait 10s before retrying
        permittedNumberOfCallsInHalfOpenState: 3

  # 2. Retry Configuration
  retry:
    instances:
      externalApiService:
        maxAttempts: 3                     # Try 3 times total
        waitDuration: 1000ms               # Wait 1s between tries
        retryExceptions:
          - org.springframework.web.client.HttpServerErrorException
          - java.io.IOException

  # 3. Rate Limiter Configuration
  ratelimiter:
    instances:
      externalApiService:
        limitForPeriod: 5                  # 5 requests
        limitRefreshPeriod: 10s            # Every 10 seconds
        timeoutDuration: 500ms             # Wait 0.5s for a slot before failing
```

---

### 2. Using it in your Code
You use simple annotations on your Service methods. 



**Important:** The `fallbackMethod` must have the same signature as the original method, plus a `Throwable` (or specific Exception) parameter at the end.

```java
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import org.springframework.stereotype.Service;

@Service
public class MyService {

    // You can stack annotations. Order matters! 
    // (Usually: Retry -> CircuitBreaker -> RateLimiter)
    @Retry(name = "externalApiService", fallbackMethod = "commonFallback")
    @CircuitBreaker(name = "externalApiService", fallbackMethod = "commonFallback")
    @RateLimiter(name = "externalApiService", fallbackMethod = "rateLimitFallback")
    public String callExternalData(String param) {
        // Your logic to call an external API or DB
        // If this throws an exception, Resilience4j handles it.
        return "Success from External Service with " + param;
    }

    // Fallback for general failures (Circuit Breaker / Retry)
    public String commonFallback(String param, Throwable t) {
        return "Fallback: Service is currently unavailable. Error: " + t.getMessage();
    }

    // Specific fallback for Rate Limiting
    public String rateLimitFallback(String param, Throwable t) {
        return "Slow down! You are making too many requests.";
    }
}
```

---

### 3. Monitoring (Actuator)
Since you added the `actuator` and `micrometer` dependencies, you can see the state of your circuit breakers by visiting:
*   **Health Status:** `http://localhost:8080/actuator/health`
*   **Detailed Metrics:** `http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.calls`

### Pro-Tips:
*   **Method Visibility:** The `@CircuitBreaker` and other annotations work via AOP (Spring Proxies). This means they only work when called from **outside** the class. Calling a method with an annotation from another method in the same class will bypass the resilience logic.
*   **Naming:** Always ensure the `name` in the annotation matches the `instance` name in your `.yml` file.

Which of these patterns is most critical for your **CodeSync** app right now—limiting how fast users can share code, or handling database timeouts?











Looking at your code, I need to understand your Thymeleaf setup and error template structure first. Let me then refactor `GlobalControllerExceptionHandler` to use Thymeleaf properly and handle all error cases (404, 403, 400, 500, etc.).

Here's the complete solution:

**1. Update `GlobalControllerExceptionHandler.java`**

```java
package com.cs.exception;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ControllerAdvice
public class GlobalControllerExceptionHandler {

    // ── 404 Not Found ──────────────────────────────────────────────────────────
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(HttpServletRequest request, Model model) {
        return error(model, 404, "Page Not Found",
                "The page you're looking for doesn't exist or has been moved.");
    }

    // ── 403 Access Denied (browser requests only) ──────────────────────────────
    // NOTE: API 403s are handled by SecurityConfig's accessDeniedHandler
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDenied(HttpServletRequest request, Model model) {
        return error(model, 403, "Access Denied",
                "You don't have permission to access this resource.");
    }

    // ── 401 Unauthorized ───────────────────────────────────────────────────────
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public String handleUnauthorized(HttpServletRequest request, Model model) {
        return error(model, 401, "Unauthorized",
                "You must be logged in to access this page.");
    }

    // ── 400 Bad Request ────────────────────────────────────────────────────────
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(HttpServletRequest request, Model model) {
        return error(model, 400, "Bad Request",
                "The request could not be understood or was missing required parameters.");
    }

    // ── ShareNotFoundException → 404 ───────────────────────────────────────────
    @ExceptionHandler(ShareNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleShareNotFound(ShareNotFoundException ex, Model model) {
        return error(model, 404, "Share Not Found", ex.getMessage());
    }

    // ── 500 Catch-all ──────────────────────────────────────────────────────────
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGeneral(HttpServletRequest request,
                                HttpServletResponse response,
                                Exception ex,
                                Model model) {
        return error(model, 500, "Something Went Wrong",
                "An unexpected error occurred. Please try again or contact support.");
    }

    // ── Helper ─────────────────────────────────────────────────────────────────
    private String error(Model model, int code, String title, String description) {
        model.addAttribute("errorCode", code);
        model.addAttribute("errorTitle", title);
        model.addAttribute("errorDescription", description);
        model.addAttribute("backUrl", resolveBackUrl(code));
        return "error"; // → templates/error.html
    }

    private String resolveBackUrl(int code) {
        return switch (code) {
            case 401 -> "/login";
            case 403 -> "/admin/dashboard";
            default  -> "/admin/dashboard";
        };
    }
}
```

**2. `GlobalExceptionHandler.java` — add a browser-check guard**

The two `@ControllerAdvice` / `@RestControllerAdvice` beans can conflict on `Exception.class`. Guard the REST handler so it only fires for API (non-browser) requests:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ShareNotFoundException.class)
    public ResponseEntity<String> handleNotFound(ShareNotFoundException ex,
                                                  HttpServletRequest request) {
        if (isBrowserRequest(request)) return null; // let ControllerAdvice handle it
        CodeSyncLogger.logError(ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadKey(IllegalArgumentException ex,
                                                HttpServletRequest request) {
        if (isBrowserRequest(request)) return null;
        CodeSyncLogger.logError(ex.getMessage(), ex);
        return ResponseEntity.badRequest().body("Invalid Share Key");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGeneric(Exception ex,
                                                 HttpServletRequest request) {
        if (isBrowserRequest(request)) return null;
        CodeSyncLogger.logError("Unhandled error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                             .body("Internal server error");
    }

    private boolean isBrowserRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }
}
```

**3. `templates/error.html`**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8"/>
    <title th:text="|Error ${errorCode} | CodeSync|">Error | CodeSync</title>
    <style>
        *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

        body {
            background: #060910;
            color: #f1f5f9;
            font-family: 'Segoe UI', system-ui, sans-serif;
            display: flex;
            align-items: center;
            justify-content: center;
            min-height: 100vh;
        }

        .card {
            background: #111827;
            border: 1px solid rgba(255,255,255,0.06);
            border-radius: 16px;
            padding: 3rem;
            max-width: 480px;
            width: 90%;
            text-align: center;
            box-shadow: 0 24px 48px rgba(0,0,0,0.4);
        }

        /* colour per status code */
        .code {
            font-size: 5rem;
            font-weight: 900;
            letter-spacing: -3px;
            line-height: 1;
        }
        .code-4xx { color: #facc15; } /* yellow  — client error */
        .code-5xx { color: #f87171; } /* red     — server error */

        h2 { margin: 1rem 0 0.25rem; font-size: 1.4rem; }

        .desc {
            color: #64748b;
            margin: 0.5rem 0 2rem;
            line-height: 1.6;
        }

        .btn {
            display: inline-block;
            padding: .75rem 1.75rem;
            background: #38bdf8;
            color: #060910;
            border-radius: 8px;
            text-decoration: none;
            font-weight: 600;
            transition: opacity .2s;
        }
        .btn:hover { opacity: .85; }
    </style>
</head>
<body>
<div class="card">

    <!-- Colour class: 5xx = red, 4xx = yellow -->
    <div class="code"
         th:classappend="${errorCode >= 500} ? 'code-5xx' : 'code-4xx'"
         th:text="${errorCode}">500</div>

    <h2 th:text="${errorTitle}">Something Went Wrong</h2>

    <p class="desc" th:text="${errorDescription}">
        An unexpected error occurred.
    </p>

    <a class="btn" th:href="${backUrl}">
        <span th:text="${errorCode == 401} ? 'Go to Login' : 'Back to Dashboard'">
            Back to Dashboard
        </span>
    </a>

</div>
</body>
</html>
```

**4. `application.properties` — enable 404 forwarding**

Spring Boot swallows `NoHandlerFoundException` by default. Add these two lines:

```properties
spring.mvc.throw-exception-if-no-handler-found=true
spring.web.resources.add-mappings=false
```

> ⚠️ `add-mappings=false` disables the default static-resource handler. If you serve static files, keep a `WebMvcConfigurer` that registers your own `ResourceHandlerRegistry` mapping instead.

**5. `/access-denied` controller route** (used by your `SecurityConfig`)

```java
@Controller
public class ErrorController {

    @GetMapping("/access-denied")
    public String accessDenied(Model model) {
        model.addAttribute("errorCode", 403);
        model.addAttribute("errorTitle", "Access Denied");
        model.addAttribute("errorDescription",
                "You don't have permission to access this resource.");
        model.addAttribute("backUrl", "/admin/dashboard");
        return "error";
    }
}
```

---

**How the routing now works:**

| Request type | Error | Handler |
|---|---|---|
| Browser (`text/html`) | Any | `GlobalControllerExceptionHandler` → `error.html` |
| API (`/api/**`) | 4xx/5xx | `GlobalExceptionHandler` → JSON `ResponseEntity` |
| Security — 403 browser | Access denied | `SecurityConfig` → redirects to `/access-denied` → same `error.html` |
| Security — 401 API | Not authenticated | `jwtAuthenticationEntryPoint` → JSON |
| 404 (no route) | `NoHandlerFoundException` | `GlobalControllerExceptionHandler` → `error.html` |