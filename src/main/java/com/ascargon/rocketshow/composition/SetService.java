package com.ascargon.rocketshow.composition;

import org.springframework.stereotype.Service;

@Service
public interface SetService {

    Set getCurrentSet();

    void setCurrentSet(Set set);

    int getCurrentCompositionIndex();

    void setCurrentCompositionIndex(int compositionIndex);

    SetComposition getNextSetComposition();

    SetComposition getPreviousSetComposition();

    String getCurrentCompositionName();

}
