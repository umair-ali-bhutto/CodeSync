package com.ag.config;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import com.ag.entity.CodeSyncAudit;
import com.ag.service.CodeSyncAuditService;

import io.github.bucket4j.Bucket;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

	@Autowired
	private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

	@Autowired
	private CodeSyncAuditService codeSyncAuditService;

	@Autowired
	private SecurityProtectionConfig protectionConfig;

	@Override
	protected void configure(HttpSecurity http) throws Exception {

		http.addFilterBefore(requestLoggingFilter(),
				org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)

				.csrf().disable()

				.authorizeRequests()

				// PUBLIC: API
				.antMatchers("/api/share/**").permitAll()

				// PUBLIC: only /share/{key}
				.antMatchers("/share/**").permitAll()

				.antMatchers("/logsService").permitAll()

				// EVERYTHING ELSE
				.anyRequest().authenticated()

				.and().exceptionHandling().authenticationEntryPoint(jwtAuthenticationEntryPoint)

				.and().sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
	}

	/**
	 * Logs every incoming HTTP request at security layer.
	 */
	private OncePerRequestFilter requestLoggingFilter() {

		return new OncePerRequestFilter() {

			@Override
			protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
					FilterChain filterChain) throws ServletException, IOException {

				ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);

				String clientIp = getClientIp(request);
				long start = System.currentTimeMillis();

				// 🚫 IP BLOCKING
				if (protectionConfig.isBlocked(clientIp)) {
					response.setStatus(HttpServletResponse.SC_FORBIDDEN);
					response.getWriter().write("Sorry IP blocked");
					logAndAudit(wrappedRequest, response, clientIp, start);
					return;
				}

				// ⏱ RATE LIMIT
				Bucket bucket = protectionConfig.resolveBucket(clientIp);
				if (!bucket.tryConsume(1)) {
					response.setStatus(429);
					response.getWriter().write("Too many requests");
					logAndAudit(wrappedRequest, response, clientIp, start);
					return;
				}

				try {
					filterChain.doFilter(wrappedRequest, response);
				} catch (Exception e) {
					CodeSyncLogger.logError(getClass(), "FILTER", e);
				} finally {
					logAndAudit(wrappedRequest, response, clientIp, start);
				}
			}
		};
	}

	/**
	 * Extracts and logs request details and inserts it into audit.
	 */
	private void logAndAudit(ContentCachingRequestWrapper request, HttpServletResponse response, String clientIp,
			long startTime) {
		String method = request.getMethod();
		String uri = request.getRequestURI();
		String query = request.getQueryString();

		String body = "";
		byte[] buf = request.getContentAsByteArray();
		if (buf.length > 0) {
			try {
				body = new String(buf, request.getCharacterEncoding());
			} catch (Exception ignored) {
			}
		}

		long duration = System.currentTimeMillis() - startTime;

		CodeSyncAudit log = new CodeSyncAudit();
		log.setHttpMethod(method);
		log.setUri(uri);
		log.setQueryString(query);
		log.setClientIp(clientIp);
		log.setStatusCode(response.getStatus());
		log.setContentSize(body.length());
		log.setRequestBody(body);
		log.setDurationMs(duration);
		log.setForwardedFor(request.getHeader("X-Forwarded-For"));
		log.setRealIp(request.getHeader("X-Real-IP"));

		codeSyncAuditService.saveSafely(log);

		String bodyLog = (body.length() <= 10000) ? " | Body=" + body : " | Body too large not logging";

		CodeSyncLogger.logInfo("SECURITY FILTER | " + method + " " + uri + (query != null ? "?" + query : "") + " | IP="
				+ clientIp + " | Status=" + response.getStatus() + " | Time=" + duration + "ms | content size: "
				+ body.length() + "" + bodyLog);
	}

	/**
	 * Resolves real client IP (supports reverse proxy).
	 */
	private String getClientIp(HttpServletRequest request) {

		// 1️⃣ X-Forwarded-For (may contain multiple IPs)
		String xff = request.getHeader("X-Forwarded-For");
		if (isValid(xff)) {
			String ip = xff.split(",")[0].trim();
			CodeSyncLogger.logInfo("Client IP from X-Forwarded-For: " + ip);
			return ip;
		}

		// 2️⃣ X-Real-IP (Nginx)
		String xRealIp = request.getHeader("X-Real-IP");
		if (isValid(xRealIp)) {
			CodeSyncLogger.logInfo("Client IP from X-Real-IP: " + xRealIp);
			return xRealIp;
		}

		// 3️⃣ Forwarded (RFC 7239)
		String forwarded = request.getHeader("Forwarded");
		if (isValid(forwarded)) {
			// Example: for=192.168.1.10;proto=https
			for (String part : forwarded.split(";")) {
				if (part.trim().startsWith("for=")) {
					String ip = part.replace("for=", "").replace("\"", "").trim();
					CodeSyncLogger.logInfo("Client IP from Forwarded: " + ip);
					return ip;
				}
			}
		}

		// 4️⃣ Fallback
		String remoteAddr = request.getRemoteAddr();
		CodeSyncLogger.logInfo("Client IP from request.getRemoteAddr(): " + remoteAddr);
		return remoteAddr;
	}

	private boolean isValid(String value) {
		return value != null && !value.isEmpty() && !"unknown".equalsIgnoreCase(value);
	}
}
