package com.ascargon.rocketshow.composition;

import com.ascargon.rocketshow.PlayerService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Handle loading and saving of compositions and sets.
 */
@Service
public interface CompositionService {

    Composition cloneComposition(Composition composition) throws Exception;

    Composition getComposition(String name);

    Set getSet(String name);

    List<Composition> getAllCompositions();

    List<Set> getAllSets();

    void loadAllCompositions();

    void loadAllSets();

    void saveComposition(Composition composition) throws Exception;

    void saveSet(Set set, boolean checkCompositions) throws Exception;

    void saveSet(Set set) throws Exception;

    void deleteComposition(String name, PlayerService playerService) throws Exception;

    void deleteSet(String name);

    Composition getNextComposition(Composition currentComposition);

    Composition getPreviousComposition(Composition currentComposition);

}
