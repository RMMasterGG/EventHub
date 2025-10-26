package com.semasem.scheduler;

import com.semasem.service.GuestService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GuestCleanupScheduler {

    private final GuestService guestService;

    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupExpiredGuests() {
        guestService.cleanupExpiredGuests();
    }
}