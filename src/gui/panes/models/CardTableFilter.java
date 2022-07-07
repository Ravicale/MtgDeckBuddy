package gui.panes.models;


import database.Card;
import database.CardColor;
import database.CardDatabase;
import gui.Gui;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class CardTableFilter {
	private CardTableFilter() {}

	private static boolean viewingDeck = true;
	private static boolean viewingCollection = true;
	private static boolean viewingUnowned = false;
	private static boolean viewingBanned = false;
	private static boolean viewingNonPlayable = false;
	private static List<Predicate<Card>> currentFilters;

	public static Predicate<Card> createManaFilter(Set<CardColor> colors) {
		return (card) -> {
			if (colors.contains(CardColor.COLORLESS)) { //OR
				if (colors.size() == 1) {
					return card.colorIdentity.contains(CardColor.COLORLESS);
				}

				for (CardColor color : colors) {
					if (card.colorIdentity.contains(color)) {
						return true;
					}
				}

				return false;
			} else { //AND
				return card.colorIdentity.size() == colors.size() && colors.containsAll(card.colorIdentity);
			}
		};
	}

	public static Predicate<Card> createNameFilter(String query) {
		if (query.startsWith("REGEX:")) {
			Pattern regex = Pattern.compile(query.toLowerCase().substring("REGEX:".length()));
			return (card -> regex.matcher(card.getName().toLowerCase()).find());
		} else if (query.startsWith("EQUALS:")) {
			String squery = query.substring("EQUALS:".length()).toLowerCase();
			return (card -> card.getName().equalsIgnoreCase(squery));
		} else {
			String lquery = query.toLowerCase();
			return (card) -> card.getName().toLowerCase().contains(lquery);
		}
	}

	public static Predicate<Card> createTextFilter(String query) {
		if (query.startsWith("REGEX:")) {
			Pattern regex = Pattern.compile(query.toLowerCase().substring("REGEX:".length()));
			return (card -> regex.matcher(card.cardText).find());
		} else if (query.startsWith("EQUALS:")) {
			String squery = query.substring("EQUALS:".length()).toLowerCase();
			return (card -> card.cardText.equals(squery));
		} else {
			String lquery = query.toLowerCase();
			return (card) -> card.cardText.contains(lquery);
		}
	}

	public static Predicate<Card> createTypeFilter(String query) {
		if (query.startsWith("REGEX:")) {
			Pattern regex = Pattern.compile(query.toLowerCase().substring("REGEX:".length()));
			return (card -> regex.matcher(card.getType().toLowerCase()).find());
		} else if (query.startsWith("EQUALS:")) {
			String squery = query.substring("EQUALS:".length()).toLowerCase();
			return (card -> card.getType().equalsIgnoreCase(squery));
		} else {
			String lquery = query.toLowerCase();
			return (card) -> card.getType().toLowerCase().contains(lquery);
		}
	}

	public static Predicate<Card> createKeywordFilter(String query) {
		String[] splitStrings = query.split("\\+");
		Collection<String[]> andGroups = new ArrayList<>(splitStrings.length);
		for (String splitString : splitStrings) {
			andGroups.add(splitString.split(","));
		}

		return (card) -> {
			for (String[] group : andGroups) {
				boolean isValid = false;
				for (String keyword : group) {
					if (card.keywords.contains(keyword.toLowerCase())) {
						isValid = true;
						break;
					}
				}

				if (!isValid) {
					return false;
				}
			}

			return true;
		};
	}

	public static void setFilterList(List<Predicate<Card>> filters) {
		currentFilters = filters;
		createTableFilter();
	}

	public static void setViewingDeck(boolean v) {
		viewingDeck = v;
		createTableFilter();
	}

	public static void setViewingCollection(boolean v) {
		viewingCollection = v;
		createTableFilter();
	}

	public static void setViewingBanned(boolean v) {
		viewingBanned = v;
		createTableFilter();
	}

	public static void setViewingUnowned(boolean v) {
		viewingUnowned = v;
		createTableFilter();
	}

	public static void setViewingNonPlayable(boolean v) {
		viewingNonPlayable = v;
		createTableFilter();
	}

	private static void createTableFilter() {
		Gui.setCardFilter(new RowFilter<CardTableModel, Integer>() {
			@Override
			public boolean include(Entry<? extends CardTableModel, ? extends Integer> entry) {
				Card card = CardDatabase.getCard(entry.getIdentifier());

				//Test against views.
				if (!viewingDeck && card.copiesInDeck() > 0) {
					return false;
				} else if (!viewingUnowned && card.getOwned() == 0) {
					return false;
				} else if (!viewingCollection && (card.getOwned() > 0 && card.copiesInDeck() == 0)) {
					return false;
				} else if (!viewingBanned && card.banned) {
					return false;
				} else if (!viewingNonPlayable && !card.isPlayable) {
					return false;
				}

				if (currentFilters != null) {
					//Test provided filters.
					for (Predicate<Card> filter : currentFilters) {
						if (!filter.test(card)) {
							return false;
						}
					}
				}
				return true;
			}
		});
	}
}
