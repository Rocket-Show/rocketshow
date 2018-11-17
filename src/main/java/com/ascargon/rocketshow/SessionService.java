package com.ascargon.rocketshow;

import org.springframework.stereotype.Service;

@Service
public interface SessionService {

    Session getSession();

    void setSession(Session session);

    void save();

}
