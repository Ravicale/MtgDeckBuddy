package application;

import database.card.CardDatabase;
import gui.Gui;

public class MtgDeckBuddy {

	public static void main(String[] args) {
		LogTags.configureLogging();
		Gui.init();
		CardDatabase.initCardDatabase();
	}
}