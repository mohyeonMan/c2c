package com.c2c.c2c.infrastructure.adapter.in.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * 닉네임 유효성 검증기
 * 
 * 클라이언트-서버 간 일관된 닉네임 검증 규칙 적용
 */
public class NicknameValidator implements ConstraintValidator<ValidNickname, String> {
    
    // 한글, 영문, 숫자, 공백, 언더스코어, 하이픈만 허용
    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[가-힣a-zA-Z0-9\\s_-]+$");
    
    // 금지된 닉네임 목록
    private static final String[] FORBIDDEN_NAMES = {
        "admin", "administrator", "system", "root", "null", "undefined", 
        "관리자", "시스템", "운영자", "서버", "bot"
    };

    @Override
    public boolean isValid(String nickname, ConstraintValidatorContext context) {
        if (nickname == null) {
            return false;
        }
        
        String trimmed = nickname.trim();
        
        // 길이 검증
        if (trimmed.length() < 1 || trimmed.length() > 20) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("닉네임은 1-20자여야 합니다")
                   .addConstraintViolation();
            return false;
        }
        
        // 패턴 검증
        if (!NICKNAME_PATTERN.matcher(trimmed).matches()) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("닉네임에는 한글, 영문, 숫자, 공백, _, -만 사용할 수 있습니다")
                   .addConstraintViolation();
            return false;
        }
        
        // 금지된 이름 검증
        String lowerCaseName = trimmed.toLowerCase();
        for (String forbidden : FORBIDDEN_NAMES) {
            if (lowerCaseName.contains(forbidden.toLowerCase())) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("사용할 수 없는 닉네임입니다")
                       .addConstraintViolation();
                return false;
            }
        }
        
        return true;
    }
}