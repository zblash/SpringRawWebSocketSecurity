package com.example.demo.configs;

import com.example.demo.JWTValidator;
import com.example.demo.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class WebSocketJWTValidator {

    @Autowired
    private JWTValidator jwtValidator;

    public Principal setPrincipal(ServerHttpRequest request){
        String url = request.getURI().toString();
        Pattern p = Pattern.compile("[&?]token=([^&\\r\\n]*)");
        Matcher matcher = p.matcher(url);
        if (matcher.find())
        {
            Optional<User> optionalUser = Optional.ofNullable(jwtValidator.validate(matcher.group(1)));
            if (optionalUser.isPresent()) {
                User user = optionalUser.get();
                List<GrantedAuthority> grantedAuthorities = AuthorityUtils
                        .commaSeparatedStringToAuthorityList(user.getRole());
                user.setName(user.getUsername());
                return new UsernamePasswordAuthenticationToken(user.getName(), null, grantedAuthorities);
            }
        }
        return null;
    }

}
