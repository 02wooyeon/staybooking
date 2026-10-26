package com.kisia.staybooking;

import com.kisia.staybooking.payment.PaymentController;
import com.kisia.staybooking.payment.PaymentHistoryService;
import com.kisia.staybooking.payment.PaymentService;
import com.kisia.staybooking.payment.TossPaymentConfirmationService;
import com.kisia.staybooking.payment.TossPaymentGateway;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

// 결제위젯 키와 API 개별 연동 키(결제창) 중 어떤 쌍을 넣었는지에 따라 화면 방식이 정해진다.
class TossPaymentModeTests {
    @Test
    void individualIntegrationTestKeysOpenThePaymentWindow() {
        PaymentController.PaymentCapabilities capabilities = capabilities("test_ck_example", "test_sk_example");

        assertTrue(capabilities.tossEnabled());
        assertEquals("PAYMENT_WINDOW", capabilities.tossMode());
        assertEquals("test_ck_example", capabilities.tossClientKey());
        assertFalse(capabilities.demoEnabled());
    }

    @Test
    void widgetTestKeysKeepTheWidget() {
        assertEquals("WIDGET", capabilities("test_gck_example", "test_gsk_example").tossMode());
    }

    @Test
    void mixedLiveOrMissingKeysStayDisabled() {
        for (String[] keys : new String[][] {
                {"test_ck_example", "test_gsk_example"},
                {"test_gck_example", "test_sk_example"},
                {"live_ck_example", "live_sk_example"},
                {"", ""}}) {
            PaymentController.PaymentCapabilities capabilities = capabilities(keys[0], keys[1]);
            assertFalse(capabilities.tossEnabled());
            assertNull(capabilities.tossMode());
            // 키가 맞지 않으면 화면에 클라이언트 키도 내려보내지 않는다.
            assertNull(capabilities.tossClientKey());
        }
    }

    private static PaymentController.PaymentCapabilities capabilities(String clientKey, String secretKey) {
        return new PaymentController(mock(PaymentService.class), mock(TossPaymentConfirmationService.class),
            new TossPaymentGateway(clientKey, secretKey), mock(PaymentHistoryService.class), true).capabilities();
    }
}
