package com.stock.dashboard.backend.repository;

import com.stock.dashboard.backend.model.User; // User 엔티티 경로
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * User 엔티티에 대한 데이터 접근 계층입니다.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     *  (email)으로 사용자를 조회합니다.
     * CustomUserDetailsService의 loadUserByUsername에 맞추어 findByUsername을 유지합니다.
     */

    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByEmail(String email);
    /**
     * 이메일 중복 체크를 위한 메서드입니다.
     */
    Boolean existsByEmail(String email);

    /**
     * 닉네임 중복 체크를 위한 메서드입니다.
     */
    Boolean existsByNickname(String nickname);
}
