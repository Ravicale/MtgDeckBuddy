package gui.panes;

import application.LogTags;
import database.Card;
import gui.Gui;
import gui.UIConstants;
import gui.elements.ImageToggleButton;
import org.tinylog.Logger;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class CardInfoPane extends JPanel {

	private final JLabel cardFrontImage = new JLabel(UIConstants.DEFAULT_CARD_ICON);
	private final JLabel cardBackImage = new JLabel(UIConstants.DEFAULT_CARD_ICON);
	private final ImageToggleButton flipButton = new ImageToggleButton("flip", UIConstants.FLIP_BUTTON_SIZE);
	private Card currentCard;
	private final JButton addButton = new JButton("Add");
	private final JButton removeButton = new JButton("Remove");
	private boolean enableDeckEditing = false;


	public CardInfoPane() {
		GroupLayout layout = new GroupLayout(this);
		setLayout(layout);
		layout.setAutoCreateGaps(true);
		addButton.setPreferredSize(UIConstants.CARD_BUTTON_SIZE);
		addButton.addActionListener(this::addToDeck);

		removeButton.setPreferredSize(UIConstants.CARD_BUTTON_SIZE);
		removeButton.addActionListener(this::removeFromDeck);

		layout.setHorizontalGroup(layout.createSequentialGroup()
				.addGap(UIConstants.MARGIN)
				.addGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER)
						.addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
								.addComponent(flipButton)
								.addComponent(cardFrontImage)
								.addComponent(cardBackImage)
						)
						.addGroup(layout.createSequentialGroup()
								.addComponent(addButton)
								.addComponent(removeButton)
						)
				)
				.addGap(UIConstants.MARGIN)
		);

		layout.setVerticalGroup(layout.createSequentialGroup()
						.addGap(UIConstants.MARGIN)
						.addGroup(layout.createParallelGroup()
								.addComponent(addButton)
								.addComponent(removeButton)
						)
						.addGroup(layout.createParallelGroup(GroupLayout.Alignment.TRAILING)
								.addComponent(flipButton)
								.addComponent(cardFrontImage)
								.addComponent(cardBackImage)
						)
				//No margin, since there's another panel right below with its own margin.
		);

		addButton.setEnabled(false);
		removeButton.setEnabled(false);
		flipButton.addActionListener((e) -> {
			boolean isFacingFront = flipButton.isSelected();
			cardFrontImage.setVisible(!isFacingFront);
			cardBackImage.setVisible(isFacingFront);
		});
		cardBackImage.setVisible(false);
		flipButton.setVisible(false);
		setPreferredSize(UIConstants.CARD_INFO_PANE_SIZE);
		setMaximumSize(UIConstants.CARD_INFO_PANE_SIZE);
	}

	public void setCard(Card card, ImageIcon front, ImageIcon back) {
		Logger.tag(LogTags.UI_UPDATES.tag).info("Setting selected card to {} - #{} with {} and {}", card.getName(), card.id, front, back);
		if (front != null && back != null) {
			flipButton.setVisible(true);
		} else {
			flipButton.setVisible(false);
			front = front != null ? front : UIConstants.DEFAULT_CARD_ICON;
			back = back != null ? back : UIConstants.DEFAULT_CARD_ICON;
		}

		flipButton.setSelected(false);
		cardFrontImage.setIcon(front);
		cardBackImage.setIcon(back);
		cardFrontImage.setVisible(true);
		cardBackImage.setVisible(false);

		currentCard = card;
		tryEnableAddButton();
		tryEnableRemoveButton();
	}

	public void tryEnableAddButton() {
		addButton.setEnabled(enableDeckEditing && currentCard != null && currentCard.copiesInDeck() < currentCard.deckMax());
	}

	public void tryEnableRemoveButton() {
		removeButton.setEnabled(enableDeckEditing && currentCard != null && currentCard.copiesInDeck() > 0);
	}

	public void setEnableDeckEditing(boolean e) {
		enableDeckEditing = e;
		tryEnableAddButton();
		tryEnableRemoveButton();
	}

	private void addToDeck(ActionEvent actionEvent) {
		Logger.tag(LogTags.USER_INPUT.tag).info("Adding {} - #{} to deck.", currentCard.getName(), currentCard.id);
		currentCard.addToDeck(1);
		Gui.updateCardInfo(currentCard.id);
		tryEnableAddButton();
		tryEnableRemoveButton();
	}

	private void removeFromDeck(ActionEvent actionEvent) {
		Logger.tag(LogTags.USER_INPUT.tag).info("Removing {} - #{} from deck.", currentCard.getName(), currentCard.id);
		currentCard.addToDeck(-1);
		Gui.updateCardInfo(currentCard.id);
		tryEnableAddButton();
		tryEnableRemoveButton();
	}
}
