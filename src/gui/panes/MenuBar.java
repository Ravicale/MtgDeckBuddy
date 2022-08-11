package gui.panes;

import application.LogTags;
import database.CardDatabase;
import gui.Gui;
import gui.panes.models.CardTableFilter;
import org.tinylog.Logger;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.util.function.Consumer;

public class MenuBar extends JMenuBar {
	private static final FileFilter DEC_FILTER = new FileNameExtensionFilter("MTG Gen Decklist (.dec)", "dec");
	private static final FileFilter IMG_FILTER = new FileNameExtensionFilter("Portable Network Graphics (.PNG)", "png");
	private final JLabel deckSizeCounter = new JLabel("Deck Size - 0");
	private final JMenuItem readCollection;
	private final JMenuItem readDeck;
	private final JMenuItem readBanList;
	private final JMenuItem clearCollection;
	private final JMenuItem clearDeck;
	private final JMenuItem fillCollection;

	public MenuBar() {
		JMenu fileMenu = new JMenu("File");
		fileMenu.setMnemonic('f');
		readCollection = new JMenuItem("Import Collection");
		readCollection.addActionListener((e) -> importPopup(DEC_FILTER, JFileChooser.FILES_AND_DIRECTORIES, CardDatabase::readCollection));
		readCollection.setMnemonic('c');
		fileMenu.add(readCollection);
		readDeck = new JMenuItem("Import Deck");
		readDeck.addActionListener((e) -> importPopup(DEC_FILTER, JFileChooser.FILES_ONLY, CardDatabase::readDeck));
		readCollection.setMnemonic('d');
		fileMenu.add(readDeck);
		readBanList = new JMenuItem("Import Banlist");
		readBanList.addActionListener((e) -> importPopup(DEC_FILTER, JFileChooser.FILES_ONLY, CardDatabase::readBans));
		readBanList.setMnemonic('b');
		fileMenu.add(readBanList);
		JMenuItem exportDeck = new JMenuItem("Save Deck");
		exportDeck.addActionListener((e) -> exportPopup(DEC_FILTER, ".dec", CardDatabase::saveDeck));
		exportDeck.setMnemonic('e');
		fileMenu.add(exportDeck);
		JMenuItem exportImage = new JMenuItem("Export Deck Image");
		exportImage.addActionListener((e) -> exportPopup(IMG_FILTER, ".png", CardDatabase::saveDeckImage));
		exportImage.setMnemonic('e');
		fileMenu.add(exportImage);
		add(fileMenu);

		JMenu editMenu = new JMenu("Edit");
		editMenu.setMnemonic('e');
		clearCollection = new JMenuItem("Clear Collection");
		clearCollection.addActionListener((e) -> CardDatabase.clearCollection());
		clearCollection.setMnemonic('c');
		editMenu.add(clearCollection);
		fillCollection = new JMenuItem("Fill Collection");
		fillCollection.addActionListener((e) -> CardDatabase.fillCollection());
		fillCollection.setMnemonic('f');
		editMenu.add(fillCollection);
		clearDeck = new JMenuItem("Clear Deck");
		clearDeck.addActionListener((e) -> CardDatabase.clearDeck());
		clearDeck.setMnemonic('d');
		editMenu.add(clearDeck);
		add(editMenu);

		JMenu viewMenu = new JMenu("View");
		viewMenu.setMnemonic('v');
		JCheckBoxMenuItem viewCollection = new JCheckBoxMenuItem("Collection");
		viewCollection.addActionListener((e) -> CardTableFilter.setViewingCollection(viewCollection.isSelected()));
		viewCollection.setMnemonic('c');
		viewCollection.setSelected(true);
		viewMenu.add(viewCollection);
		JCheckBoxMenuItem viewDeck = new JCheckBoxMenuItem("Deck");
		viewDeck.addActionListener((e) -> CardTableFilter.setViewingDeck(viewDeck.isSelected()));
		viewDeck.setMnemonic('d');
		viewDeck.setSelected(true);
		viewMenu.add(viewDeck);
		JCheckBoxMenuItem viewUnowned = new JCheckBoxMenuItem("Unowned");
		viewUnowned.addActionListener((e) -> CardTableFilter.setViewingUnowned(viewUnowned.isSelected()));
		viewUnowned.setMnemonic('u');
		viewMenu.add(viewUnowned);
		JCheckBoxMenuItem viewBanned = new JCheckBoxMenuItem("Banned");
		viewBanned.addActionListener((e) -> CardTableFilter.setViewingBanned(viewBanned.isSelected()));
		viewBanned.setMnemonic('b');
		viewMenu.add(viewBanned);
		JCheckBoxMenuItem viewNonPlayable = new JCheckBoxMenuItem("Non-Playable");
		viewNonPlayable.addActionListener((e) -> CardTableFilter.setViewingNonPlayable(viewNonPlayable.isSelected()));
		viewNonPlayable.setMnemonic('p');
		viewMenu.add(viewNonPlayable);
		add(viewMenu);

		add(Box.createHorizontalGlue());
		add(deckSizeCounter);
	}

	public void setDeckSizeCounter(int value) {
		deckSizeCounter.setText("Deck Size - " + value);
	}

	public void setEnableDeckEditing(boolean enabled) {
		readBanList.setEnabled(enabled);
		readDeck.setEnabled(enabled);
		readCollection.setEnabled(enabled);
		clearCollection.setEnabled(enabled);
		fillCollection.setEnabled(enabled);
		clearDeck.setEnabled(enabled);
	}

	private void importPopup(FileFilter filter, int selectionMode, Consumer<File> action) {
		JFileChooser fileChooser = new JFileChooser(".");
		fileChooser.setFileSelectionMode(selectionMode);
		fileChooser.setFileFilter(filter);
		int result = fileChooser.showOpenDialog(Gui.getFrame());
		if (result == JFileChooser.APPROVE_OPTION) {
			File selection = fileChooser.getSelectedFile();
			if (selection.canRead()) {
				action.accept(selection);
			} else {
				Logger.tag(LogTags.USER_INPUT.tag).warn("Could not read file '{}'.", selection.getAbsolutePath());
				JOptionPane.showMessageDialog(Gui.getFrame(), "Could not read file.");
			}
		}
	}

	private void exportPopup(FileFilter filter, String extension, Consumer<File> action) {
		JFileChooser fileChooser = new JFileChooser(".");
		fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		fileChooser.setFileFilter(filter);
		int result = fileChooser.showSaveDialog(Gui.getFrame());
		if (result == JFileChooser.APPROVE_OPTION) {
			File selection = fileChooser.getSelectedFile();
			Logger.tag(LogTags.USER_INPUT.tag).info("Attempting to export deck to '{}'", selection.getAbsolutePath());
			if (!selection.getName().endsWith(extension)) {
				selection = new File(selection.getAbsolutePath() + extension);
			}

			if (selection.exists()) {
				if (!selection.delete()) {
					Logger.tag(LogTags.USER_INPUT.tag).warn("Could not remove exististing file '{}'.", selection.getAbsolutePath());
					JOptionPane.showMessageDialog(Gui.getFrame(), "Could not remove exististing file.");
					return;
				}
			}

			if (selection.getParentFile().canWrite()) {
				action.accept(selection);
			} else {
				Logger.tag(LogTags.USER_INPUT.tag).warn("Unable to write to selected file at '{}'.", selection.getAbsolutePath());
				JOptionPane.showMessageDialog(Gui.getFrame(), "No write permissions for selected file.");
			}
		}
	}
}
