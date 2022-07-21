package database;

import gui.Gui;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.tinylog.Logger;

import javax.swing.*;
import java.io.*;
import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ObjIntConsumer;

/**
 Class that handles data for all cards. Any mutations to cards should be done through this. */
public class CardDatabase {
	//Some of the tasks here can take a bit. So keep them off of the Swing event thread.
	private static final ExecutorService databaseWorkerThread = Executors.newSingleThreadExecutor();
	//Reference to a card loading event. Used to allow for the thread to be interrupted if something else gets clicked.
	private static Future<?> cardToLoad;
	//The previously loaded card image.
	private Card previousCard;
	//Database instance. Should be initialized via initCardDatabase before anything else happens.
	private static CardDatabase instance;
	//Latch used to allow for offthreads to safely see if the database is initialized.
	private static final CountDownLatch databaseLoaded = new CountDownLatch(1);

	//Map of cards sorted by name.
	private Map<String, Card> cardMap;
	//List of cards. The id number on a Card object correponds to this. Try using this over the map when possible.
	private List<Card> cardDataList;
	//Set of Card objects representing the user's current deck.
	private Set<Card> deckList;
	//The number of cards inside of the current deck.
	private final AtomicInteger deckSize = new AtomicInteger(0);

	private CardDatabase() {
	}

	/**
	 Reads the embedded carddb.json file and populates the card database.
	 */
	public static void initCardDatabase() {
		databaseWorkerThread.submit(() -> {
			Thread.currentThread().setName("Card Database Worker");
			Logger.trace("Initializing card database.");
			if (instance != null) {
				Logger.warn("initCardDatabase called more than once!");
				return;
			}

			CardDatabase database = new CardDatabase();
			InputStream databaseUri = CardDatabase.class.getClassLoader().getResourceAsStream("carddb.json");
			if (databaseUri == null) {
				Logger.error("Unable to load card database.");
				return;
			}

			Gui.setBusyLoading(true);
			Logger.info("Loading cards.");
			database.cardDataList = new ArrayList<>(26042);
			database.cardMap = new HashMap<>(26042);
			database.deckList = Collections.synchronizedSet(new HashSet<>(100));

			//Read in json objects for cards one at a time to avoid reading all of the JSON to memory at once.
			JSONTokener databaseJson = new JSONTokener(databaseUri);
			databaseJson.next('[');
			int cardNum = 0;
			while (databaseJson.skipTo('{') != 0) {
				try {
					JSONObject cardJson = new JSONObject(databaseJson);
					Card card = new Card(cardJson, cardNum);
					database.cardDataList.add(card);
					database.cardMap.put(card.getName(), card);
					cardNum++;
				} catch (JSONException e) {
					Logger.error(e, "Unable to create card #{}", cardNum);
					throw new RuntimeException("Unable to create card", e);
				} catch (MalformedURLException e) {
					Logger.error(e, "Scryfall reported an invalid URL in card #{}", cardNum);
				}
			}

			Logger.info("Loaded {} cards. Updating GUI.", database.cardDataList.size());
			instance = database;
			databaseLoaded.countDown();

			Gui.initializeCardListTable();
			Gui.setBusyLoading(false);
		});
	}

	/**
	 Called by card objects when the number of that card inside of the current deck changes.
	 Used to keep the deck list stuff up to date.
	 @param card The card being added to/removed from the deck.
	 @param prev The previous number of copies that card was in the deck prior to this update.
	 */
	public static void updateCardInDeck(Card card, int prev) {
		//No need to await here, Card objects don't exist if the database has not been initialized.
		int curr = card.copiesInDeck();
		if (curr > 0) {
			instance.deckList.add(card);
		} else {
			instance.deckList.remove(card);
		}

		Gui.setDeckSize(instance.deckSize.addAndGet(curr - prev));
	}

	/**
	 Loads an image for the desired card and displays it in the gui.
	 If a user-generated load event is currently ongoing and this is triggered, then the previous attempt is stopped.
	 Does nothing if the provided card is the same as the most recently loaded card.
	 Uses synchronization to ensure that cardToLoad and previousCard are both kept consistent- in case stuff outside
	 the Swing event thread might call this in the future.
	 @param card Card to load image(s) for.
	 */
	public static synchronized void loadAndDisplayImage(Card card) {
		try {
			databaseLoaded.await();

			if (card == instance.previousCard) {
				return;
			}
			instance.previousCard = card;

			//Stop the previous request, since it's no longer relevant.
			if (cardToLoad != null && !cardToLoad.isDone()) {
				cardToLoad.cancel(true);
			}

			Gui.setSelectedCard(card, null, null);
		} catch (InterruptedException e) {
			Logger.error("Database not yet initialized. Thread waiting on it was interrupted.");
		}

		cardToLoad = databaseWorkerThread.submit(() -> {
			try {
				Logger.info("Requesting images for '{}'.", card.getName());
				Gui.setBusyLoading(true);
				ImageIcon front = null;
				ImageIcon back = null;
				if (card.frontImageUrl != null) {
					front = ImageStore.getCardImage(card.frontImageUrl);
				}
				if (card.backImageUrl != null) {
					back = ImageStore.getCardImage(card.backImageUrl);
				}
				Gui.setSelectedCard(card, front, back);
			} catch (InterruptedException e) {
				Logger.info("Image loading interrupted.");
			} finally {
				Gui.setBusyLoading(false);
			}
		});
	}

	/**
	 Empties out the user's deck.
	 */
	public static void clearDeck() {
		databaseWorkerThread.submit(() -> {
			Gui.lockDeck();
			Logger.info("Clearing deck.");
			for (Card card : instance.cardDataList) {
				card.setInDeck(0);
			}
			ImageStore.setDeckPrefetchList(Collections.emptyList());
			Gui.unlockDeck();
		});
	}

	/**
	 Empties out the user's collection.
	 */
	public static void clearCollection() {
		databaseWorkerThread.submit(() -> {
			Gui.lockDeck();
			Logger.info("Clearing collection.");
			for (Card card : instance.cardDataList) {
				card.setOwned(0);
			}
			Gui.unlockDeck();
		});
	}

	/**
	 Reads the provided collection file and adds all cards inside of it into the user's collection.
	 @param file The file containing the card collection. A folder results in all .dec files inside of it being read.
	 */
	public static void readCollection(File file) {
		File[] files = file.isDirectory() ? file.listFiles((f) -> f.getName().endsWith(".dec")) : new File[]{file};
		if (files == null || files.length == 0) {
			Logger.warn("No .dec files found inside of '{}'.", file.getAbsolutePath());
			JOptionPane.showMessageDialog(Gui.getFrame(), "No .dec files found inside of selected folder.");
			return;
		}

		databaseWorkerThread.submit(() -> {
			Gui.lockDeck();
			for (File f : files) {
				instance.readDecFile(f, Card::addOwned);
			}
			Gui.unlockDeck();
		});
	}

	/**
	 Resets the ban list and adds the cards listed inside of the provided file to the ban list.
	 @param file The list of banned cards.
	 */
	public static synchronized void readBans(File file) {
		databaseWorkerThread.submit(() -> {
			Gui.lockDeck();
			for (Card card : instance.cardDataList) {
				card.banned = false;
			}

			instance.readDecFile(file, (card, count) -> {
				card.banned = true;
				card.setInDeck(0);
			});
			Gui.unlockDeck();
		});
	}

	/**
	 Read's the users provided .dec file and attempts to construct that deck off of the user's collection.
	 @param file The file containing the user's deck.
	 */
	public static synchronized void readDeck(File file) {
		databaseWorkerThread.submit(() -> {
			Gui.lockDeck();
			instance.readDecFile(file, Card::addToDeck);
			ImageStore.setDeckPrefetchList(instance.deckList);
			Gui.unlockDeck();
		});
	}

	/**
	 Saves the current deck to a .dec file.
	 @param file The file to write to.
	 */
	public static synchronized void saveDeck(File file) {
		databaseWorkerThread.submit(() -> {
			Gui.lockDeck();
			if (instance.deckSize.get() == 0) {
				Logger.info("User attempted to save an empty deck.");
				JOptionPane.showMessageDialog(Gui.getFrame(), "Your deck is currently empty.");
				Gui.unlockDeck();
				return;
			}

			try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
				writer.write("// Deck with " + instance.deckSize + " cards - " + Calendar.getInstance().getTime());
				writer.newLine();
				for (Card card : instance.deckList) {
					writer.write(card.copiesInDeck() + " " + card.getName());
					writer.newLine();
				}
				Gui.setBusyLoading(false);
			} catch (IOException e) {
				Logger.error("Unable to write deck to {}", file.getAbsolutePath());
			} finally {
				Gui.unlockDeck();
			}
		});
	}

	/**
	 Saves the user's current deck to a tiled image that is compatible with Tabletop Simulator.
	 @param file The file to write to. Will have incremental numbers to handle overflow + backfaces.
	 */
	public static void saveDeckImage(File file) {
		databaseWorkerThread.submit(() -> {
			Gui.lockDeck();
			if (instance.deckSize.get() == 0) {
				Logger.info("User attempted to save an empty deck.");
				JOptionPane.showMessageDialog(Gui.getFrame(), "Your deck is currently empty.");
			} else {
				ImageStore.writeDeckImage(instance.deckList, instance.deckSize.get(), file);
			}
			Gui.unlockDeck();
		});
	}

	/**
	 Adds 99 copies of every card to the user's collection.
	 */
	public static void fillCollection() {
		Logger.info("Filling collection.");
		databaseWorkerThread.submit(() -> {
			Gui.lockDeck();
			Logger.info("Filling collection.");
			for (Card card : instance.cardDataList) {
				card.setOwned(99);
			}
			Gui.unlockDeck();
		});
	}

	/**
	 Parses the provided .dec file and executes the desired action based on whatever cards are found + the
	 quantity of that card.
	 @param file   The file being read.
	 @param action Action to perform for every card.
	 */
	private void readDecFile(File file, ObjIntConsumer<Card> action) {
		Logger.info("Reading collection file at '{}'.", file.getAbsolutePath());
		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			long lineNum = 0;
			String line;

			while ((line = reader.readLine()) != null) {
				try {
					lineNum++;
					if (!line.isEmpty() && !line.startsWith("//")) {
						int count = Integer.parseInt(line.substring(0, line.indexOf(' ')));
						String name = line.substring(line.indexOf(' ')).trim();
						Card card = instance.cardMap.get(name);
						if (card != null) {
							action.accept(card, count);
						} else {
							Logger.error("Unable to find card named {} in database!", name);
						}
					}
				} catch (NumberFormatException e) {
					Logger.error(e, "Unable to read number of cards in line {} or {}", lineNum, file.getName());
				}
			}
		} catch (IOException e) {
			Logger.error(e, "Error while reading file {}.", file.getName());
		}
	}

	/**
	 Returns a Card corresponding to the desired id number.
	 @param index The id number of the card.
	 @return The related Card object.
	 */
	public static Card getCard(int index) {
		try {
			databaseLoaded.await();
		} catch (InterruptedException e) {
			Logger.error("Database not yet initialized. Thread waiting on it was interrupted.");
		}

		return instance.cardDataList.get(index);
	}

	/**
	 @return The total number of cards.
	 */
	public static int getCardCount() {
		if (databaseLoaded.getCount() > 0) {
			return 0;
		}

		return instance.cardDataList.size();
	}
}
