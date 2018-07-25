import { HttpClient } from '@angular/common/http';
import { Composition } from './../models/composition';
import { Observable } from 'rxjs/Rx';
import { Set } from './../models/set';
import { Injectable } from '@angular/core';
import { Response } from '@angular/http';
import { environment } from '../../environments/environment';
import { CompositionFile } from '../models/composition-file';

@Injectable()
export class FileService {

  constructor(private http: HttpClient) { }

  getFiles(): Observable<CompositionFile[]> {
    return this.http.get('file/list')
      .map((response: Array<Object>) => {
        let files: CompositionFile[] = [];

        for (let file of response) {
          files.push(Composition.getFileObjectByType(file));
        }

        return files;
      });
  }

  deleteFile(file: CompositionFile): Observable<void> {
    return this.http.post('file/delete?name=' + file.name + '&type=' + file.type, undefined).map((response: Response) => {
      return null;
    });
  }

}
