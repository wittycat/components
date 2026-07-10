package com.wittycat.components.sca.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by chenxun.
 * Date: 2026/7/10 22:59
 * Description:
 */
@Component
public class JwtUtil {
    /**
     统一放到nacos

     jwt:
         secret: YTMyYml0c2xvbmdzZWNyZXRrZXlhYmMxMjM0NTY3ODl4eXphYmN4eXo
         expire: 7200000
         header: Authorization
         prefix: Bearer
     */

//    @Value("${jwt.secret}")
    private String secretStr="YTMyYml0c2xvbmdzZWNyZXRrZXlhYmMxMjM0NTY3ODl4eXphYmN4eXo";
//    @Value("${jwt.expire}")
    private Long expireTime=7200000L;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        byte[] decode = Base64.getDecoder().decode(secretStr);
        secretKey = Keys.hmacShaKeyFor(decode);
    }

    // 生成token，登录接口调用
    public String createToken(String userId, Map<String, Object> claims) {
        long now = System.currentTimeMillis();
        Date exp = new Date(now + expireTime);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userId)
                .setIssuedAt(new Date(now))
                .setExpiration(exp)
                .signWith(secretKey)
                .compact();
    }

    public String createToken(String userId) {
        return createToken(userId, new HashMap<>());
    }

    // 解析载荷
    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // 校验token
    public boolean verify(String token) {
        try {
            getClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
        } catch (SignatureException e) {
        } catch (MalformedJwtException e) {
        } catch (Exception e) {
        }
        return false;
    }

    // 获取用户ID
    public String getUserId(String token) {
        return getClaims(token).getSubject();
    }
}