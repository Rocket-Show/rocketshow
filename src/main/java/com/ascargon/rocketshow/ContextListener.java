package com.ascargon.rocketshow;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class ContextListener implements ServletContextListener {

	private Manager manager;
	
	@Override
	public void contextDestroyed(ServletContextEvent servletContextEvent) {
		// Destroy the manager
		manager.close();
	}

	@Override
	public void contextInitialized(ServletContextEvent servletContextEvent) {
		// Initialize the manager
		manager = new Manager();
		manager.load();
		
		servletContextEvent.getServletContext().setAttribute("manager", manager);
	}

}
