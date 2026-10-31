import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, of, Subject, timer } from 'rxjs';
import { catchError, filter, map, switchMap, take, tap } from 'rxjs/operators';

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
  public noConnection: boolean = false;
  public initiallyLoaded: boolean = false;

  // Remembered in this browser, to log in again after the device rebooted and forgot the session
  private readonly passwordKey = 'adminPassword';

  constructor(private http: HttpClient) {
    this.init().subscribe();
  }

  init(): Observable<AuthState> {
    this.noConnection = false;
    return this.http.get<AuthState>('auth/me', { withCredentials: true }).pipe(
      switchMap(result => this.loginWithStoredPassword(result)),
      tap(result => {
        this.setCurrentState(result);
      }),
      catchError((error) => {
        if (error.status === 0) {
          this.noConnection = true;
        }
        return of(this.setCurrentState({
          authenticated: false,
          passwordConfigured: true
        }, false));
      })
    );
  }

  // Whether the device forgot the session (e.g. rebooted), without changing the state. False, if not reachable.
  sessionLost(): Observable<boolean> {
    return this.http.get<AuthState>('auth/me', { withCredentials: true }).pipe(
      map(result => !result.authenticated),
      catchError(() => of(false))
    );
  }

  private loginWithStoredPassword(state: AuthState): Observable<AuthState> {
    if (!state.passwordConfigured) {
      // E.g. after a factory reset
      this.removeStoredPassword();
      return of(state);
    }

    const password = this.getStoredPassword();

    if (state.authenticated || !password) {
      return of(state);
    }

    return this.http.post<AuthState>(
      'auth/login',
      { password },
      { withCredentials: true }
    ).pipe(
      catchError((error) => {
        if (error.status === 401) {
          // E.g. changed in another browser -> ask for the new one
          this.removeStoredPassword();
        }
        return of(state);
      })
    );
  }

  private getStoredPassword(): string {
    try {
      return localStorage.getItem(this.passwordKey);
    } catch (e) {
      return null;
    }
  }

  private storePassword(password: string) {
    try {
      localStorage.setItem(this.passwordKey, password);
    } catch (e) {
      // Storage not available (e.g. disabled) -> the user has to log in after each reboot
    }
  }

  private removeStoredPassword() {
    try {
      localStorage.removeItem(this.passwordKey);
    } catch (e) {
      // Storage not available -> nothing stored either
    }
  }

  pollForStateAfterConnectionLoss(pollIntervalMs: number = 1000): Observable<AuthState> {
    let connectionLost = false;

    return timer(0, pollIntervalMs).pipe(
      switchMap(() => this.http.get<AuthState>('auth/me', { withCredentials: true }).pipe(
        catchError((error) => {
          connectionLost = true;
          if (error.status === 0) {
            this.noConnection = true;
          }
          return of(undefined);
        })
      )),
      filter((result) => connectionLost && result !== undefined),
      take(1),
      map((result) => this.setCurrentState(result as AuthState))
    );
  }

  private setCurrentState(state: AuthState, connectionAvailable: boolean = true): AuthState {
    this.currentState = state;
    this.state.next(this.currentState);
    this.initiallyLoaded = true;
    if (connectionAvailable) {
      this.noConnection = false;
    }
    return this.currentState;
  }

  setup(
    language: string,
    deviceName: string,
    password: string,
    wifiApEnabled: boolean,
    wifiApPassword: string,
    tlsEnabled: boolean
  ) {
    return this.http.post<AuthState>(
      'auth/setup',
      {
        "language": language,
        "deviceName": deviceName,
        "password": password,
        "wifiApEnabled": wifiApEnabled,
        "wifiApPassword": wifiApPassword,
        "tlsEnabled": tlsEnabled
      },
      { withCredentials: true }
    ).pipe(
      tap(res => {
        this.storePassword(password);
        this.state.next(res);
      })
    );
  }

  login(password: string) {
    return this.http.post<AuthState>(
      'auth/login',
      { password },
      { withCredentials: true }
    ).pipe(
      tap(res => {
        this.storePassword(password);
        this.state.next(res);
      })
    );
  }

  logout() {
    return this.http.post(
      'auth/logout',
      {},
      { withCredentials: true }
    ).pipe(
      tap(() => {
        // Otherwise the next start would log in again right away
        this.removeStoredPassword();
        this.state.next({
          authenticated: false,
          passwordConfigured: true
        });
      })
    );
  }

  changePassword(oldPassword: string, newPassword: string) {
    return this.http.post<AuthState>(
      'auth/change-password',
      { "oldPassword": oldPassword, "newPassword": newPassword },
      { withCredentials: true }
    ).pipe(
      tap(() => this.storePassword(newPassword))
    );
  }
}
