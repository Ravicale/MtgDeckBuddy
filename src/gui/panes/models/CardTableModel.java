package gui.panes.models;

import database.Card;
import database.CardDatabase;

import javax.swing.table.AbstractTableModel;

public class CardTableModel extends AbstractTableModel {
	@Override
	public int getRowCount() {
		return CardDatabase.getCardCount();
	}

	@Override
	public int getColumnCount() {
		return Card.DATA_FIELD_NAMES.length;
	}

	@Override
	public boolean isCellEditable(int x, int y) {
		return false;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		return CardDatabase.getCard(rowIndex).get(columnIndex);
	}

	@Override
	public String getColumnName(int columnNum) {
		return Card.DATA_FIELD_NAMES[columnNum];
	}
}
