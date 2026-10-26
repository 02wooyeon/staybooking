package com.kisia.staybooking.payment;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 빌링키 암호화. 빌링키가 있으면 카드 정보 없이 결제할 수 있으므로 DB에는 AES-256-GCM으로 암호화해 저장한다.
 * 키는 STAYBOOKING_BILLING_ENCRYPTION_KEY(Base64 32바이트)로 넣는다. 없으면 이번 실행에서만 쓰는 임시 키를 만든다
 * (서버를 다시 켜면 예전에 등록한 카드는 복호화되지 않아 다시 등록해야 한다).
 */
@Component
public class BillingKeyCipher {
    private static final Logger log = LoggerFactory.getLogger(BillingKeyCipher.class);
    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public BillingKeyCipher(@Value("${staybooking.billing.encryption-key:}") String base64Key) {
        byte[] raw;
        if (base64Key == null || base64Key.isBlank()) {
            raw = new byte[32];
            random.nextBytes(raw);
            log.warn("STAYBOOKING_BILLING_ENCRYPTION_KEY is not set; registered cards are only usable until restart");
        } else {
            raw = Base64.getDecoder().decode(base64Key.trim());
            if (raw.length != 32) throw new IllegalStateException("Billing encryption key must be 32 bytes");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(encrypted, 0, out, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Billing key encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        try {
            if (stored == null || !stored.startsWith(PREFIX)) throw new GeneralSecurityException("Unknown format");
            byte[] in = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
            return new String(cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Billing key decryption failed", e);
        }
    }
}
