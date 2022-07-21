package application;

import database.CardDatabase;
import gui.Gui;

public class MtgDeckBuddy {

	public static void main(String[] args) {
		org.tinylog.configuration.Configuration.set("writingthread", "true"); //Make tinylog use its own thread for that sick speed boost.
		org.tinylog.configuration.Configuration.set("writer.level", "info");
		Gui.init();
		CardDatabase.initCardDatabase();
	}
}