package com.ascargon.rocketshow.update;

import lombok.Getter;
import lombok.Setter;

/**
 * Release change notes for the app.
 *
 * @author Moritz A. Vieli
 */
@Getter
@Setter
public class ChangeNote {

	private String version;
	private String changes;

}
