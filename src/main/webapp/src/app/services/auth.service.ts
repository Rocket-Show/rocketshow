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

  constructor(private http: HttpClient) {
    this.init().subscribe();
  }

  init(): Observable<AuthState> {
    this.noConnection = false;
    return this.http.get<AuthState>('auth/me', { withCredentials: true }).pipe(
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

  changePassword(oldPassword: string, newPassword: string) {
    return this.http.post<AuthState>(
      'auth/change-password',
      { "oldPassword": oldPassword, "newPassword": newPassword },
      { withCredentials: true }
    );
  }
}
