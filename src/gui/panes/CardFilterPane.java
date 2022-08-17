package gui.panes;

import database.card.Card;
import database.card.CardColor;
import gui.UIConstants;
import gui.elements.ImageToggleButton;
import gui.elements.SearchBox;
import gui.panes.models.CardTableFilter;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.*;
import java.util.function.Predicate;

public class CardFilterPane extends JPanel {
	private final Map<CardColor, ImageToggleButton> colorButtons;

	private final SearchBox nameField = new SearchBox("Name");
	private final SearchBox keywordField = new SearchBox("Keywords");
	private final SearchBox typeField = new SearchBox("Type");
	private final SearchBox textField = new SearchBox("Card Text");

	public CardFilterPane() {
		JButton searchButton = new JButton("Search");
		searchButton.addActionListener(this::filter);

		JButton resetButton = new JButton("Reset");
		resetButton.addActionListener(this::reset);

		GroupLayout layout = new GroupLayout(this);
		setLayout(layout);
		layout.setAutoCreateGaps(true);

		KeyListener enterButtonListener = new KeyListener() {
			@Override
			public void keyTyped(KeyEvent e) {
				if (e.getKeyChar() == '\n') {
					filter(null);
				}
			}

			@Override
			public void keyPressed(KeyEvent e) {
			}

			@Override
			public void keyReleased(KeyEvent e) {
			}
		};

		nameField.addKeyListener(enterButtonListener);
		keywordField.addKeyListener(enterButtonListener);
		typeField.addKeyListener(enterButtonListener);
		textField.addKeyListener(enterButtonListener);

		GroupLayout.SequentialGroup manaHorizontalLayout = layout.createSequentialGroup();
		manaHorizontalLayout.addGap(UIConstants.MARGIN);
		GroupLayout.ParallelGroup manaVerticalLayout = layout.createParallelGroup();
		Map<CardColor, ImageToggleButton> mutColorButtons = new EnumMap<>(CardColor.class);
		for (CardColor color : CardColor.values()) {
			ImageToggleButton colorButton = new ImageToggleButton(color);
			mutColorButtons.put(color, colorButton);
			manaHorizontalLayout.addComponent(colorButton);
			manaVerticalLayout.addComponent(colorButton);
		}
		colorButtons = Collections.unmodifiableMap(mutColorButtons);
		manaHorizontalLayout.addGap(UIConstants.MARGIN);

		layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.CENTER)
				.addGroup(manaHorizontalLayout)
				.addGroup(
						layout.createSequentialGroup()
								.addGap(UIConstants.MARGIN)
								.addGroup(layout.createParallelGroup()
										.addComponent(nameField)
										.addComponent(keywordField)
										.addComponent(typeField)
										.addComponent(textField)
								)
								.addGap(UIConstants.MARGIN)
				)
				.addGroup(
						layout.createSequentialGroup()
								.addComponent(searchButton)
								.addComponent(resetButton)
				)
		);

		layout.setVerticalGroup(layout.createSequentialGroup()
				.addGap(UIConstants.MARGIN)
				.addGroup(manaVerticalLayout)
				.addComponent(nameField)
				.addComponent(keywordField)
				.addComponent(typeField)
				.addComponent(textField)
				.addGroup(layout.createParallelGroup()
						.addComponent(searchButton)
						.addComponent(resetButton)
				)
				.addGap(UIConstants.MARGIN, Integer.MAX_VALUE, Integer.MAX_VALUE)
		);

		setPreferredSize(UIConstants.CARD_FILTER_PANE_SIZE);
		setMaximumSize(UIConstants.CARD_FILTER_PANE_SIZE);
	}

	private void reset(ActionEvent actionEvent) {
		for (ImageToggleButton button : colorButtons.values()) {
			button.setSelected(false);
		}

		nameField.setText("");
		typeField.setText("");
		keywordField.setText("");
		textField.setText("");
		CardTableFilter.setFilterList(null);
	}

	private void filter(ActionEvent actionEvent) {
		List<Predicate<Card>> filters = new ArrayList<>(8);
		Set<CardColor> selectedColors = new HashSet<>(6);
		for (Map.Entry<CardColor, ImageToggleButton> colorEntry : colorButtons.entrySet()) {
			if (colorEntry.getValue().isSelected()) {
				selectedColors.add(colorEntry.getKey());
			}
		}

		if (!selectedColors.isEmpty()) {
			filters.add(CardTableFilter.createManaFilter(selectedColors));
		}

		if (!nameField.getText().isEmpty()) {
			filters.add(CardTableFilter.createNameFilter(nameField.getText()));
		}

		if (!typeField.getText().isEmpty()) {
			filters.add(CardTableFilter.createTypeFilter(typeField.getText()));
		}

		if (!keywordField.getText().isEmpty()) {
			filters.add(CardTableFilter.createKeywordFilter(keywordField.getText()));
		}

		if (!textField.getText().isEmpty()) {
			filters.add(CardTableFilter.createTextFilter(textField.getText()));
		}

		CardTableFilter.setFilterList(filters);
	}
}
