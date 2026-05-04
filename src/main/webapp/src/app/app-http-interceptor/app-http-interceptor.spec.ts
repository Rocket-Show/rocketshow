import { TestBed, inject } from '@angular/core/testing';
import { HttpErrorResponse, HttpHandler, HttpRequest } from '@angular/common/http';
import { Injector } from '@angular/core';
import { of, throwError } from 'rxjs';

import { AppHttpInterceptor } from './app-http-interceptor';
import { AuthService } from '../services/auth.service';

describe('AppHttpInterceptor', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AppHttpInterceptor]
    });
  });

  it('should be created', inject([AppHttpInterceptor], (service: AppHttpInterceptor) => {
    expect(service).toBeTruthy();
  }));

  it('should reinitiate auth status after 401 responses', (done) => {
    const authService = jasmine.createSpyObj<AuthService>('AuthService', ['init']);
    authService.init.and.returnValue(of({
      authenticated: false,
      passwordConfigured: true
    }));
    const injector = jasmine.createSpyObj<Injector>('Injector', ['get']);
    injector.get.and.returnValue(authService);
    const interceptor = new AppHttpInterceptor(injector);
    const request = new HttpRequest('GET', 'system/state');
    const handler = createErrorHandler(401);

    interceptor.intercept(request, handler).subscribe({
      error: () => {
        expect(authService.init).toHaveBeenCalledTimes(1);
        done();
      }
    });
  });

  it('should reinitiate auth status after 403 responses', (done) => {
    const authService = jasmine.createSpyObj<AuthService>('AuthService', ['init']);
    authService.init.and.returnValue(of({
      authenticated: false,
      passwordConfigured: true
    }));
    const injector = jasmine.createSpyObj<Injector>('Injector', ['get']);
    injector.get.and.returnValue(authService);
    const interceptor = new AppHttpInterceptor(injector);
    const request = new HttpRequest('GET', 'settings');
    const handler = createErrorHandler(403);

    interceptor.intercept(request, handler).subscribe({
      error: () => {
        expect(authService.init).toHaveBeenCalledTimes(1);
        done();
      }
    });
  });

  it('should not reinitiate auth status for auth endpoint errors', (done) => {
    const authService = jasmine.createSpyObj<AuthService>('AuthService', ['init']);
    const injector = jasmine.createSpyObj<Injector>('Injector', ['get']);
    injector.get.and.returnValue(authService);
    const interceptor = new AppHttpInterceptor(injector);
    const request = new HttpRequest('POST', 'auth/login', {});
    const handler = createErrorHandler(401);

    interceptor.intercept(request, handler).subscribe({
      error: () => {
        expect(authService.init).not.toHaveBeenCalled();
        done();
      }
    });
  });
});

function createErrorHandler(status: number): HttpHandler {
  return {
    handle: () => throwError(new HttpErrorResponse({ status }))
  };
}
