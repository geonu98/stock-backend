package com.stock.dashboard.backend.model;

import com.stock.dashboard.backend.model.audit.DateAudit;
import com.stock.dashboard.backend.model.payload.request.UpdateUserRequest;
import com.stock.dashboard.backend.model.vo.InterestsVO;
import com.stock.dashboard.backend.util.UsernameGenerator;
import jakarta.persistence.*;
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


    @Column(name = "EMAIL", unique = true)
    private String email;

    @Column(name = "USERNAME", unique = true, length = 30)
    private String username;

    @Column(name = "NICKNAME", unique = true, length = 50)
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
    private Boolean emailVerified;

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
        this.emailVerified = emailVerified;  // 기본값: 이메일 인증되지 않음
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
        this.emailVerified = user.getEmailVerified();
        this.roles = user.getRoles();
    }
//(소셜 유저 정적 팩토리 메서드)
public static User createSocialUser(
        String email,
        String nickname,
        String provider,
        String providerId,
        String profileImage,
        Role defaultRole
) {
    User user = new User();

    user.email = (email == null || email.isBlank()) ? null : email;


    user.username = UsernameGenerator.generate(provider, providerId);

    // 3) nickname null 허용
    user.nickname = nickname;

    // 4) 비밀번호는 소셜 로그인에서 사용되지 않음
    user.password = "SOCIAL_LOGIN";

    user.provider = provider;
    user.providerId = providerId;

    user.profileImage = profileImage;

    user.active = true;
    user.emailVerified = false; // 소셜 이메일 검증은 별도 처리

    // 5) 역할 부여
    user.roles = new HashSet<>();
    user.roles.add(defaultRole);

    return user;
}
//이메일 없는용 따로 하나 만듬
    public static User createSocialStub(
            String provider,
            String providerId,
            String nickname,
            String profileImage,
            Role defaultRole
    ) {
        User user = new User();

        user.email = null;                 // ✅ 핵심: email은 비워둔다
        user.username = UsernameGenerator.generate(provider, providerId);
        user.nickname = nickname;
        user.password = "SOCIAL_LOGIN";
        user.provider = provider;
        user.providerId = providerId;
        user.profileImage = profileImage;
        user.active = true;
        user.emailVerified = false;

        user.roles = new HashSet<>();
        user.roles.add(defaultRole);

        return user;
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
    public boolean isEnabled() {
        return active;   // 계정 정지/탈퇴만 판단
    }



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

    public void updateNickname(String nickname) {
        if (nickname != null && !nickname.isBlank()) {
            this.nickname = nickname;
        }
    }

    public void updateProfileImage(String profileImage) {
        if (profileImage != null && !profileImage.isBlank()) {
            this.profileImage = profileImage;
        }
    }

    //이메일 연결
    public void connectEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be blank");
        }

        this.email = email;
        this.emailVerified = false; // 이메일 인증후 변경 할거임
    }


//이메일 인증 확인
    public  void  verifyEmail(){
        this.emailVerified = true;

    }

    public void connectSocial(String provider, String providerId) {

        // 이미 같은 provider로 연결되어 있다면 아무것도 하지 않음
        if (provider.equals(this.provider) && providerId.equals(this.providerId)) {
            return;
        }

        // 다른 소셜 Provider로 이미 가입한 경우 처리 (확장 가능)
        // 예: 기존에 이메일 인증한 로컬 계정 → 소셜 로그인 추가 연결

        this.provider = provider;
        this.providerId = providerId;
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
