package com.motivhub.be.global.config;

import java.util.Arrays;
import java.util.List;

// FRONTEND_URL 환경변수는 콤마로 여러 origin을 받을 수 있다(배포 전환기에 임시 프리뷰 주소와
// 최종 커스텀 도메인을 동시에 허용해야 하는 경우). CORS/WebSocket처럼 "여러 개를 다 허용"해야
// 하는 곳은 allOrigins()를, 로그인 리다이렉트·초대 메일 링크처럼 절대 URL 하나가 필요한 곳은
// 목록의 첫 번째 값을 대표 주소로 쓰는 primary()를 쓴다.
public final class FrontendUrls {

    private FrontendUrls() {
    }

    public static List<String> allOrigins(String rawFrontendUrl) {
        return Arrays.stream(rawFrontendUrl.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    public static String primary(String rawFrontendUrl) {
        return allOrigins(rawFrontendUrl).get(0);
    }
}
