package com.ascargon.rocketshow.composition;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.log4j.Logger;

import com.ascargon.rocketshow.Manager;

@XmlRootElement
public class Set {

	final static Logger logger = Logger.getLogger(Set.class);

	private String name;

	private List<SetComposition> setCompositionList = new ArrayList<SetComposition>();

	private int currentCompositionIndex;

	private Manager manager;

	private String notes;

	public Set() {
		currentCompositionIndex = 0;
	}

	// Read the current composition from its file
	public void readCurrentComposition() throws Exception {
		if (currentCompositionIndex >= setCompositionList.size()) {
			return;
		}

		// Load the current composition into the player
		SetComposition currentSetComposition = setCompositionList.get(currentCompositionIndex);

		manager.getPlayer()
				.setComposition(manager.getCompositionManager().loadComposition(currentSetComposition.getName()));
		manager.getPlayer().setAutoStartNextComposition(currentSetComposition.isAutoStartNextComposition());
	}

	// Return only the set-relevant information of the composition (e.g. to save
	// to a file)
	@XmlElement(name = "composition")
	@XmlElementWrapper(name = "compositionList")
	public List<SetComposition> getSetCompositionList() {
		return setCompositionList;
	}

	public void nextComposition(boolean playDefaultComposition) throws Exception {
		int newIndex = currentCompositionIndex + 1;

		if (newIndex >= setCompositionList.size()) {
			return;
		}

		setCompositionIndex(newIndex, playDefaultComposition);
	}

	public boolean hasNextComposition() {
		int newIndex = currentCompositionIndex + 1;

		if (newIndex >= setCompositionList.size()) {
			return false;
		}

		return true;
	}

	public void nextComposition() throws Exception {
		nextComposition(true);
	}

	public void previousComposition() throws Exception {
		int newIndex = currentCompositionIndex - 1;

		if (newIndex < 0) {
			return;
		}

		setCompositionIndex(newIndex);
	}

	public void close() throws Exception {
		// Nothing to do at the moment
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@XmlTransient
	public Manager getManager() {
		return manager;
	}

	public void setManager(Manager manager) {
		this.manager = manager;
	}

	@XmlElement
	public int getCurrentCompositionIndex() {
		return currentCompositionIndex;
	}

	// For the XML load to work
	public void setCurrentCompositionIndex(int currentCompositionIndex) throws Exception {
		this.setCompositionIndex(currentCompositionIndex);
	}

	@XmlTransient
	public String getCurrentCompositionName() {
		if (setCompositionList.size() == 0) {
			return null;
		}

		return setCompositionList.get(currentCompositionIndex).getName();
	}

	public void setCompositionIndex(int compositionIndex, boolean playDefaultComposition) throws Exception {
		// Stop a playing composition if needed
		if (manager != null) {
			manager.getPlayer().stop(playDefaultComposition, true);
		}

		// Return, if we already have the correct composition set
		if (currentCompositionIndex == compositionIndex) {
			return;
		}

		currentCompositionIndex = compositionIndex;

		// Load the new composition
		readCurrentComposition();

		if (manager != null) {
			if (manager.getStateManager() != null) {
				manager.getStateManager().notifyClients();
			}
		}

		if (manager != null) {
			// Save the set list to remember the current composition index (e.g.
			// after a reboot)
			manager.getCompositionManager().saveSet(this, false);
		}

		logger.info("Set composition index " + currentCompositionIndex);
	}

	public void setCompositionIndex(int compositionIndex) throws Exception {
		setCompositionIndex(compositionIndex, true);
	}

	public String getNotes() {
		return notes;
	}

	public void setNotes(String notes) {
		this.notes = notes;
	}

}
