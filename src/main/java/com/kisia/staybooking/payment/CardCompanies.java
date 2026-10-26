package com.kisia.staybooking.payment;

import java.util.Map;

// 토스페이먼츠 카드사 코드(issuerCode) → 화면에 보여 줄 카드사 이름.
// 빌링키 발급 응답의 cardCompany가 비어 있을 때 쓴다.
final class CardCompanies {
    private static final Map<String, String> NAMES = Map.ofEntries(
        Map.entry("3K", "기업BC"), Map.entry("46", "광주"), Map.entry("71", "롯데"), Map.entry("30", "KDB산업"),
        Map.entry("31", "BC"), Map.entry("51", "삼성"), Map.entry("38", "새마을"), Map.entry("41", "신한"),
        Map.entry("62", "신협"), Map.entry("36", "씨티"), Map.entry("33", "우리"), Map.entry("37", "우체국"),
        Map.entry("39", "저축은행"), Map.entry("35", "전북"), Map.entry("42", "제주"), Map.entry("15", "카카오뱅크"),
        Map.entry("3A", "케이뱅크"), Map.entry("24", "토스뱅크"), Map.entry("21", "하나"), Map.entry("61", "현대"),
        Map.entry("11", "KB국민"), Map.entry("91", "NH농협"), Map.entry("34", "수협"));

    private CardCompanies() {
    }

    static String name(String issuerCode) {
        return issuerCode == null ? null : NAMES.get(issuerCode);
    }
}
