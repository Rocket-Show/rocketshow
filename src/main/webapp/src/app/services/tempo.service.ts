import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

/**
 * The tempo the beat-synced effects run at, from outside: tapped, set, halved, doubled or nudged. It drives the scenes
 * picked by hand and the compositions without audio. The device sends the tempo playing, where it is from and its
 * beats with its state.
 */
@Injectable({
  providedIn: 'root',
})
export class TempoService {
  constructor(private http: HttpClient) { }

  // a beat tapped: the beats tapped give the tempo, the first one after a pause is the first beat of a bar
  tap(): Observable<Object> {
    return this.http.post('tempo/tap', null);
  }

  // now is the first beat of a bar
  beatOne(): Observable<Object> {
    return this.http.post('tempo/beat-one', null);
  }

  // a tempo set (undefined = back to the tempo of the composition or the project playing)
  set(beatsPerMinute: number): Observable<Object> {
    return this.http.post(beatsPerMinute === undefined ? 'tempo/set' : 'tempo/set?value=' + encodeURIComponent('' + beatsPerMinute), null);
  }

  halve(): Observable<Object> {
    return this.http.post('tempo/halve', null);
  }

  double(): Observable<Object> {
    return this.http.post('tempo/double', null);
  }

  // a little faster (1) or slower (-1), in beats per minute
  nudge(beatsPerMinute: number): Observable<Object> {
    return this.http.post('tempo/nudge?value=' + encodeURIComponent('' + beatsPerMinute), null);
  }
}
