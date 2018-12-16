import { Observable } from 'rxjs';
import {CanDeactivate} from '@angular/router';

export interface ComponentCanDeactivate {
  canDeactivate: () => boolean | Observable<boolean>;
}

export class PendingChangesGuard implements CanDeactivate<ComponentCanDeactivate> {
  canDeactivate(component: ComponentCanDeactivate): boolean | Observable<boolean> {
    if(!component) {
      return true;
    }
    
    // If there are no pending changes, just allow deactivation; else confirm first
    return component.canDeactivate();
  }
}