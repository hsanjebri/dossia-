package com.example.dossia.chat;

import com.example.dossia.config.ChatRateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Caps anonymous POST /api/v1/chat calls per client IP to limit scrapers beyond the guest trial UX.
 */
@Component
public class GuestChatRateLimitFilter extends OncePerRequestFilter {

    private final ChatRateLimitProperties properties;
    private final Map<String, Deque<Long>> hitsByIp = new ConcurrentHashMap<>();

    public GuestChatRateLimitFilter(ChatRateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!properties.enabled()) {
            return true;
        }
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return path == null || !path.equals("/api/v1/chat");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        if (isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = clientIp(request);
        long now = Instant.now().getEpochSecond();
        long windowStart = now - properties.guestWindowSeconds();
        Deque<Long> hits = hitsByIp.computeIfAbsent(ip, ignored -> new ArrayDeque<>());

        synchronized (hits) {
            while (!hits.isEmpty() && hits.peekFirst() < windowStart) {
                hits.pollFirst();
            }
            if (hits.size() >= properties.guestMaxRequests()) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter()
                        .write(
                                """
                                {"status":429,"error":"Too Many Requests","message":"Guest chat rate limit reached. Sign in or try again later.","timestamp":"%s"}
                                """
                                        .formatted(Instant.now())
                                        .trim());
                return;
            }
            hits.addLast(now);
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
