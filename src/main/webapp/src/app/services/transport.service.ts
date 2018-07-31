import { Response } from '@angular/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs/Observable';
import { HttpClient } from '../../../node_modules/@angular/common/http';

@Injectable()
export class TransportService {

  constructor(private http: HttpClient) { }

  play(): Observable<null> {
    return this.http.post('transport/play', null).map((response: Response) => {
      return null;
    });
  }

  stop(): Observable<null> {
    return this.http.post('transport/stop', null).map((response: Response) => {
      return null;
    });
  }

  pause(): Observable<null> {
    return this.http.post('transport/pause', null).map((response: Response) => {
      return null;
    });
  }

  seek(positionMillis: number): Observable<null> {
    return this.http.post('transport/seek?position=' + positionMillis, null).map((response: Response) => {
      return null;
    });
  }

  nextComposition(): Observable<null> {
    return this.http.post('transport/next-composition', null).map((response: Response) => {
      return null;
    });
  }

  previousComposition(): Observable<null> {
    return this.http.post('transport/previous-composition', null).map((response: Response) => {
      return null;
    });
  }

  setCompositionName(compositionName: string): Observable<null> {
    return this.http.post('transport/set-composition-name?name=' + compositionName, null).map((response: Response) => {
      return null;
    });
  }

  setCompositionIndex(index: number): Observable<null> {
    return this.http.post('transport/set-composition-index?index=' + index, null).map((response: Response) => {
      return null;
    });
  }

}
