package com.ascargon.rocketshow.composition;

public interface SetService {

    Set getCurrentSet();

    void setCurrentSet(Set set);

    void readCurrentComposition() throws Exception;

    void setCompositionIndex(int compositionIndex, boolean playDefaultComposition) throws Exception;

    void setCompositionIndex(int compositionIndex) throws Exception;

    int getCurrentCompositionIndex();

    void setCurrentCompositionIndex(int currentCompositionIndex);

    boolean hasNextComposition();

    void nextComposition(boolean playDefaultComposition) throws Exception;

    void nextComposition() throws Exception;

    void previousComposition() throws Exception;

}
