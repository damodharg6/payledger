package com.payledger.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Answers "authenticated, but not allowed" with a 403 RFC 7807 problem body.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private static final String BODY = """
            {"type":"about:blank","title":"Forbidden","status":403,\
            "detail":"You are not allowed to do this with your credentials"}""";

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(BODY);
    }
}
