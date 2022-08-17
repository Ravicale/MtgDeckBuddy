package database.card;

public enum CardColor {
	WHITE("white", "W"),
	BLUE("blue", "U"),
	BLACK("black", "B"),
	RED("red", "R"),
	GREEN("green", "G"),
	COLORLESS("colorless", null);

	public final String niceName;
	public final String scryfallSymbol;

	CardColor(String niceName, String scryfallSymbol) {
		this.niceName = niceName;
		this.scryfallSymbol = scryfallSymbol;
	}

	public String toString() {
		return niceName;
	}

	public static CardColor getColorFromScryfall(String color) {
		for (CardColor value : values()) {
			if (color.equals(value.scryfallSymbol)) {
				return value;
			}
		}
		return COLORLESS;
	}
}
