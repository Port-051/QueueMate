package com.queuemate.platform.common.security;

import com.queuemate.platform.common.error.ApiException;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * {@link CurrentUserId} 를 채운다 — 보안 필터가 검증해 둔 access 토큰({@link Jwt})의 {@code sub} 를 {@code Long} 으로 판다.
 * 자기 자신을 MVC 에 등록한다({@link WebMvcConfigurer}) — 클래스 하나로 끝내려는 것이다.
 *
 * <p>{@code sub} 는 사용자 번호의 십진 문자열이다({@code TokenClaims#SUBJECT_PATTERN}). 검증기가 이미 그 꼴만 통과시키므로 여기서 못 파는 일은
 * 없어야 하지만, 못 팔면 500 이 아니라 401 {@code UNAUTHENTICATED} 다 — 토큰이 이상한 것이지 서버가 고장 난 것이 아니다.
 */
@Component
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver, WebMvcConfigurer {

    @Override
    public boolean supportsParameter(MethodParameter parameter)
    {
        return parameter.hasParameterAnnotation(CurrentUserId.class);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory)
    {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt))
        {
            // permitAll 경로 — 로그인한 사람이 없다
            return null;
        }
        try
        {
            return Long.parseLong(jwt.getSubject());
        }
        catch(NumberFormatException e)
        {
            throw ApiException.unauthenticated();
        }
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers)
    {
        resolvers.add(this);
    }
}
