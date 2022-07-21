package database;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 Object representing a magic the gathering card. Should only be mutated by CardDatabase, but may be referenced elsewhere
 for convenience or general sanity. */
public class Card {
	//Float comparison delta. Used to check if decimal points should get filtered.
	private static final double DELTA = 0.001;
	/** Field names for JTables that are displaying cards. */
	public static final String[] DATA_FIELD_NAMES = {"Name", "Type", "Mana", "Power", "Tough", "Owned", "In Deck"};

	/** Used by get() for JTables. */
	public static final int NAME = 0;

	/** Used by get() for JTables. */
	public static final int TYPE = 1;

	/** Used by get() for JTables. */
	public static final int MANA = 2;

	/** Used by get() for JTables. */
	public static final int POWER_OR_LOYALTY = 3;

	/** Used by get() for JTables. */
	public static final int TOUGHNESS = 4;

	/** Used by get() for JTables. */
	public static final int IN_TRUNK = 5;

	/** Used by get() for JTables. */
	public static final int IN_DECK = 6;

	private final CardField<String> name;
	private final CardField<String> type;
	private final CardField<Double> mana;
	private final CardField<Float> powerOrLoyalty;
	private final CardField<Float> toughness;
	private CardField<Integer> owned = new CardField<>(0);
	private CardField<Integer> inDeck = new CardField<>(0);
	/** Whether or not the current active ban list hits this card. */
	public volatile boolean banned = false;
	/** The text inside the text box for the card. */
	public final String cardText;
	private final boolean isBasicLand;
	/** Index of this card in the card list, for fast lookups. */
	public final int id;
	/** List of colors making up this card's color identity. */
	public final List<CardColor> colorIdentity;
	/** Keyworks associated with the card IE: Trample, Likelink, Scry, ect.*/
	public final List<String> keywords;
	/** URL for the card's front image in Scryfall. */
	public final URL frontImageUrl;
	/** URL for the card's back image on Scryfall. */
	public final URL backImageUrl;
	/** Whether or not the card is 'playable' inside a deck. Set to false on some promo cards, tokens, and so on. */
	public final boolean isPlayable;

	/**
	 Constructs a card object from JSON.
	 @param cardJson The json to construct the card from.
	 @param id       The ID number for the card.
	 @throws JSONException         If the json object returns a parsing error.
	 @throws MalformedURLException If the url for the card images are invalid.
	 */
	public Card(JSONObject cardJson, int id) throws JSONException, MalformedURLException {
		this.id = id;

		JSONArray jsonColorIdentity = cardJson.getJSONArray("color_identity");
		List<CardColor> mutColorIdentity = new ArrayList<>(jsonColorIdentity.length());
		if (!jsonColorIdentity.isEmpty()) {
			for (Object colorJson : jsonColorIdentity) {
				mutColorIdentity.add(CardColor.getColorFromScryfall(colorJson.toString()));
			}
		} else {
			mutColorIdentity.add(CardColor.COLORLESS);
		}
		colorIdentity = Collections.unmodifiableList(mutColorIdentity);

		JSONArray jsonKeywords = cardJson.getJSONArray("keywords");
		List<String> mutKeywords = new ArrayList<>(jsonKeywords.length());
		for (Object jsonKeyword : jsonKeywords) {
			mutKeywords.add(jsonKeyword.toString().toLowerCase());
		}
		keywords = Collections.unmodifiableList(mutKeywords);

		cardText = cardJson.optString("oracle_text", "").toLowerCase().trim();

		name = new CardField<>(cardJson.getString("name"));
		String typeline = cardJson.getString("type_line"); //TODO: Sort out encoding issues.
		isPlayable = !typeline.contains("Card") && !typeline.contains("Token");
		type = new CardField<>(typeline);
		isBasicLand = typeline.contains("Basic Land");

		double cmc = cardJson.getDouble("cmc");
		if (cmc - (int) cmc < DELTA) {
			mana = new CardField<>(String.valueOf((int) cmc), cmc); //Remove decimals from CMCs that doesn't need it.
		} else {
			mana = new CardField<>(String.valueOf(cmc), cmc);
		}

		if (!cardJson.has("card_faces")) { //Single faced cards.
			String powerStr = cardJson.optString("power", cardJson.optString("loyalty", "N/A"));
			float p = Float.MIN_VALUE;
			try {
				p = Float.parseFloat(powerStr);
			} catch (NumberFormatException e) {
				if (!powerStr.equals("N/A")) {
					p = 0;
				}
			}
			powerOrLoyalty = new CardField<>(powerStr, p);

			String toughStr = cardJson.optString("toughness", "N/A");
			float t = Float.MIN_VALUE;
			try {
				t = Float.parseFloat(toughStr);
			} catch (NumberFormatException e) {
				if (!toughStr.equals("N/A")) {
					t = 0;
				}
			}
			toughness = new CardField<>(toughStr, t);

			frontImageUrl = cardJson.has("image_uris") ? new URL(cardJson.getJSONObject("image_uris").getString("border_crop")) : null;
			backImageUrl = null;
		} else { //Double faced cards.
			JSONArray faces = cardJson.getJSONArray("card_faces");
			JSONObject front = faces.getJSONObject(0);
			JSONObject back = faces.getJSONObject(1);

			String powerStr = front.optString("power", front.optString("loyalty", "N/A")) + " // " + back.optString("power", back.optString("loyalty", "N/A"));
			float pf = Float.MIN_VALUE;
			float pb = Float.MIN_VALUE;
			try {
				pf = Float.parseFloat(front.optString("power", front.optString("loyalty")));
			} catch (NumberFormatException e) {
				if (!powerStr.equals("N/A // N/A")) {
					pf = 0;
				}
			}
			try {
				pb = Float.parseFloat(back.optString("power", back.optString("loyalty")));
			} catch (NumberFormatException e) {
				if (!powerStr.equals("N/A // N/A")) {
					pb = 0;
				}
			}
			powerOrLoyalty = new CardField<>(powerStr, Float.max(pf, pb));

			String toughStr = front.optString("toughness", "N/A") + " // " + back.optString("toughness", "N/A");

			float tf = Float.MIN_VALUE;
			float tb = Float.MIN_VALUE;
			try {
				tf = Float.parseFloat(front.optString("toughness"));
			} catch (NumberFormatException e) {
				if (!toughStr.equals("N/A // N/A")) {
					tf = 0;
				}
			}
			try {
				tb = Float.parseFloat(back.optString("toughness"));
			} catch (NumberFormatException e) {
				if (!toughStr.equals("N/A // N/A")) {
					tb = 0;
				}
			}
			toughness = new CardField<>(toughStr, Float.max(tf, tb));

			frontImageUrl = front.has("image_uris") ? new URL(front.getJSONObject("image_uris").getString("border_crop")) : null;
			backImageUrl = back.has("image_uris") ? new URL(back.getJSONObject("image_uris").getString("border_crop")) : null;
		}
	}

	/** @return The name of the card. */
	public String getName() {
		return name.toString();
	}

	/** @return The typeline of the card. */
	public String getType() {
		return type.toString();
	}

	/**
	 Returns the desired CardField. Valid fields are the public static ints provided by Card. For use by JTables.
	 * @param value  The type of field that's desired.
	 * @return       The desired CardField.
	 */
	public CardField<?> get(int value) {
		switch (value) {
			case NAME:
				return name;
			case TYPE:
				return type;
			case MANA:
				return mana;
			case POWER_OR_LOYALTY:
				return powerOrLoyalty;
			case TOUGHNESS:
				return toughness;
			case IN_TRUNK:
				return owned;
			case IN_DECK:
				return inDeck;
			default:
				throw new IllegalArgumentException("Attempted to look up a nonexistent card field.");
		}
	}

	/** Sets the number of copies of this card that are owned. */
	public synchronized void setOwned(int count) {
		owned = new CardField<>(count);
		setInDeck(copiesInDeck());
	}

	public synchronized void addOwned(int count) {
		owned = new CardField<>((int) owned.data + count);
		setInDeck(copiesInDeck());
	}

	public synchronized int getOwned() {
		return (int) owned.data;
	}

	public synchronized int copiesInDeck() {
		return (int) inDeck.data;
	}

	public synchronized void addToDeck(int count) {
		setInDeck((int) inDeck.data + count);
	}

	public synchronized void setInDeck(int count) {
		int oldValue = copiesInDeck();
		inDeck = new CardField<>(Integer.max(Integer.min(count, deckMax()), 0));
		CardDatabase.updateCardInDeck(this, oldValue);
	}

	public synchronized int deckMax() {
		if (banned) {
			return 0;
		}

		return Integer.min(isBasicLand ? Integer.MAX_VALUE : 4, (int) owned.data);
	}
}
