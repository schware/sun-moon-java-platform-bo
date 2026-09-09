package com.sunmoon.bo.domain.operator;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the three permission tiers into Spring Security authorities, which
 * is what lets controllers state their requirement inline:
 *
 * <ol>
 *   <li>전체 관리자 → {@code ROLE_SUPER_ADMIN}</li>
 *   <li>화면별 → an authority exists for that screen at all</li>
 *   <li>화면 안의 행위별 → {@code SCREEN_ACTION}, e.g. {@code COMMON_CODE_SAVE}</li>
 * </ol>
 */
@Service
public class OperatorDetailsService implements UserDetailsService {

    private final OperatorRepository operatorRepository;

    public OperatorDetailsService(OperatorRepository operatorRepository) {
        this.operatorRepository = operatorRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Operator operator = operatorRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("No operator named " + username));

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        if (operator.superAdmin()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
        }
        for (OperatorScreenPermission permission : operator.permissions()) {
            permission.authorities().forEach(a -> authorities.add(new SimpleGrantedAuthority(a)));
        }

        return User.withUsername(operator.username())
                .password(operator.passwordHash())
                .authorities(authorities)
                .disabled(!operator.active())
                .build();
    }
}
