package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.online.AccountService;
import com.ascargon.rocketshow.online.AccountStatus;
import com.ascargon.rocketshow.online.OnlineApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * The account of the online designer the device is linked to, in the settings of the web app: a linked device has
 * the AI and the votes of the designer.
 */
@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    public record LoginRequest(String email, String password) {
    }

    @GetMapping
    public AccountStatus getStatus() {
        return accountService.getStatus();
    }

    // Links the device to the account. A refusal of the online designer (e.g. wrong credentials) is answered as it is.
    @PostMapping("login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            return ResponseEntity.ok(accountService.link(request.email(), request.password()));
        } catch (OnlineApiException e) {
            return OnlineApiAnswers.answer(e.getResponse());
        } catch (IOException e) {
            return OnlineApiAnswers.unreachable(e);
        }
    }

    // Unlinks the device, online as well
    @PostMapping("logout")
    public AccountStatus logout() {
        accountService.unlink();
        return accountService.getStatus();
    }

}
