package com.example.bami.short_travel.service;

import com.example.bami.short_travel.dto.*;
import com.example.bami.weather.service.ReverseGeocodingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AIRecommendationService {

    private final WebClient webClient;
    private ReverseGeocodingService reverseGeocodingService;

    @Value("app.ai_addr")
    private String endPoint;
    private final String googleApiKey;  // Google API Key


    public AIRecommendationService(WebClient.Builder webClientBuilder, ReverseGeocodingService reverseGeocodingService,
                                   @Value("${google.maps.key}") String googleApiKey) {
        this.webClient = webClientBuilder.build();
        this.reverseGeocodingService = reverseGeocodingService;
        this.googleApiKey = googleApiKey;
    }

    public List<RecommendationDTO> getRecommendations(ShortTravelDTO shortTravelDTO) {
        AiShortTravelDTO aiShortTravelDTO = AiShortTravelDTO.toAiShortTravelDTO(shortTravelDTO);

        // List<Map<String, Object>>로 응답을 받기 위한 설정
        List<Map<String, Object>> recommendations = webClient.post()
                .uri("http://localhost:8000/ai/trip/short")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(aiShortTravelDTO)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .block();

        List<RecommendationDTO> recommendationDTOS = new ArrayList<>();

        for (Map<String, Object> recommendation : recommendations) {
            List<Map<String, Object>> places = (List<Map<String, Object>>) recommendation.get("places");

            RecommendationDTO recommendationDTO = new RecommendationDTO((String) recommendation.get("day"));
            List<PlaceDTO> placeDTOs = new ArrayList<>();

            for (Map<String, Object> place : places) {
                String name = null;
                String city = null;
                String address = null;
                if (!place.get("name").equals("0.0")){
                    name = (String)place.get("name");
                }
                if (!place.get("city").equals("0.0")){
                    city = (String)place.get("city");
                } else{
                    city = reverseGeocodingService.getAddress(((Number) place.get("latitude")).doubleValue(), ((Number) place.get("longitude")).doubleValue());
                }
                if (!place.get("address").equals("0.0")){
                    address = (String)place.get("address");
                }
                float latitude = ((Number) place.get("longitude")).floatValue();
                float longitude = ((Number) place.get("latitude")).floatValue();

                PlaceDTO placeDTO = new PlaceDTO(name, city, address, latitude, longitude);
                placeDTOs.add(placeDTO);
                recommendationDTO.addPlaces(placeDTO);
            }
//             각 PlaceDTO 간의 거리 계산
            for (int i = 0; i < placeDTOs.size() - 1; i++) {
                for (int j = i + 1; j < placeDTOs.size(); j++) {
                    PlaceDTO place1 = placeDTOs.get(i);
                    PlaceDTO place2 = placeDTOs.get(j);
                    float distance = getDistanceBetweenPlaces(place1, place2);
                    log.info("Distance between " + place1.getName() + " and " + place2.getName() + ": " + distance + " meters");
                }
            }

            recommendationDTOS.add(recommendationDTO);
        }

        return recommendationDTOS;
    }


    public float getDistanceBetweenPlaces(PlaceDTO place1, PlaceDTO place2) {
        String origins = place1.getLatitude() + "," + place1.getLongitude();
        String destinations = place2.getLatitude() + "," + place2.getLongitude();

        String url = "https://maps.googleapis.com/maps/api/distancematrix/json?"
                + "units=metric&mode=transit"
                + "&origins=" + origins
                + "&destinations=" + destinations
                + "&region=KR"
                + "&key=" + googleApiKey;

        // Google Distance Matrix API 호출
        Map<String, Object> response = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

        List<Map<String, Object>> rows = (List<Map<String, Object>>) response.get("rows");
        if (rows != null && !rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            List<Map<String, Object>> elements = (List<Map<String, Object>>) row.get("elements");
            if (elements != null && !elements.isEmpty()) {
                Map<String, Object> element = elements.get(0);
                Map<String, Object> distance = (Map<String, Object>) element.get("distance");
                return ((Number) distance.get("value")).floatValue(); // 거리 값 (미터 단위)
            }
        }

        return -1; // 거리를 계산할 수 없을 때
    }
}