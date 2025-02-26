package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.util.Action;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;

@Getter
@Setter
public class HttpAction extends Action {

    public enum HttpMethod {
        GET, POST, PUT, DELETE, PATCH
    }

    private HttpMethod httpMethod;
    private String url;
    private String body;
    private HashMap<String, String> headerList = new HashMap<>();

    @Override
    public ActionType getType() {
        return ActionType.HTTP;
    }
}
