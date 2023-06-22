    package com.growable.starting.service;

    import com.auth0.jwt.JWT;
    import com.auth0.jwt.algorithms.Algorithm;
    import com.fasterxml.jackson.core.JsonProcessingException;
    import com.fasterxml.jackson.databind.ObjectMapper;
    import com.growable.starting.dto.auth.KakaoProfile;
    import com.growable.starting.dto.auth.OauthToken;
    import com.growable.starting.jwt.JwtProperties;
    import com.growable.starting.model.User;
    import com.growable.starting.repository.UserRepository;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.beans.factory.annotation.Value;
    import org.springframework.http.HttpEntity;
    import org.springframework.http.HttpHeaders;
    import org.springframework.http.HttpMethod;
    import org.springframework.http.ResponseEntity;
    import org.springframework.stereotype.Service;
    import org.springframework.transaction.annotation.Transactional;
    import org.springframework.util.LinkedMultiValueMap;
    import org.springframework.util.MultiValueMap;
    import org.springframework.web.client.RestTemplate;

    import javax.servlet.http.HttpServletRequest;
    import java.util.Date;


    @Service
    @Transactional
    public class AuthService {

        private final UserRepository userRepository;

        @Autowired
        public AuthService(UserRepository userRepository) {
            this.userRepository = userRepository;
        }

        //환경 변수 가져오기
        @Value("${kakao.clientId}")
        String client_id;

        @Value("${kakao.secret}")
        String client_secret;

        public OauthToken getAccessToken(String code, String redirect_uri) {

            // POST 방식으로 key=value 데이터 요청
            RestTemplate rt = new RestTemplate();

            // HttpHeader 오브젝트 생성
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

            // HttpBody 오브젝트 생성
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "authorization_code");
            params.add("client_id", client_id);
            params.add("redirect_uri", redirect_uri);
            params.add("code", code);

            // HttpHeader 와 HttpBody 정보를 하나의 오브젝트에 담음
            HttpEntity<MultiValueMap<String, String>> kakaoTokenRequest =
                    new HttpEntity<>(params, headers);

            // Http 요청 (POST 방식) 후, response 변수의 응답을 받음
            ResponseEntity<String> accessTokenResponse = rt.exchange(
                    "https://kauth.kakao.com/oauth/token",
                    HttpMethod.POST,
                    kakaoTokenRequest,
                    String.class
            );
            System.out.println("Response from Kakao: " + accessTokenResponse);

            if (accessTokenResponse.getStatusCodeValue() != 200) {
                throw new RuntimeException("Error while obtaining access token: " + accessTokenResponse.getBody());
            }

            System.out.println(accessTokenResponse);

            // JSON 응답을 객체로 변환
            ObjectMapper objectMapper = new ObjectMapper();
            OauthToken oauthToken = null;
            try {
                oauthToken = objectMapper.readValue(accessTokenResponse.getBody(), OauthToken.class);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }

            return oauthToken;
        }

        public String processAuthCode(String code,String redirect_uri) {
            // 넘어온 인가 코드를 통해 access_token 발급
            OauthToken oauthToken = getAccessToken(code,redirect_uri);

            if (oauthToken == null) {
                throw new RuntimeException("Failed to obtain access token");
            }

            // 발급 받은 accessToken 으로 카카오 회원 정보 조회 및 DB 저장
            return SaveUserAndGetToken(oauthToken.getAccess_token());
        }

        public KakaoProfile findProfile(String token) {
            // POST 방식으로 key=value 데이터 요청
            RestTemplate rt = new RestTemplate();

            // HttpHeader 오브젝트 생성
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", "Bearer " + token);
            headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

            // HttpHeader 와 HttpBody 정보를 하나의 오브젝트에 담음
            HttpEntity<MultiValueMap<String, String>> kakaoProfileRequest =
                    new HttpEntity<>(headers);

            // Http 요청 (POST 방식) 후, response 변수의 응답을 받음
            ResponseEntity<String> kakaoProfileResponse = rt.exchange(
                    "https://kapi.kakao.com/v2/user/me",
                    HttpMethod.POST,
                    kakaoProfileRequest,
                    String.class
            );

            // JSON 응답을 객체로 변환
            ObjectMapper objectMapper = new ObjectMapper();
            KakaoProfile kakaoProfile = null;
            try {
                kakaoProfile = objectMapper.readValue(kakaoProfileResponse.getBody(), KakaoProfile.class);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }

            return kakaoProfile;
        }

        public User getUser(HttpServletRequest request) {
            Long userCode = (Long) request.getAttribute("userCode");

            return userRepository.findByUserCode(userCode);
        }

        public String SaveUserAndGetToken(String token) {
            KakaoProfile profile = findProfile(token);

            User user = userRepository.findByKakaoEmail(profile.getKakao_account().getEmail());
            if (user == null) {
                user = User.builder()
                        .kakaoId(profile.getId())
                        .kakaoProfileImg(profile.getKakao_account().getProfile().getProfile_image_url())
                        .kakaoNickname(profile.getKakao_account().getProfile().getNickname())
                        .kakaoEmail(profile.getKakao_account().getEmail())
                        .userRole("ROLE_USER").build();

                userRepository.save(user);
            }

            return createToken(user);
        }

        public String createToken(User user) {
            // Jwt 생성 후 헤더에 추가해서 보내줌

            return JWT.create()
                    .withSubject(user.getKakaoEmail())
                    .withExpiresAt(new Date(System.currentTimeMillis() + JwtProperties.EXPIRATION_TIME))
                    .withClaim("id", user.getUserCode())
                    .withClaim("nickname", user.getKakaoNickname())
                    .sign(Algorithm.HMAC512(JwtProperties.SECRET));
        }

    }