import {
  AfterViewInit,
  Directive,
  ElementRef,
  EventEmitter,
  Input,
  NgZone,
  OnDestroy,
  Output,
} from "@angular/core";
import Sortable, { Options } from "sortablejs";

interface SortableReorderEvent {
  item?: HTMLElement;
  oldIndex?: number | null;
  newIndex?: number | null;
  oldDraggableIndex?: number | null;
  newDraggableIndex?: number | null;
}

const DEFAULT_OPTIONS: Options = {
  animation: 150,
  draggable: ">*:not(.no-sortjs)",
  filter: ".no-sortjs, .no-sortjs *",
  handle: ".list-sort-handle",
  preventOnFilter: false,
};

@Directive({
  selector: "[sortablejs]",
  standalone: false,
})
export class SortablejsDirective implements AfterViewInit, OnDestroy {
  @Input() sortablejs: any[] | undefined | null;
  @Input() sortablejsContainer: string | undefined;
  @Input() sortablejsOptions: Options | undefined;
  @Output() sortablejsInit = new EventEmitter<any>();

  private sortableInstance: any;
  private createTimer: ReturnType<typeof setTimeout> | undefined;

  constructor(
    private element: ElementRef<HTMLElement>,
    private zone: NgZone
  ) {}

  ngAfterViewInit(): void {
    const container = this.sortablejsContainer
      ? this.element.nativeElement.querySelector(this.sortablejsContainer)
      : this.element.nativeElement;

    if (!(container instanceof HTMLElement)) {
      return;
    }

    this.createTimer = setTimeout(() => {
      this.zone.runOutsideAngular(() => {
        this.sortableInstance = Sortable.create(container, this.options);
      });

      this.sortablejsInit.emit(this.sortableInstance);
    }, 0);
  }

  ngOnDestroy(): void {
    if (this.createTimer) {
      clearTimeout(this.createTimer);
    }

    this.sortableInstance?.destroy();
  }

  private get options(): Options {
    const { onMove: _onMove, ...userOptions } = this.sortablejsOptions || {};
    const userOnEnd = userOptions.onEnd;
    const userOnUpdate = userOptions.onUpdate;

    return {
      ...userOptions,
      ...DEFAULT_OPTIONS,
      onEnd: (event: SortableReorderEvent) => {
        if (userOnEnd) {
          this.zone.run(() => userOnEnd(event));
        }
      },
      onUpdate: (event: SortableReorderEvent) => {
        this.zone.run(() => {
          this.restoreDomOrder(event);
          this.reorder(event);
          userOnUpdate?.(event);
        });
      },
    };
  }

  private reorder(event: SortableReorderEvent): void {
    if (!this.sortablejs) {
      return;
    }

    const oldIndex = this.indexFromEvent(event, "old");
    const newIndex = this.indexFromEvent(event, "new");

    if (
      oldIndex === undefined ||
      oldIndex === null ||
      newIndex === undefined ||
      newIndex === null ||
      oldIndex === newIndex ||
      oldIndex < 0 ||
      newIndex < 0 ||
      oldIndex >= this.sortablejs.length ||
      newIndex >= this.sortablejs.length
    ) {
      return;
    }

    const [movedItem] = this.sortablejs.splice(oldIndex, 1);
    this.sortablejs.splice(newIndex, 0, movedItem);
  }

  private restoreDomOrder(event: SortableReorderEvent): void {
    const oldIndex = this.indexFromEvent(event, "old");
    const item = event.item;
    const container = item?.parentElement;

    if (
      !item ||
      !container ||
      oldIndex === undefined ||
      oldIndex === null ||
      oldIndex < 0
    ) {
      return;
    }

    const draggableChildren = Array.from(container.children).filter(
      (child): child is HTMLElement =>
        child instanceof HTMLElement &&
        child !== item &&
        !child.classList.contains("no-sortjs")
    );
    const firstNoSortItem = Array.from(container.children).find(
      (child): child is HTMLElement =>
        child instanceof HTMLElement && child.classList.contains("no-sortjs")
    );
    const referenceNode = draggableChildren[oldIndex] || firstNoSortItem || null;

    container.insertBefore(item, referenceNode);
  }

  private indexFromEvent(
    event: SortableReorderEvent,
    direction: "old" | "new"
  ): number | null | undefined {
    const draggableIndex =
      direction === "old" ? event.oldDraggableIndex : event.newDraggableIndex;
    const index = direction === "old" ? event.oldIndex : event.newIndex;

    return draggableIndex ?? index;
  }
}
