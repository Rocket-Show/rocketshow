import { HttpClient } from '@angular/common/http';
import { Subject } from 'rxjs/Subject';
import { Composition } from './../models/composition';
import { Observable } from 'rxjs/Rx';
import { Set } from './../models/set';
import { Injectable } from '@angular/core';
import { Response } from '@angular/http';

@Injectable()
export class CompositionService {

  private compositions: Composition[];
  private sets: Set[];

  private currentSet: Set;

  // Fires, when compositions have changed (new ones, deleted)
  compositionsChanged: Subject<void> = new Subject<void>();

  constructor(private http: HttpClient) { }

  getCurrentSet(clearCache: boolean = false): Observable<Set> {
    if (this.currentSet && !clearCache) {
      return Observable.of(this.currentSet);
    }

    return this.http.get('set')
      .map(response => {
        this.currentSet = new Set(response);
        return this.currentSet;
      });
  }

  getCompositions(clearCache: boolean = false): Observable<Composition[]> {
    if (this.compositions && !clearCache) {
      return Observable.of(this.compositions);
    }

    return this.http.get('composition/list')
      .map((response: Array<Object>) => {
        this.compositions = [];

        for (let composition of response) {
          this.compositions.push(new Composition(composition));
        }

        return this.compositions;
      });
  }

  getSets(clearCache: boolean = false): Observable<Set[]> {
    if (this.sets && !clearCache) {
      return Observable.of(this.sets);
    }

    return this.http.get('set/list')
      .map((response: Array<Object>) => {
        this.sets = [];

        for (let set of response) {
          this.sets.push(new Set(set));
        }

        return this.sets;
      });
  }

  getComposition(compositionName: string): Observable<Composition> {
    return this.http.get('composition?name=' + compositionName)
      .map(response => {
        return new Composition(response);
      });
  }

  // Load a set on the device
  loadSet(name: string): Observable<Object> {
    return this.http.post('set/load?name=' + name, undefined);
  }

  // Get a set from the device
  getSet(setName: string): Observable<Set> {
    return this.http.get('set/details?name=' + setName)
      .map(response => {
        return new Set(response);
      });
  }

  saveComposition(composition: Composition): Observable<Object> {
    return this.http.post('composition', composition.stringify());
  }

  deleteComposition(name: string): Observable<Object> {
    return this.http.post('composition/delete?name=' + name, undefined);
  }

  saveSet(set: Set): Observable<Object> {
    return this.http.post('set', set.stringify());
  }

  deleteSet(name: string): Observable<Object> {
    return this.http.post('set/delete?name=' + name, undefined);
  }

}
