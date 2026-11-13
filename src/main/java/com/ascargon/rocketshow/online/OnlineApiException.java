package com.ascargon.rocketshow.online;

/**
 * The API of the online designer refused a request, e.g. a login with a wrong password. Holds its answer, to hand it
 * on to the web app as it is.
 */
public class OnlineApiException extends Exception {

    private final OnlineApiClient.Response response;

    public OnlineApiException(OnlineApiClient.Response response) {
        super("The API of the online designer answered HTTP " + response.status());
        this.response = response;
    }

    public OnlineApiClient.Response getResponse() {
        return response;
    }

}
