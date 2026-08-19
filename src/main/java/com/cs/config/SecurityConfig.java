package com.cs.config;

import static org.springframework.security.config.Customizer.withDefaults;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.cs.entity.CodeSyncAudit;
import com.cs.service.CodeSyncAuditService;
import com.cs.service.CodeSyncClientCache;
import com.cs.util.CodeSyncUtil;

import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Autowired
	private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

	@Autowired
	private CodeSyncAuditService codeSyncAuditService;

	@Autowired
	private SecurityProtectionConfig protectionConfig;

	@Value("${dashboard.admin.username}")
	private String adminUsername;

	@Value("${dashboard.admin.password}")
	private String adminPassword;

	@Value("${dashboard.user.username}")
	private String userUsername;

	@Value("${dashboard.user.password}")
	private String userPassword;

	@Value("${spring.h2.console.path:/h2-console}")
	private String h2ConsolePath;

	/**
	 * Main Security Filter Chain - Modern Spring Security 6.x approach
	 */
	@SuppressWarnings("unused")
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		String h2ConsolePattern = h2ConsolePath.endsWith("/**") ? h2ConsolePath : h2ConsolePath + "/**";

		http.addFilterBefore(requestLoggingFilter(), UsernamePasswordAuthenticationFilter.class)
				.csrf(csrf -> csrf
						.ignoringRequestMatchers("/api/**", "/logsService", "/actuator/**", "/login", h2ConsolePattern))
				.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
				.authorizeHttpRequests(auth -> auth

						// Public API's and Logs
						.requestMatchers("/api/share/*", "/api/files/**", "/share/*", "/logsService").permitAll()

						// Static resources
						.requestMatchers("/images/**", "/css/**", "/js/**").permitAll()

						// CRITICAL: login page must be explicitly permitted
						.requestMatchers("/login", "/login/**").permitAll()

						// For ERROR PAGE
						.requestMatchers("/access-denied", "/blocked-ip").permitAll()

						// Secure Actuator
						.requestMatchers("/actuator/**").hasRole("ADMIN")

						// Admin Dashboard
						.requestMatchers("/admin/dashboard").authenticated()
						.requestMatchers("/admin/dashboard/download").hasRole("ADMIN")
						.requestMatchers("/admin/dashboard/status").hasRole("ADMIN")
						.requestMatchers("/admin/ip-management/**").hasRole("ADMIN")

						// H2 Console
						.requestMatchers(h2ConsolePattern).hasRole("ADMIN")

						// swagger
						.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").hasRole("ADMIN")

						// EVERYTHING ELSE
						.anyRequest().authenticated())
				.exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint)
						.accessDeniedHandler((request, response, accessDeniedException) -> {
							String acceptHeader = request.getHeader("Accept");
							boolean isBrowser = acceptHeader != null && acceptHeader.contains("text/html");

							if (isBrowser) {
								response.sendRedirect(request.getContextPath() + "/access-denied");
							} else {
								response.setStatus(HttpServletResponse.SC_FORBIDDEN);
								response.setContentType("application/json");
								response.getWriter().write("{\"error\":\"Access Denied\",\"status\":403}");
							}
						}))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
				// Enable form login for dashboard authentication
				.formLogin(form -> form.loginPage("/login").loginProcessingUrl("/login")
						.defaultSuccessUrl("/admin/dashboard", false).permitAll())
				// Logout configuration
				.logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout")
						.invalidateHttpSession(true).clearAuthentication(true).deleteCookies("JSESSIONID").permitAll())

				// Enable HTTP Basic authentication (useful for API testing)
				.httpBasic(withDefaults());

		return http.build();
	}

	/**
	 * Password Encoder Bean - BCrypt for secure password hashing
	 */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/**
	 * In-Memory User Details Service for Dashboard Authentication
	 * 
	 * Credentials are loaded from application.properties
	 * 
	 */
	@Bean
	public UserDetailsService userDetailsService() {
		UserDetails admin = User.builder().username(adminUsername).password(passwordEncoder().encode(adminPassword))
				.roles("ADMIN").build();

		UserDetails user = User.builder().username(userUsername).password(passwordEncoder().encode(userPassword))
				.roles("USER").build();

		return new InMemoryUserDetailsManager(admin, user);
	}

	/**
	 * Logs every incoming HTTP request at security layer. Converted to Bean to
	 * avoid deprecation warnings
	 */
	@Bean
	public OncePerRequestFilter requestLoggingFilter() {

		return new OncePerRequestFilter() {

			@Override
			protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
					FilterChain filterChain) throws ServletException, IOException {

				String uri = request.getRequestURI();
				if (uri.endsWith("/blocked-ip")) {
					filterChain.doFilter(request, response);
					return;
				}

				boolean isDownloadAll = uri.endsWith("/download-all");

				ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request, 0);
				HttpServletResponse wrappedResponse = isDownloadAll ? response
						: new ContentCachingResponseWrapper(response);

				String clientIp = CodeSyncUtil.getClientIp(request);
				long start = System.currentTimeMillis();

				// 🚫 IP BLOCKING
				if (protectionConfig.isBlocked(clientIp)) {
					String blockId = CodeSyncUtil.generateBlockRef(clientIp);

					wrappedRequest.setAttribute("blockedIp", clientIp);
					wrappedRequest.setAttribute("blockId", blockId);

					logAndAudit(wrappedRequest, wrappedResponse, clientIp, start, "blockId: " + blockId);

					request.setAttribute("blockedIp", clientIp);
					request.setAttribute("blockId", blockId);
					request.getRequestDispatcher("/blocked-ip").forward(request, response);
					return;
				}

				if (uri.startsWith(request.getContextPath() + "/share/")) {

					String key = uri.substring(uri.lastIndexOf("/") + 1);

					// invalid cases
					if (key.isEmpty() || key.length() > 100 || key.contains("/")) {
						logAndAudit(wrappedRequest, wrappedResponse, clientIp, start, "Invalid Share Key");

						// Trick Spring Security into calling JwtAuthenticationEntryPoint
						jwtAuthenticationEntryPoint.commence(request, response,
								new org.springframework.security.authentication.BadCredentialsException(
										"Invalid Share Key"));

						return;
					}
				}

				// ⏱ RATE LIMIT
				Bucket bucket = protectionConfig.resolveBucket(clientIp);
				if (!bucket.tryConsume(1)) {
					wrappedResponse.setStatus(429);
					wrappedResponse.getWriter().write("Too many requests");
					logAndAudit(wrappedRequest, wrappedResponse, clientIp, start, "Too many requests");
					return;
				}

				try {
					filterChain.doFilter(wrappedRequest, wrappedResponse);
				} catch (Exception e) {
					CodeSyncLogger.logError(getClass(), "FILTER Exception", e);
				} finally {
					logAndAudit(wrappedRequest, wrappedResponse, clientIp, start, "");
				}
			}
		};
	}

	/**
	 * Extracts and logs request details and inserts it into audit.
	 */
	private void logAndAudit(ContentCachingRequestWrapper request, HttpServletResponse response, String clientIp,
			long startTime, String additionalData) throws IOException {
		String method = request.getMethod();
		String uri = request.getRequestURI();
		String query = request.getQueryString();

		String userAgent = request.getHeader("User-Agent");
		String language = request.getHeader("Accept-Language");
		String referer = request.getHeader("Referer");
		String origin = request.getHeader("Origin");
		String host = request.getHeader("Host");

		String secChUa = request.getHeader("Sec-CH-UA");
		String secChUaPlatform = request.getHeader("Sec-CH-UA-Platform");
		String secChUaMobile = request.getHeader("Sec-CH-UA-Mobile");

		String secFetchSite = request.getHeader("Sec-Fetch-Site");
		String secFetchMode = request.getHeader("Sec-Fetch-Mode");
		String secFetchDest = request.getHeader("Sec-Fetch-Dest");

		String body = "";
		byte[] buf = request.getContentAsByteArray();
		if (buf.length > 0) {
			try {
				body = new String(buf, request.getCharacterEncoding());
			} catch (Exception ignored) {
			}
		}

		String responseBody = "";
		if (response instanceof ContentCachingResponseWrapper cachedResponse) {
			byte[] responseBuf = cachedResponse.getContentAsByteArray();
			if (responseBuf.length > 0) {
				try {
					responseBody = new String(responseBuf, cachedResponse.getCharacterEncoding());
				} catch (Exception e) {
					CodeSyncLogger.logError(getClass(), "Exception", e);
				}
			}
		}

		long duration = System.currentTimeMillis() - startTime;

		String browserInfo = parseClientInfo(userAgent);

		CodeSyncAudit log = new CodeSyncAudit();
		log.setHttpMethod(method);
		log.setUri(CodeSyncUtil.validateKeyWithoutException(uri));
		log.setQueryString(query);
		log.setClientIp(clientIp);
		log.setStatusCode(response.getStatus());
		log.setContentSize(body.length());
		log.setRequestBody(body);
		log.setDurationMs(duration);
		log.setForwardedFor(request.getHeader("X-Forwarded-For"));
		log.setRealIp(request.getHeader("X-Real-IP"));
		log.setUserAgent(userAgent);
		log.setBrowserInfo(browserInfo);
		log.setLanguage(language);
		log.setReferer(referer);
		log.setOrigin(origin);
		log.setHost(host);
		log.setSecFetchSiteModeDest(secFetchSite + " | " + secFetchMode + " | " + secFetchDest);
		log.setSecChUaPlatformMobile(secChUa + " | " + secChUaPlatform + " | " + secChUaMobile);
		log.setAdditionalInfo(additionalData);

		String bodyLog = (body.length() <= 50000) ? " | Body=" + body : " | Body too large not logging";
		String responseLog = (responseBody.length() <= 50000) ? " | Response=" + responseBody
				: " | Response too large, not logging";

		String clientName = CodeSyncClientCache.getNameByIp(clientIp);

		Long uploadedFileSize = null;
		String uploadedFileName = "";
		try {
			uploadedFileSize = (Long) request.getAttribute("uploadedFileSize");
			uploadedFileName = (String) request.getAttribute("uploadedFileName");
		} catch (Exception e) {
			CodeSyncLogger.logError(getClass(), "Exception", e);
		}

		String uploadInfo = "";
		if (uploadedFileSize != null) {
			uploadInfo = " | UploadedFile=" + uploadedFileName + " | UploadSize=" + uploadedFileSize + " bytes" + " ("
					+ String.format("%.2f", uploadedFileSize / (1024.0 * 1024.0)) + " MB)";

			log.setUploadedFileName(uploadedFileName);
			log.setUploadedFileSize(uploadedFileSize);
		}

		CodeSyncLogger
				.logInfo("SECURITY FILTER | " + method + " " + uri + uploadInfo + (query != null ? "?" + query : "")
						+ " | Client=" + clientName + " | IP=" + clientIp + " | browserInfo=" + browserInfo + " | Lang="
						+ language + " | Ref=" + referer + " | Status=" + response.getStatus() + " | Time=" + duration
						+ "ms | content size: " + body.length() + "" + bodyLog + responseLog);

		codeSyncAuditService.saveSafely(log);

		if (response instanceof ContentCachingResponseWrapper cachedResponse) {
			cachedResponse.copyBodyToResponse();
		}

	}

	private String parseClientInfo(String userAgent) {
		if (userAgent == null || userAgent.isEmpty())
			return "Unknown";

		userAgent = userAgent.toLowerCase();

		/* ================= DEVICE / OS ================= */

		String os = "Unknown OS";

		if (userAgent.contains("windows nt 10"))
			os = "Windows 10/11";
		else if (userAgent.contains("windows nt 6.3"))
			os = "Windows 8.1";
		else if (userAgent.contains("windows nt 6.2"))
			os = "Windows 8";
		else if (userAgent.contains("windows nt 6.1"))
			os = "Windows 7";
		else if (userAgent.contains("mac os x"))
			os = "Mac OS";
		else if (userAgent.contains("iphone"))
			os = "iPhone iOS";
		else if (userAgent.contains("ipad"))
			os = "iPad iOS";
		else if (userAgent.contains("android"))
			os = "Android";
		else if (userAgent.contains("linux"))
			os = "Linux";
		else if (userAgent.contains("cros"))
			os = "Chrome OS";

		/* ================= BROWSER ================= */

		String browser = "Unknown Browser";

		if (userAgent.contains("edg/"))
			browser = "Edge (Chromium)";
		else if (userAgent.contains("opr/") || userAgent.contains("opera"))
			browser = "Opera";
		else if (userAgent.contains("chrome/") && !userAgent.contains("edg/") && !userAgent.contains("opr/"))
			browser = "Chrome";
		else if (userAgent.contains("firefox/"))
			browser = "Firefox";
		else if (userAgent.contains("safari/") && !userAgent.contains("chrome/"))
			browser = "Safari";
		else if (userAgent.contains("trident") || userAgent.contains("msie"))
			browser = "Internet Explorer";

		/* ================= CLIENT TYPE ================= */

		String clientType = "Browser";

		if (userAgent.contains("postman"))
			clientType = "Postman";
		else if (userAgent.contains("curl"))
			clientType = "Curl";
		else if (userAgent.contains("okhttp"))
			clientType = "Android App (OkHttp)";
		else if (userAgent.contains("java"))
			clientType = "Java Client";
		else if (userAgent.contains("python"))
			clientType = "Python Script";
		else if (userAgent.contains("wget"))
			clientType = "Wget";
		else if (userAgent.contains("bot") || userAgent.contains("spider") || userAgent.contains("crawler"))
			clientType = "Bot/Crawler";

		/* ================= DEVICE TYPE ================= */

		String device = "Desktop";
		if (userAgent.contains("mobile"))
			device = "Mobile";
		if (userAgent.contains("ipad") || userAgent.contains("tablet"))
			device = "Tablet";

		return " os=" + os + " | browser=" + browser + " | device=" + device + " | clientType=" + clientType;
	}

}
