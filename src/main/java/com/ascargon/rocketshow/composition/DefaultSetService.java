package com.ascargon.rocketshow.composition;

import org.springframework.stereotype.Service;

@Service
public class DefaultSetService implements SetService {

    private Set currentSet;
    private int currentCompositionIndex;

    public DefaultSetService() {
        currentCompositionIndex = 0;
    }

    @Override
    public Set getCurrentSet() {
        return currentSet;
    }

    @Override
    public void setCurrentSet(Set set) {
        this.currentSet = set;
    }

    @Override
    public int getCurrentCompositionIndex() {
        return currentCompositionIndex;
    }

    @Override
    public void setCurrentCompositionIndex(int currentCompositionIndex) {
        this.currentCompositionIndex = currentCompositionIndex;
    }

    @Override
    public SetComposition getNextSetComposition() {
        if (currentSet == null || currentSet.getSetCompositionList().size() == 0) {
            return null;
        }

        int newIndex = currentCompositionIndex + 1;

        if (newIndex >= currentSet.getSetCompositionList().size()) {
            return null;
        }

        return currentSet.getSetCompositionList().get(newIndex);
    }

    @Override
    public SetComposition getPreviousSetComposition() {
        if (currentSet == null || currentSet.getSetCompositionList().size() == 0) {
            return null;
        }

        int newIndex = currentCompositionIndex - 1;

        if (newIndex < 0) {
            return null;
        }

        return currentSet.getSetCompositionList().get(newIndex);
    }

    @Override
    public String getCurrentCompositionName() {
        if(currentSet == null || currentCompositionIndex >= currentSet.getSetCompositionList().size()) {
            return null;
        }

        return currentSet.getSetCompositionList().get(currentCompositionIndex).getName();
    }

}
