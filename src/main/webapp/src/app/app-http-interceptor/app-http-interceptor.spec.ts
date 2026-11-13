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

  // The request as the interceptor hands it on
  function intercepted(request: HttpRequest<any>): HttpRequest<any> {
    let handled: HttpRequest<any>;
    const interceptor = new AppHttpInterceptor(jasmine.createSpyObj<Injector>('Injector', ['get']));
    interceptor.intercept(request, { handle: (next) => { handled = next; return of(); } }).subscribe();
    return handled;
  }

  it('should send JSON by default', () => {
    const request = intercepted(new HttpRequest('POST', 'account/login', { email: 'band@example.com' }));

    expect(request.url.endsWith('api/account/login')).toBeTrue();
    expect(request.headers.get('Content-Type')).toBe('application/json');
  });

  it('should leave the type of a form with a file to the browser', () => {
    // e.g. a manual the designer hands on to the AI, whose type holds the boundary of its parts
    const form = new FormData();
    form.append('action', 'generate');
    form.append('manual', new Blob(['%PDF-1.4'], { type: 'application/pdf' }), 'manual.pdf');

    const request = intercepted(new HttpRequest('POST', 'ai-fixtures', form));

    expect(request.headers.has('Content-Type')).toBeFalse();
    expect(request.body).toBe(form);
  });
});

function createErrorHandler(status: number): HttpHandler {
  return {
    handle: () => throwError(new HttpErrorResponse({ status }))
  };
}
