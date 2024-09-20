    package com.example.bami.short_travel.controller;

    import com.example.bami.short_travel.dto.*;
    import com.example.bami.short_travel.service.AIRecommendationService;
    import com.example.bami.short_travel.service.TravelPlanService;
    import com.example.bami.user.domain.BamiUser;
    import com.example.bami.user.repository.UserRepository;
    import com.example.bami.user.security.JwtTokenProvider;
    import jakarta.servlet.http.HttpServletRequest;
    import lombok.AllArgsConstructor;
    import lombok.extern.slf4j.Slf4j;
    import org.springframework.http.*;
    import org.springframework.web.bind.annotation.*;

    import java.util.List;

    @RestController
    @RequestMapping("/api/shortTrip")
    @AllArgsConstructor
    @Slf4j
    public class ShortTravelController {

        private final TravelPlanService travelPlanService;
        private final JwtTokenProvider jwtTokenProvider;
        private final UserRepository userRepository;
        private final AIRecommendationService aiRecommendationService;


        @PostMapping("/save")
        public ResponseEntity<String> saveTravelPlan(@RequestBody SaveShortTravelDTO saveShortTravelDTO, HttpServletRequest request) {
            String token = jwtTokenProvider.resolveToken(request);
            if (token == null || !jwtTokenProvider.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("유효하지 않은 토큰입니다.");
            }

            String email = jwtTokenProvider.getClaimsAsMap(token).get("email");
            BamiUser bamiUser = userRepository.findByEmail(email);
            if (bamiUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("사용자를 찾을 수 없습니다.");
            }

            int userId = bamiUser.getId();

            if (userId == -1) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
            }

            try {
                travelPlanService.saveTravelPlan(saveShortTravelDTO, userId);
                return ResponseEntity.ok("일정이 성공적으로 저장되었습니다.");
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("일정 저장 중 오류 발생: " + e.getMessage());
            }
        }

        @PostMapping(value = "/submit", consumes = "application/json")
        public List<RecommendationDTO> submitTravelPlan(@RequestBody ShortTravelDTO shortTravelDTO) {
            log.info("Received travel plan: {}", shortTravelDTO);

            return aiRecommendationService.getRecommendations(shortTravelDTO);
        }
    }