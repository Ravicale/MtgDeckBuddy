package gui;

import com.formdev.flatlaf.FlatDarkLaf;
import database.Card;
import gui.panes.CardFilterPane;
import gui.panes.CardInfoPane;
import gui.panes.CardListPane;
import gui.panes.MenuBar;
import gui.panes.models.CardTableModel;
import org.tinylog.Logger;

import javax.swing.*;
import java.util.concurrent.CountDownLatch;

public class Gui {
	private static CardListPane cardListGui;
	private static CardInfoPane cardInfoGui;
	private static CardFilterPane cardFilterGui;
	private static MenuBar menuBar;
	private static JFrame frame;

	private static final CountDownLatch guiAvailable = new CountDownLatch(1);

	private Gui() {
	}

	public static void init() {
		Logger.info("Initializing GUI.");
		FlatDarkLaf.setup();
		SwingUtilities.invokeLater(() -> {
			cardFilterGui = new CardFilterPane();
			cardListGui = new CardListPane();
			cardInfoGui = new CardInfoPane();
			frame = new JFrame("MTG Deck Buddy");
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

			GroupLayout layout = new GroupLayout(frame.getContentPane());
			frame.getContentPane().setLayout(layout);

			layout.setHorizontalGroup(layout.createSequentialGroup()
					.addComponent(cardListGui)
					.addGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER)
							.addComponent(cardInfoGui)
							.addComponent(cardFilterGui)
					)
			);

			layout.setVerticalGroup(layout.createParallelGroup()
					.addComponent(cardListGui)
					.addGroup(layout.createSequentialGroup()
							.addComponent(cardInfoGui)
							.addComponent(cardFilterGui)
					)
			);

			menuBar = new MenuBar();
			frame.setJMenuBar(menuBar);

			//frame.setMinimumSize(UIConstants.WINDOW_MIN_SIZE);
			frame.pack();
			frame.setMinimumSize(frame.getSize());
			frame.setVisible(true);
			guiAvailable.countDown();
			Logger.info("GUI Initialized.");
		});
	}

	public static void setBusyLoading(boolean isBusy) {
		SwingUtilities.invokeLater(() -> {
			if (guiAvailable.getCount() == 0) {
				cardListGui.setBusy(isBusy);
			} else {
				setBusyLoading(isBusy);
			}
		});
	}

	public static void setDeckSize(int value) {
		SwingUtilities.invokeLater(() -> menuBar.setDeckSizeCounter(value));
	}

	public static void initializeCardListTable() {
		SwingUtilities.invokeLater(() -> cardListGui.initTable());
	}

	public static void setSelectedCard(Card card, ImageIcon front, ImageIcon back) {
		SwingUtilities.invokeLater(() -> cardInfoGui.setCard(card, front, back));
	}

	public static void setCardFilter(RowFilter<CardTableModel, Integer> filter) {
		SwingUtilities.invokeLater(() -> cardListGui.setFilter(filter));
	}

	public static void updateCardInfo(int id) {
		SwingUtilities.invokeLater(() -> cardListGui.updateRow(id));
	}

	public static void lockDeck() {
		Object lock = new Object();
		SwingUtilities.invokeLater(() -> {
			Logger.info("Locking deck editing.");
			cardInfoGui.setEnableDeckEditing(false);
			menuBar.setEnableDeckEditing(false);
			cardListGui.setBusy(true);
			synchronized (lock) {
				lock.notifyAll();
			}
		});
		try {
			synchronized (lock) {
				lock.wait();
			}
		} catch (InterruptedException e) {
			//Nothing to do.
		}
	}

	public static void unlockDeck() {
		Object lock = new Object();
		SwingUtilities.invokeLater(() -> {
			Logger.info("Unlocking deck editing.");
			cardInfoGui.setEnableDeckEditing(true);
			menuBar.setEnableDeckEditing(true);
			cardListGui.setBusy(false);
			cardListGui.updateTable();
			cardInfoGui.tryEnableAddButton();
			cardInfoGui.tryEnableRemoveButton();
			synchronized (lock) {
				lock.notifyAll();
			}
		});
		try {
			synchronized (lock) {
				lock.wait();
			}
		} catch (InterruptedException e) {
			//Nothing to do.
		}
	}

	public static JFrame getFrame() {
		if (!SwingUtilities.isEventDispatchThread()) {
			try {
				guiAvailable.await();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		}
		return frame;
	}
}
