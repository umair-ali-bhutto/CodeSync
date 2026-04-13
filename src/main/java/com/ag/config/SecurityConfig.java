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
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import com.ag.entity.CodeSyncAudit;
import com.ag.service.CodeSyncAuditService;
import com.ag.service.CodeSyncClientCache;

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

		http.addFilterBefore(requestLoggingFilter(), UsernamePasswordAuthenticationFilter.class).csrf()
				.ignoringAntMatchers("/api/**", "/logsService").and().authorizeRequests()

				// PUBLIC: API
				.antMatchers("/api/share/*").permitAll()

				// PUBLIC: only /share/{key}
				.antMatchers("/share/*").permitAll()

				// For Logs
				.antMatchers("/logsService").permitAll()

				// For Admin Dashboard
				.antMatchers("/admin/dashboard", "/admin/dashboard/download").permitAll()

				// EVERYTHING ELSE
				.anyRequest().authenticated().and().exceptionHandling()
				.authenticationEntryPoint(jwtAuthenticationEntryPoint).and().sessionManagement()
				.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED);

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

				String uri = request.getRequestURI();

				if (uri.startsWith("/codesync/share/")) {

					String key = uri.substring(uri.lastIndexOf("/") + 1);

					// invalid cases
					if (key.isEmpty() || key.length() > 100 || key.contains("/")) {
						// Trick Spring Security into calling JwtAuthenticationEntryPoint
						jwtAuthenticationEntryPoint.commence(request, response,
								new org.springframework.security.authentication.BadCredentialsException(
										"Invalid Share Key"));
						logAndAudit(wrappedRequest, response, clientIp, start);
						return;
					}
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

		String bodyLog = (body.length() <= 50000) ? " | Body=" + body : " | Body too large not logging";

		String clientName = CodeSyncClientCache.getNameByIp(clientIp);

		CodeSyncLogger.logInfo("SECURITY FILTER | " + method + " " + uri + (query != null ? "?" + query : "")
				+ " | Client=" + clientName + " | IP=" + clientIp + " | browserInfo=" + browserInfo + " | Lang="
				+ language + " | Ref=" + referer + " | Status=" + response.getStatus() + " | Time=" + duration
				+ "ms | content size: " + body.length() + "" + bodyLog);

		codeSyncAuditService.saveSafely(log);

	}

	/**
	 * Resolves real client IP (supports reverse proxy).
	 */
	private String getClientIp(HttpServletRequest request) {

		// 1️⃣ X-Forwarded-For (may contain multiple IPs)
		String xff = request.getHeader("X-Forwarded-For");
		if (CodeSyncUtil.isIpValid(xff)) {
			String ip = xff.split(",")[0].trim();
			CodeSyncLogger.logInfo("Client IP from X-Forwarded-For: " + ip);
			return ip;
		}

		// 2️⃣ X-Real-IP (Nginx)
		String xRealIp = request.getHeader("X-Real-IP");
		if (CodeSyncUtil.isIpValid(xRealIp)) {
			CodeSyncLogger.logInfo("Client IP from X-Real-IP: " + xRealIp);
			return xRealIp;
		}

		// 3️⃣ Forwarded (RFC 7239)
		String forwarded = request.getHeader("Forwarded");
		if (CodeSyncUtil.isIpValid(forwarded)) {
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
