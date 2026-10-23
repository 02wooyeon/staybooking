package com.kisia.staybooking.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 로그인·아이디 찾기 같은 요청을 짧은 시간에 반복하는 공격(브루트포스·계정 열거)을 막는다.
// 서버 한 대 기준의 메모리 저장이라 재시작하면 초기화된다. 서버를 여러 대로 늘리면 Redis로 옮겨야 한다.
@Component
public class AttemptLimiter {
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Duration window;
    private final Duration lockTime;
    private final Clock clock;

    @Autowired
    public AttemptLimiter(@Value("${staybooking.auth.attempt-window:PT15M}") Duration window,
                          @Value("${staybooking.auth.lock-time:PT15M}") Duration lockTime) {
        this(window, lockTime, Clock.systemUTC());
    }

    AttemptLimiter(Duration window, Duration lockTime, Clock clock) {
        this.window = window;
        this.lockTime = lockTime;
        this.clock = clock;
    }

    public boolean isLocked(String key) {
        Window current = windows.get(key);
        return current != null && current.lockedUntil != null && current.lockedUntil.isAfter(clock.instant());
    }

    // 실패를 기록하고, 한도에 닿으면 잠근다.
    public void recordFailure(String key, int maxFailures) {
        Instant now = clock.instant();
        windows.compute(key, (k, current) -> {
            Window next = current == null || current.startedAt.plus(window).isBefore(now)
                ? new Window(now, 0, null) : current;
            int failures = next.failures + 1;
            Instant lockedUntil = failures >= maxFailures ? now.plus(lockTime) : next.lockedUntil;
            return new Window(next.startedAt, failures, lockedUntil);
        });
        cleanUpOccasionally(now);
    }

    public void reset(String key) {
        windows.remove(key);
    }

    // 오래된 기록이 계속 쌓이지 않게 가끔 정리한다.
    private void cleanUpOccasionally(Instant now) {
        if (windows.size() < 10_000) return;
        windows.entrySet().removeIf(entry -> entry.getValue().startedAt.plus(window).isBefore(now)
            && (entry.getValue().lockedUntil == null || entry.getValue().lockedUntil.isBefore(now)));
    }

    private record Window(Instant startedAt, int failures, Instant lockedUntil) { }
}
