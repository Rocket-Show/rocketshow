import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BehaviorSubject, Subject } from 'rxjs';
import { tap } from 'rxjs/operators';

export interface AuthState {
  authenticated: boolean;
  passwordConfigured: boolean;
  username?: string;
  roles?: string[];
}

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  public state: Subject<AuthState> = new Subject();
  public currentState: AuthState;

  constructor(private http: HttpClient) {
    this.init();
  }

  private init() {
    this.http.get<AuthState>('auth/me', { withCredentials: true })
      .subscribe({
        next: result => {
          this.currentState = result;
          this.state.next(this.currentState);
        },
        error: () => {
          this.currentState = {
            authenticated: false,
            passwordConfigured: true
          };
          this.state.next(this.currentState);
        }
      });
  }

  setup(language: string, deviceName: string, password: string) {
    return this.http.post<AuthState>(
      'auth/setup',
      { "language": language, "deviceName": deviceName, "password": password },
      { withCredentials: true }
    ).pipe(
      tap(res => this.state.next(res))
    );
  }

  login(password: string) {
    return this.http.post<AuthState>(
      'auth/login',
      { password },
      { withCredentials: true }
    ).pipe(
      tap(res => this.state.next(res))
    );
  }

  logout() {
    return this.http.post(
      'auth/logout',
      {},
      { withCredentials: true }
    ).pipe(
      tap(() => this.state.next({
        authenticated: false,
        passwordConfigured: true
      }))
    );
  }
}