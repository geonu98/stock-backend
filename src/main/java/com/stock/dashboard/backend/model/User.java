package com.stock.dashboard.backend.model;

import com.stock.dashboard.backend.model.Role;
import com.stock.dashboard.backend.model.audit.DateAudit;
import com.stock.dashboard.backend.model.payload.request.UpdateUserRequest;
import com.stock.dashboard.backend.model.vo.InterestsVO;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
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
@Getter
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

    @Column(name = "PHONE_NUMBER")
    private String phoneNumber;

    @Column(name = "IS_ACTIVE", nullable = false)
    private Boolean active;

    @Column(name = "IS_EMAIL_VERIFIED", nullable = false)
    private Boolean isEmailVerified;

    @Column(name = "PROVIDER")
    private String provider;  // local/kakao/google/ 등등

    @Column(name = "PROVIDER_ID")
    private String providerId; // 소셜 고유 ID

    @Column(name = "PROFILE_IMAGE")
    private String profileImage;



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



    public User(String email, String encodedPassword, String name, Integer age, String phoneNumber , String provider, boolean emailVerified) {
        this.email = email;
        this.password = encodedPassword;
        this.name = name;
        this.age = age;
        this.phoneNumber = phoneNumber;

        this.active = true;            // 기본 활성화
        this.isEmailVerified = emailVerified;  // 기본값: 이메일 인증되지 않음
        this.provider = "local";       // 임시, 나중에 카카오/구글 들어오면 변경
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



    //비밀번호 변경
    public void updatePassword(String encodePassword){
        this.password = encodePassword;
    }


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


    public void updateProfile(UpdateUserRequest req) {
        if (req == null) return; // ide 오류 때매 걍 추가함  오류 보기싫어서

        if (req.getName() != null) this.name = req.getName();
        if (req.getNickname() != null) this.nickname = req.getNickname();
        if (req.getAge() != null) this.age = req.getAge();
        if (req.getPhoneNumber() != null) this.phoneNumber = req.getPhoneNumber();
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
