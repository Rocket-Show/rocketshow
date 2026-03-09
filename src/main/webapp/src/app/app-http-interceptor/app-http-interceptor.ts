import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest, HttpHeaders } from "@angular/common/http";

import { Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { environment } from "../../environments/environment";

@Injectable()
export class AppHttpInterceptor implements HttpInterceptor {
  // The rest endpoint base url
  private restUrl: string;

  constructor() {
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

    return next.handle(clonedRequest);
  }

  getRestUrl(): string {
    return this.restUrl;
  }
}
