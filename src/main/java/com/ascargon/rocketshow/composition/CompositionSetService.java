package com.ascargon.rocketshow.composition;

import com.ascargon.rocketshow.PlayerService;
import com.ascargon.rocketshow.api.NotificationService;

public class CompositionSetService implements SetService {

    private PlayerService playerService;
    private NotificationService notificationService;
    private Set currentSet;
    private int currentCompositionIndex;

    public CompositionSetService(NotificationService notificationService, PlayerService playerService) {
        this.notificationService = notificationService;
        this.playerService = playerService;

        currentCompositionIndex = 0;
    }

    // Read the current composition from its file and set it to the player
    private void readCurrentComposition() throws Exception {
        if (currentSet == null) {
            return;
        }

        if (currentCompositionIndex >= currentSet.getSetCompositionList().size()) {
            return;
        }

        // Load the current composition into the player
        SetComposition currentSetComposition = currentSet.getSetCompositionList().get(currentCompositionIndex);

        playerService.setCompositionName(currentSetComposition.getName());
        playerService.setAutoStartNextComposition(currentSetComposition.isAutoStartNextComposition());
    }

    @Override
    public Set getCurrentSet() {
        return null;
    }

    @Override
    public void setCurrentSet(Set set) {

    }

    @Override
    public void setCompositionIndex(int compositionIndex, boolean playDefaultComposition) throws Exception {
        // Stop a playing composition if needed
        playerService.stop(playDefaultComposition);

        // Return, if we already have the correct composition set
        if (currentCompositionIndex == compositionIndex) {
            return;
        }

        currentCompositionIndex = compositionIndex;

        // Load the new composition
        readCurrentComposition();

        notificationService.notifyClients();
    }

    @Override
    public void setCompositionIndex(int compositionIndex) throws Exception {
        setCompositionIndex(compositionIndex, true);
    }

    @Override
    public int getCurrentCompositionIndex() {
        return 0;
    }

    @Override
    public void setCurrentCompositionIndex(int currentCompositionIndex) {

    }

    @Override
    public void nextComposition(boolean playDefaultComposition) throws Exception {
        if(currentSet == null) {
            return;
        }

        int newIndex = currentCompositionIndex + 1;

        if (newIndex >= currentSet.getSetCompositionList().size()) {
            return;
        }

        setCompositionIndex(newIndex, playDefaultComposition);
    }

    @Override
    public boolean hasNextComposition() {
        if(currentSet == null) {
            return false;
        }

        return currentCompositionIndex + 1 < currentSet.getSetCompositionList().size();
    }

    @Override
    public void nextComposition() throws Exception {
        nextComposition(true);
    }

    @Override
    public void previousComposition() throws Exception {
        int newIndex = currentCompositionIndex - 1;

        if (newIndex < 0) {
            return;
        }

        setCompositionIndex(newIndex);
    }

}
