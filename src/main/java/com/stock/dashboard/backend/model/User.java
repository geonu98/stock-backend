package com.stock.dashboard.backend.model;

import com.stock.dashboard.backend.model.Role;
import com.stock.dashboard.backend.model.audit.DateAudit;
import com.stock.dashboard.backend.model.vo.InterestsVO;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import org.hibernate.annotations.NaturalId;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "USERS")
@Data
@ToString(exclude = "roles")
public class User extends DateAudit implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "USER_ID")
    private Long id;

    @NaturalId
    @Column(name = "EMAIL", unique = true)
    private String email;

    @Column(name = "USERNAME", unique = true, length = 30)
    private String username;

    @Column(name = "NICKNAME", unique = true, length = 50) // 길이와 유니크 지정
    private String nickname;

    @Column(name = "PASSWORD")
    private String password;

    @Column(name = "NAME")
    private String name;

    @Column(name = "AGE")
    private Integer age;



    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean active;

    @Column(name = "IS_EMAIL_VERIFIED", nullable = false)
    private Boolean isEmailVerified;

    @ManyToMany(fetch = FetchType.EAGER, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(name = "USER_AUTHORITY", joinColumns = @JoinColumn(name = "USER_ID"),
            inverseJoinColumns = @JoinColumn(name = "ROLE_ID"))
    private Set<Role> roles = new HashSet<>();


    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "USER_INTEREST",
            joinColumns = @JoinColumn(name = "USER_ID"),
            inverseJoinColumns = @JoinColumn(name = "INTEREST_ID")
    )
    private Set<InterestsVO> interests = new HashSet<>();






    public User() {
        super();
    }

    // 💡 복사 생성자
    public User(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.username = user.getUsername();
        this.nickname = user.getNickname();
        this.password = user.getPassword();
        this.active = user.getActive();
        this.isEmailVerified = user.getIsEmailVerified();
        this.roles = user.getRoles();
    }

    // UserDetails 구현
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority(role.getRole().name()))
                .collect(Collectors.toSet());
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return active; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return isEmailVerified; }


    // 관심사 이름 리스트 반환
    public List<String> getInterestNames() {
        return interests.stream()
                .map(InterestsVO::getName)
                .collect(Collectors.toList());
    }
    // 관심사 추가/삭제 헬퍼
    public void addInterest(InterestsVO interest) {
        interests.add(interest);
        interest.getUsers().add(this); // InterestsVO에서 사용자 컬렉션이 있어야 함
    }

    public void removeInterest(InterestsVO interest) {
        interests.remove(interest);
        interest.getUsers().remove(this);
    }







    // 역할 추가/삭제 헬퍼
    public void addRole(Role role) {
        roles.add(role);
        role.getUserList().add(this);
    }

    public void removeRole(Role role) {
        roles.remove(role);
        role.getUserList().remove(this);
    }
}
