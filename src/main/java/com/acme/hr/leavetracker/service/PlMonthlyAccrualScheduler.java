package com.acme.hr.leavetracker.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PlMonthlyAccrualScheduler implements ApplicationRunner {
    private final PlMonthlyAccrualService accrualService;

    public PlMonthlyAccrualScheduler(PlMonthlyAccrualService accrualService) {
        this.accrualService = accrualService;
    }

    @Override
    public void run(ApplicationArguments args) {
        accrualService.accrueCurrentYear();
    }

    @Scheduled(cron = "0 5 0 * * *")
    public void checkMonthlyAccruals() {
        accrualService.accrueCurrentYear();
    }
}
