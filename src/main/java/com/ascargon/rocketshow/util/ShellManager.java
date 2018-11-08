package com.ascargon.rocketshow.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

public class ShellManager {

	private Process process;
	private PrintStream outStream;

	public ShellManager(String[] command) throws IOException {
		//process = Runtime.getRuntime().exec(command);
		process = new ProcessBuilder(command).redirectErrorStream(true).start();
		outStream = new PrintStream(process.getOutputStream());
	}

	public void sendCommand(String command, boolean newLine) {
		if (newLine) {
			outStream.println(command);
		} else {
			outStream.print(command);
		}
		outStream.flush();
	}

	public InputStream getInputStream() {
		return process.getInputStream();
	}

	public void close() {
		if (process != null) {
			process.destroy();
		}
	}

	public Process getProcess() {
		return process;
	}

	public void setProcess(Process process) {
		this.process = process;
	}

}
