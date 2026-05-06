package com.cs.exception;

import java.io.IOException;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ControllerAdvice
public class GlobalControllerExceptionHandler {

    @ExceptionHandler(Exception.class)
    public void handleGeneral(HttpServletRequest request, 
                               HttpServletResponse response, 
                               Exception ex) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.getWriter().write("""
            <!DOCTYPE html>
            <html>
            <head><title>Error | CodeSync</title>
            <style>
                body { background:#060910; color:#f1f5f9; font-family:sans-serif;
                       display:flex; align-items:center; justify-content:center; min-height:100vh; }
                .card { background:#111827; border:1px solid rgba(255,255,255,0.06);
                        border-radius:16px; padding:3rem; max-width:480px; text-align:center; }
                .code { font-size:5rem; font-weight:900; color:#f87171; letter-spacing:-3px; }
                .desc { color:#64748b; margin:1rem 0 2rem; line-height:1.6; }
                a { display:inline-block; padding:.75rem 1.75rem; background:#38bdf8;
                    color:#060910; border-radius:8px; text-decoration:none; font-weight:600; }
            </style></head>
            <body>
                <div class="card">
                    <div class="code">500</div>
                    <h2>Something went wrong</h2>
                    <p class="desc">An unexpected error occurred. Please try again or contact support.</p>
                    <a href="/admin/dashboard">Back to Dashboard</a>
                </div>
            </body>
            </html>
        """);
    }
}