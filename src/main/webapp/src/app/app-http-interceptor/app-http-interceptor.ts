import { HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from "@angular/common/http";

import { Injectable, Injector } from "@angular/core";
import { Observable, throwError } from "rxjs";
import { catchError } from "rxjs/operators";
import { environment } from "../../environments/environment";
import { AuthService } from "../services/auth.service";

@Injectable()
export class AppHttpInterceptor implements HttpInterceptor {
  // The rest endpoint base url
  private restUrl: string;
  private authRefreshInProgress: boolean = false;

  constructor(private injector: Injector) {
    // Create the backend-url
    if (environment.name == "dev") {
      this.restUrl = "http://" + environment.localBackend + "/";
    } else {
      this.restUrl = "/";
    }

    this.restUrl += "api/";
  }

  public intercept(
    req: HttpRequest<any>,
    next: HttpHandler
  ): Observable<HttpEvent<any>> {
    const originalUrl = req.url;
    let newUrl: string;

    if (req.url.startsWith(".")) {
      // Referencing local resources (e.g. ./assets for the translate module)
      // -> don't add the api-url
      newUrl = req.url;
    } else {
      newUrl = this.restUrl + req.url;
    }

    const methodsWithBody = ['POST', 'PUT', 'PATCH'];

    if (methodsWithBody.includes(req.method) && !req.headers.has('Content-Type')) {
      req = req.clone({
        setHeaders: {
          'Content-Type': 'application/json',
        },
      });
    }

    // use withCredentials to send the JSESSIONCOOKIE with each request
    const clonedRequest: HttpRequest<any> = req.clone({
      url: newUrl,
      withCredentials: true
    });

    return next.handle(clonedRequest).pipe(
      catchError((error) => {
        if (this.shouldRefreshAuthStatus(error, originalUrl)) {
          this.refreshAuthStatus();
        }

        return throwError(error);
      })
    );
  }

  getRestUrl(): string {
    return this.restUrl;
  }

  private shouldRefreshAuthStatus(error: any, originalUrl: string): boolean {
    if (!(error instanceof HttpErrorResponse)) {
      return false;
    }

    if (this.authRefreshInProgress || originalUrl.startsWith("auth/")) {
      return false;
    }

    return error.status === 401 || error.status === 403;
  }

  private refreshAuthStatus(): void {
    this.authRefreshInProgress = true;

    this.injector.get(AuthService).init().subscribe({
      complete: () => {
        this.authRefreshInProgress = false;
      },
      error: () => {
        this.authRefreshInProgress = false;
      }
    });
  }
}
