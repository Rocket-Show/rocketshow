import { Observable } from 'rxjs';

import { Injectable } from "@angular/core";

export interface ComponentCanDeactivate {
  canDeactivate: () => boolean | Observable<boolean>;
}

@Injectable()
export class PendingChangesGuard  {
  canDeactivate(component: ComponentCanDeactivate): boolean | Observable<boolean> {
    if(!component) {
      return true;
    }
    
    // If there are no pending changes, just allow deactivation; else confirm first
    return component.canDeactivate();
  }
}