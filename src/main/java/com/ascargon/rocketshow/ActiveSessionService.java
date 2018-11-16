package com.ascargon.rocketshow;

public class ActiveSessionService implements SessionService {

    private Session session;

    @Override
    public Session getSession() {
        return session;
    }

    @Override
    public void setSession(Session session) {
        this.session = session;
    }

}
