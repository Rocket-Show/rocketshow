package com.ascargon.rocketshow.raspberry;

import com.pi4j.context.Context;
import org.springframework.stereotype.Service;

@Service
public interface Pi4jService {
    Context getContext();
}
