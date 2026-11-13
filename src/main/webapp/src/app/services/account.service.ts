import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AccountStatus } from '../models/account-status';

// The account of the online designer the device is linked to: a linked device has the AI and the votes of the designer
@Injectable({
  providedIn: 'root',
})
export class AccountService {

  constructor(private http: HttpClient) { }

  getStatus(): Observable<AccountStatus> {
    return this.http.get<AccountStatus>('account');
  }

  // The password is only handed on to rocketshow.net, the device keeps a key of its own for the account
  login(email: string, password: string): Observable<AccountStatus> {
    return this.http.post<AccountStatus>('account/login', { email, password });
  }

  // Unlinks the device, on rocketshow.net as well
  logout(): Observable<AccountStatus> {
    return this.http.post<AccountStatus>('account/logout', null);
  }

}
