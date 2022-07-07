package gui.panes;

import database.Card;
import database.CardDatabase;
import database.ImageStore;
import gui.UIConstants;
import gui.panes.models.CardTableFilter;
import gui.panes.models.CardTableModel;
import gui.panes.models.CardTableSorter;
import org.tinylog.Logger;

import javax.swing.*;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CardListPane extends JPanel {
	private final JTable cardTable;
	private final JProgressBar busyIndicator;
	private CardTableSorter sorter;
	private CardTableModel model;
	private RowFilter<CardTableModel, Integer> cachedFilter;

	private static final Object[][] defaultTable;

	static {
		defaultTable = new Object[1][Card.DATA_FIELD_NAMES.length];
		Arrays.fill(defaultTable[0], "");
	}

	public CardListPane() {
		cardTable = new JTable(defaultTable, Card.DATA_FIELD_NAMES);
		cardTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		cardTable.setAutoCreateColumnsFromModel(false);
		cardTable.setEnabled(false);
		cardTable.setShowHorizontalLines(true);
		cardTable.setMinimumSize(UIConstants.CARD_LIST_PANE_MIN_SIZE);
		setMinimumSize(UIConstants.CARD_LIST_PANE_MIN_SIZE);
		JTableHeader header = cardTable.getTableHeader();
		header.setReorderingAllowed(true);
		header.setResizingAllowed(false);
		JScrollPane tableScrollPane = new JScrollPane(cardTable);
		tableScrollPane.getVerticalScrollBar().addAdjustmentListener((e) -> updatePrefetching());

		TableColumnModel columnModel = cardTable.getColumnModel();
		for (int i = 0; i < columnModel.getColumnCount(); i++) {
			TableColumn column = columnModel.getColumn(i);
			column.setMinWidth(UIConstants.DEFAULT_COLUMN_SIZES[i]);
			column.setMaxWidth(UIConstants.MAX_COLUMN_SIZES[i]);
		}

		busyIndicator = new JProgressBar();
		busyIndicator.setOrientation(JProgressBar.HORIZONTAL);
		busyIndicator.setBorderPainted(false);
		busyIndicator.setVisible(false);
		busyIndicator.setIndeterminate(true);

		GroupLayout layout = new GroupLayout(this);
		setLayout(layout);

		layout.setVerticalGroup(layout.createSequentialGroup()
				.addGap(UIConstants.MARGIN)
				.addComponent(tableScrollPane)
				.addComponent(busyIndicator)
				.addGap(UIConstants.MARGIN)
		);

		layout.setHorizontalGroup(layout.createSequentialGroup()
				.addGap(UIConstants.MARGIN)
				.addGroup(layout.createParallelGroup()
						.addComponent(tableScrollPane)
						.addComponent(busyIndicator)
				)
				.addGap(UIConstants.MARGIN)
		);

		cardTable.getSelectionModel().addListSelectionListener((e) -> {
			int selectedIndex = cardTable.getSelectedRow();
			if (selectedIndex >= 0 && selectedIndex < cardTable.getRowCount()) {
				selectedIndex = cardTable.convertRowIndexToModel(selectedIndex);
				Card card = CardDatabase.getCard(selectedIndex);
				CardDatabase.loadAndDisplayImage(card);
			}
		});
	}

	public void updatePrefetching() {
		if (model != null && model.getRowCount() > 1) {
			List<Card> visibleCards = new ArrayList<>();
			Rectangle viewport = cardTable.getVisibleRect();
			int firstRow = cardTable.rowAtPoint(viewport.getLocation());
			if (firstRow == -1) {
				return;
			}
			viewport.translate(0, viewport.height);
			int visibleRows = cardTable.rowAtPoint(viewport.getLocation()) - firstRow;
			int lastRow = (visibleRows > 0) ? visibleRows+firstRow : cardTable.getRowCount() - 1;
			Logger.debug("First row = {}, Visible rows = {}, Last row = {}", firstRow, visibleRows, lastRow);
			for(int row=firstRow; row <= lastRow; row++) {
				int id = cardTable.convertRowIndexToModel(row);
				visibleCards.add(CardDatabase.getCard(id));
			}

			ImageStore.setPrefetchList(visibleCards);
		}
	}

	public void initTable() {
		model = new CardTableModel();
		sorter = new CardTableSorter(this, model);
		if (cachedFilter != null) {
			sorter.setRowFilter(cachedFilter);
		} else {
			CardTableFilter.setFilterList(null);
		}
		cachedFilter = null;
		cardTable.setRowSorter(sorter);
		cardTable.setModel(model);
		cardTable.setEnabled(true);
	}

	public void setBusy(boolean isBusy) {
		if (!busyIndicator.isIndeterminate()) {
			busyIndicator.setIndeterminate(true);
		}
		busyIndicator.setVisible(isBusy);
	}

	public void setFilter(RowFilter<CardTableModel, Integer> filter) {
		if (sorter == null) {
			cachedFilter = filter;
		} else {
			sorter.setRowFilter(filter);
		}
	}

	public void updateRow(int id) {
		if (model != null) {
			Logger.info("Updating table row for card #{}.", id);
			model.fireTableRowsUpdated(id, id);
			cardTable.repaint();
		} else {
			Logger.error("Attempted to update a card row when the card table has not been fully initialized.");
		}
	}

	public void updateTable() {
		if (model != null) {
			model.fireTableDataChanged();
			cardTable.repaint();
		} else {
			Logger.error("Attempted to update a card row when the card table has not been fully initialized.");
		}
	}
}
