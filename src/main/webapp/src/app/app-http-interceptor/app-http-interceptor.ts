import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';

import { Injectable } from '@angular/core';
import { Observable } from 'rxjs/Rx';
import { environment } from '../../environments/environment';

@Injectable()
export class AppHttpInterceptor implements HttpInterceptor {

  // The rest endpoint base url
  private restUrl: string;

  constructor() {
    // Create the backend-url
    if (environment.name == 'dev') {
      this.restUrl = 'http://' + environment.localBackend + '/';
    } else {
      this.restUrl = '/'
    }

    this.restUrl += 'api/';
  }

  public intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    let newUrl: string;

    if(req.url.startsWith('\.')) {
      // Referencing local resources (e.g. ./assets for the translate module)
      // -> don't add the api-url
      newUrl = req.url;
    } else {
      newUrl = this.restUrl + req.url;
    }

    const clonedRequest: HttpRequest<any> = req.clone({
      url: newUrl
    });

    return next.handle(clonedRequest);
  }

  getRestUrl(): string {
    return this.restUrl;
  }

  // get(url: string, options: RequestOptionsArgs = undefined): Observable<Response> {
  //   return super.get(this.restUrl + url, options);
  // }

  // post(url: string, body: any): Observable<Response> {
  //   let headers = new Headers();
  //   headers.append('Content-Type', 'application/json');
  //   return super.post(this.restUrl + url, body, {headers: headers});
  // }

  // put(url: string, body: any): Observable<Response> {
  //   return super.put(this.restUrl + url, body);
  // }

  // delete(url: string): Observable<Response> {
  //   return super.delete(this.restUrl + url);
  // }

}
