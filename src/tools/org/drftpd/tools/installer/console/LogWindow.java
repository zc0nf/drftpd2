package org.drftpd.tools.installer.console;
/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

import charva.awt.BorderLayout;
import charva.awt.Color;
import charva.awt.Container;
import charva.awt.FlowLayout;
import charva.awt.event.ActionEvent;
import charva.awt.event.ActionListener;

import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PipedInputStream;

import charvax.swing.JButton;
import charvax.swing.JFileChooser;
import charvax.swing.JFrame;
import charvax.swing.JPanel;
import charvax.swing.JScrollPane;
import charvax.swing.JTextArea;
import charvax.swing.border.LineBorder;
import charvax.swing.border.TitledBorder;

import org.drftpd.tools.installer.FileLogger;
import org.drftpd.tools.installer.InstallerConfig;
import org.drftpd.tools.installer.PluginBuilder;
import org.drftpd.tools.installer.PluginBuilderThread;
import org.drftpd.tools.installer.UserFileLocator;

/**
 * @author djb61
 * @version $Id$
 */
public class LogWindow extends JFrame implements UserFileLocator {

	private boolean _fileLogEnabled;
	private boolean _suppressLog;
	private FileLogger _fileLog;
	private JButton _okButton;
	private JTextArea _logArea;
	private BufferedReader _logReader;
	private PipedInputStream _logInput;
	private PluginBuilder _builder;

	public LogWindow(PipedInputStream logInput, InstallerConfig config) {
		super("Build Log");
		_fileLogEnabled = config.getFileLogging();
		_suppressLog = config.getSuppressLog();
		_logInput = logInput;
		Container contentPane = getContentPane();
		contentPane.setLayout(new BorderLayout());
		JPanel centerPanel = new JPanel();
		BorderLayout centerLayout = new BorderLayout();
		centerPanel.setLayout(centerLayout);
		_logArea = new JTextArea();
		_logArea.setLineWrap(true);
		_logArea.setEditable(false);
		if (_suppressLog) {
			_logArea.setText("LOGGING SUPPRESSED");
		} else {
			_logArea.setText("");
		}
		JScrollPane logPane = new JScrollPane(_logArea);
		TitledBorder pluginBorder = new TitledBorder(new LineBorder(Color.white));
		pluginBorder.setTitle("Plugin Build Log");
		logPane.setViewportBorder(pluginBorder);
		_logArea.setColumns(76);
		_logArea.setRows(13);
		centerPanel.add(logPane, BorderLayout.CENTER);
		contentPane.add(centerPanel, BorderLayout.CENTER);
		JPanel southPanel = new JPanel();
		FlowLayout southLayout = new FlowLayout();
		southLayout.setAlignment(FlowLayout.CENTER);
		southPanel.setLayout(southLayout);
		_okButton = new JButton();
		_okButton.setText("OK");
		_okButton.setEnabled(false);
		_okButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setVisible(false);
			}
		});
		southPanel.add(_okButton);
		contentPane.add(southPanel, BorderLayout.SOUTH);
		setSize(80,18);
		setLocation(0,2);
		validate();
	}

	public void setBuilder(PluginBuilder builder) {
		_builder = builder;
	}

	public void init() throws IOException {
		if (_fileLogEnabled) {
			_fileLog = new FileLogger();
			_fileLog.init();
		}
		_logReader = new BufferedReader(new InputStreamReader(_logInput));
		new Thread(new ReadingThread()).start();
		new Thread(new PluginBuilderThread(_builder)).start();
		setVisible(true);
	}

	public String getUserDir() {
		JFileChooser userFileChooser = new JFileChooser(new File(System.getProperty("user.dir")));
		userFileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		userFileChooser.setDialogTitle("Select directory DrFTPd 2.0 is installed in");
		int result = userFileChooser.showOpenDialog(this);
		if (result == JFileChooser.APPROVE_OPTION) {
			return userFileChooser.getSelectedFile().getPath();
		} else {
			return null;
		}
	}

	private class ReadingThread implements Runnable {

		public void run() {
			try {
				String logLine = null;
				do {
					logLine = _logReader.readLine();
					if (logLine != null) {
						if (_fileLogEnabled) {
							_fileLog.writeLog(logLine);
						}
						if (!_suppressLog) {
							_logArea.append(logLine+"\n");
						}
					}
				} while(logLine != null);
			} catch (Exception e) {
				// Ignore
			} finally {
				// cleanup
				if (_fileLogEnabled) {
					_fileLog.cleanup();
				}
				try {
					_logReader.close();
				} catch (IOException e) {
					// already closed
				}
				try {
					_logInput.close();
				} catch (IOException e) {
					// already closed
				}
				// build thread has finished, enable button
				_okButton.setEnabled(true);
			}
		}
	}
}

