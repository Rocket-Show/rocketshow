package com.ascargon.rocketshow.online;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Service
public interface AccountService {

    AccountStatus getStatus();

    // Links the device to the account with the passed email and password (device-link of the API). The password is
    // only handed on, never kept. Throws OnlineApiException with the answer of the API when it refuses (e.g. wrong
    // credentials), IOException when it can't be reached.
    AccountStatus link(String email, String password) throws IOException, OnlineApiException;

    // Unlinks the device: online as far as the API can be reached, on the device in any case
    void unlink();

    // The headers which make a request to the API one of the account the device is linked to, none while it isn't
    Map<String, String> getDeviceHeaders() throws IOException;

    // The API refused the passed device headers (it answered 401), e.g. because the device was unlinked from "My
    // devices" online: the device isn't linked anymore, unless it was linked anew meanwhile
    void forgetRefusedLink(Map<String, String> deviceHeaders);

}
