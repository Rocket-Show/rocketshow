package com.ascargon.rocketshow;

import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class Session {

    private String currentSetName;
    private boolean firstStart = true;
    private boolean updateFinished = false;
    private boolean autoSelectNextComposition = false;

    public String getCurrentSetName() {
        return currentSetName;
    }

    public void setCurrentSetName(String currentSetName) {
        this.currentSetName = currentSetName;
    }

    public boolean isFirstStart() {
        return firstStart;
    }

    public void setFirstStart(boolean firstStart) {
        this.firstStart = firstStart;
    }

    public boolean isUpdateFinished() {
        return updateFinished;
    }

    public void setUpdateFinished(boolean updateFinished) {
        this.updateFinished = updateFinished;
    }

    public boolean isAutoSelectNextComposition() {
        return autoSelectNextComposition;
    }

    public void setAutoSelectNextComposition(boolean autoSelectNextComposition) {
        this.autoSelectNextComposition = autoSelectNextComposition;
    }

}
