package com.shopping.integration_service.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorsFilter implements Filter {

    @Value("${cors.allowed-origins:http://localhost:4200,http://localhost:4201,https://shopping-admin-production.up.railway.app,https://shopping-customer-production.up.railway.app}")
    private String allowedOriginsRaw;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        String origin = request.getHeader("Origin");

        // Build allowed origins set from comma-separated property
        Set<String> allowed = new HashSet<>(Arrays.asList(
                allowedOriginsRaw.split(",")
        ));

        if (origin != null && allowed.contains(origin.trim())) {
            response.setHeader("Access-Control-Allow-Origin",      origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Allow-Methods",     "GET,POST,PUT,DELETE,OPTIONS");
            response.setHeader("Access-Control-Allow-Headers",     "Authorization,Content-Type");
            response.setHeader("Access-Control-Max-Age",           "3600");
        }

        // Respond immediately to preflight OPTIONS requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        chain.doFilter(req, res);
    }
}
