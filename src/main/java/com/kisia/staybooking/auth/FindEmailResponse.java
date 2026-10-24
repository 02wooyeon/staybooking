package com.kisia.staybooking.auth;

import java.util.List;

// 이메일 전체가 아니라 앞 2글자만 보이게 가린 값 (예: gu***@st***.test)
public record FindEmailResponse(List<String> emails) {
}
