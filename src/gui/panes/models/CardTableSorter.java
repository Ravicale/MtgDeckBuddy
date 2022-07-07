package gui.panes.models;

import gui.panes.CardListPane;

import javax.swing.table.TableRowSorter;
import java.util.Comparator;

public class CardTableSorter extends TableRowSorter<CardTableModel> {
	public CardTableSorter(CardListPane pane, CardTableModel model) {
		super(model);
		addRowSorterListener((e) -> pane.updatePrefetching());
	}

	@Override
	public Comparator<?> getComparator(int column) {
		return (a, b) -> ((Comparable) a).compareTo(b);
	}

	@Override
	protected boolean useToString(int column) {
		return false;
	}
}
