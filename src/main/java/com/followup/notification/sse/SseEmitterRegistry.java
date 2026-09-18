package com.followup.notification.sse;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 사용자별로 열려 있는 SSE 연결(탭/기기당 하나씩)을 들고 있다가 알림 발생 시 그 사용자의 모든 연결로
 * 이벤트를 밀어 보낸다. 연결은 요청 스레드에서 등록/제거되고 전송은 트랜잭션 이벤트 리스너나 스케줄러
 * 스레드에서 일어나므로 스레드 세이프한 구조를 쓴다.
 */
@Component
public class SseEmitterRegistry {

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emittersByUserId = new ConcurrentHashMap<>();

    public void register(Long userId, SseEmitter emitter) {
        emittersByUserId.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>()).add(emitter);
    }

    public void remove(Long userId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByUserId.get(userId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
    }

    /** 대상 사용자의 모든 연결(여러 탭/기기)에 이벤트를 보낸다. 끊긴 연결은 전송 실패 시 자동으로 제거한다. */
    public void sendToUser(Long userId, String eventName, Object data) {
        List<SseEmitter> emitters = emittersByUserId.get(userId);
        if (emitters == null) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                remove(userId, emitter);
            }
        }
    }

    /** 20초마다 모든 연결에 주석 이벤트를 보내 중간의 프록시/로드밸런서가 유휴 연결을 끊지 않게 한다. */
    @Scheduled(fixedRate = 20_000)
    public void pingAll() {
        emittersByUserId.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException e) {
                    remove(userId, emitter);
                }
            }
        });
    }
}
