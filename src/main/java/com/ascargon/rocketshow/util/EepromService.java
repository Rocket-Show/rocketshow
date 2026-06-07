package com.ascargon.rocketshow.util;

import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public interface EepromService {

    LocalDate getVersionDate() throws Exception;

}
