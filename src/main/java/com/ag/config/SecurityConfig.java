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

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

	@Autowired
	private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

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

				long start = System.currentTimeMillis();

				try {
					filterChain.doFilter(wrappedRequest, response);
				} finally {
					logRequest(wrappedRequest, response, start);
				}
			}
		};
	}

	/**
	 * Extracts and logs request details.
	 */
	private void logRequest(ContentCachingRequestWrapper request, HttpServletResponse response, long startTime) {

		String method = request.getMethod();
		String uri = request.getRequestURI();
		String query = request.getQueryString();
		String clientIp = getClientIp(request);

		String body = "";
		byte[] buf = request.getContentAsByteArray();
		if (buf.length > 0) {
			try {
				body = new String(buf, 0, buf.length, request.getCharacterEncoding());
			} catch (Exception ignored) {
			}
		}

		long duration = System.currentTimeMillis() - startTime;

		CodeSyncLogger.logInfo("SECURITY FILTER | " + method + " " + uri + (query != null ? "?" + query : "") + " | IP="
				+ clientIp + " | Status=" + response.getStatus() + " | Time=" + duration + "ms"
				+ (body.isEmpty() ? "" : " | Body=" + body));
	}

	/**
	 * Resolves real client IP (supports reverse proxy).
	 */
	private String getClientIp(HttpServletRequest request) {
		String xfHeader = request.getHeader("X-Forwarded-For");
		if (xfHeader != null && !xfHeader.isEmpty()) {
			return xfHeader.split(",")[0];
		}
		return request.getRemoteAddr();
	}
}
