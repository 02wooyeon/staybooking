package com.kisia.staybooking;

import com.kisia.staybooking.common.ApiException;
import com.kisia.staybooking.payment.PaymentController;
import com.kisia.staybooking.payment.PaymentService;
import com.kisia.staybooking.payment.PaymentHistoryService;
import com.kisia.staybooking.payment.TossPaymentConfirmationService;
import com.kisia.staybooking.payment.TossPaymentGateway;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TossPaymentConfigurationTests {
    @Test
    void onlyMatchingWidgetTestKeysEnableTossAndDisableDemoBypass() {
        TossPaymentGateway gateway = new TossPaymentGateway("test_gck_example", "test_gsk_example");
        PaymentController controller = new PaymentController(mock(PaymentService.class),
            mock(TossPaymentConfirmationService.class), gateway, mock(PaymentHistoryService.class), true);

        assertTrue(controller.capabilities().tossEnabled());
        assertFalse(controller.capabilities().demoEnabled());
        assertEquals("test_gck_example", controller.capabilities().tossClientKey());
        assertThrows(ApiException.class,
            () -> controller.completeDemo(null, new PaymentController.DemoCompleteRequest(1L)));
    }

    @Test
    void liveAndMismatchedKeysStayDisabled() {
        assertFalse(new TossPaymentGateway("live_gck_example", "live_gsk_example").isConfigured());
        assertFalse(new TossPaymentGateway("test_gck_example", "test_sk_example").isConfigured());
    }
}
