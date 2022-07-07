package database;

import gui.UIConstants;
import org.tinylog.Logger;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;

public class ImageStore {
	private ImageStore() {
	}

	private static final long MIN_REQUEST_DELAY = 100;
	private static final int MAX_CACHED_IMAGES = 60;

	private static final Map<String, BufferedImage> remoteImageCache = new WeakHashMap<>(MAX_CACHED_IMAGES);
	private static final Set<String> cachedImageList = Collections.newSetFromMap(new LinkedHashMap<String, Boolean>() {
		@Override
		protected boolean removeEldestEntry(Map.Entry eldest) {
			return size() > MAX_CACHED_IMAGES;
		}
	});
	private static long lastRequestTime = 0;

	private static final Deque<Card> deckPrefetchQueue = new LinkedBlockingDeque<>();
	private static final Deque<Card> guiPrefetchQueue = new LinkedBlockingDeque<>();
	private static final Object imagePrefetchActive = new Object();
	private static final Thread prefetchThread = new Thread(() -> {
		while (true) {
			try {
				Logger.info("Prefetching cards.");
				while (!deckPrefetchQueue.isEmpty()) {
					Card currentCard = deckPrefetchQueue.pop();
					Logger.info("Prefetching card {}.", currentCard.getName());
					if (currentCard.frontImageUrl != null) {
						getImageFromScryfall(currentCard.frontImageUrl);
					}
					if (currentCard.backImageUrl != null) {
						getImageFromScryfall(currentCard.backImageUrl);
					}
				}

				while (!guiPrefetchQueue.isEmpty()) {
					Card currentCard = guiPrefetchQueue.pop();
					Logger.info("Prefetching card {}.", currentCard.getName());
					if (currentCard.frontImageUrl != null) {
						getImageFromScryfall(currentCard.frontImageUrl);
					}
					if (currentCard.backImageUrl != null) {
						getImageFromScryfall(currentCard.backImageUrl);
					}
				}
				synchronized (imagePrefetchActive) {
					imagePrefetchActive.wait();
				}
			} catch (InterruptedException e) {
				Logger.info("Prefetch lists updated, restarting prefetch process.");
			}
		}
	});

	static {
		prefetchThread.setName("Image Prefetcher");
		prefetchThread.start();
	}

	public static void setDeckPrefetchList(Collection<Card> cards) {
		deckPrefetchQueue.clear();
		deckPrefetchQueue.addAll(cards);
		prefetchThread.interrupt();
		synchronized (imagePrefetchActive) {
			imagePrefetchActive.notifyAll();
		}
	}

	public static void setPrefetchList(Collection<Card> cards) {
		guiPrefetchQueue.clear();
		guiPrefetchQueue.addAll(cards);
		prefetchThread.interrupt();
		synchronized (imagePrefetchActive) {
			imagePrefetchActive.notifyAll();
		}
	}

	/**
	 * Returns an image icon from the jar file based on the provided path.
	 *
	 * @param path The resource path to load/get.
	 * @return An ImageIcon corresponding to the image from the path.
	 */
	public static ImageIcon getLocal(String path) {
		URL resourceId = ImageStore.class.getClassLoader().getResource(path);
		if (resourceId != null) {
			return new ScaleableImageIcon(resourceId);
		} else {
			//If this fires, then the path that was provided was incorrect or something is missing from the jar.
			throw new NullPointerException("No URL created from path: " + path);
		}
	}

	/**
	 * Returns an image icon from the jar file based on the provided path with the desired scaling.
	 *
	 * @param path The resource path to load/get.
	 * @param size The size to rescale the image to.
	 * @return An ImageIcon corresponding to the image from the path.
	 */
	public static ImageIcon getLocal(String path, Dimension size) {
		ScaleableImageIcon icon = (ScaleableImageIcon) getLocal(path);
		icon.setIconWidth(size.width);
		icon.setIconHeight(size.height);
		return icon;
	}

	public static synchronized ImageIcon getCardImage(URL imageUrl) throws InterruptedException {
		BufferedImage rawImage = getImageFromScryfall(imageUrl);
		if (rawImage == null) {
			return null;
		}

		ScaleableImageIcon icon = new ScaleableImageIcon(rawImage);
		icon.setIconWidth(UIConstants.CARD_IMAGE_SIZE.width);
		icon.setIconHeight(UIConstants.CARD_IMAGE_SIZE.height);
		Logger.info("Successfully loaded image '{}'.", imageUrl.toString());
		return icon;
	}

	private static synchronized BufferedImage getImageFromScryfall(URL imageUrl) throws InterruptedException {
		String urlString = imageUrl.toString();

		//Check if we already have the image, and can reuse it first!
		BufferedImage image = remoteImageCache.get(urlString);
		if (image != null) {
			Logger.debug("Loaded image from cache '{}'.", urlString);
			return image;
		}

		//Avoid sending too many requests to Scryfall at once and getting blocked.
		long currentTime = System.currentTimeMillis();
		if (currentTime < lastRequestTime + MIN_REQUEST_DELAY) {
			long delay = lastRequestTime + MIN_REQUEST_DELAY - currentTime;
			Logger.debug("Delaying request by {}. Last request {}, current request {}.", delay, currentTime, lastRequestTime);
			Thread.sleep(delay);
		} else {
			Logger.debug("No delay required. Last request {}, current request {}.", currentTime, lastRequestTime);
		}

		try {
			image = ImageIO.read(imageUrl);
			Logger.debug("Loaded image from Scryfall '{}'.", urlString);
		} catch (IOException e) {
			Logger.warn("Unable to load image '{}'", imageUrl);
			return null;
		}

		lastRequestTime = System.currentTimeMillis();
		remoteImageCache.put(urlString, image);
		cachedImageList.add(urlString);
		return image;
	}

	private static final int CARD_SIZE_X = 480;
	private static final int CARD_SIZE_Y = 680;
	private static final int CARDS_X = 10;
	private static final int CARDS_Y = 7;
	private static final int IMAGE_SIZE_X = CARD_SIZE_X * CARDS_X;
	private static final int IMAGE_SIZE_Y = CARD_SIZE_Y * CARDS_Y;
	//Somehow leaving this constructor inside of writeDeckImage would freese the entire thread for bizarre reasons (IE:
	//if the prefetching thread was enabled it would freeze). So instead, statically allocate it and reuse the same one.
	private static final BufferedImage deckImage = new BufferedImage(IMAGE_SIZE_X, IMAGE_SIZE_Y, BufferedImage.TYPE_INT_RGB);
	private static final Graphics2D canvas = deckImage.createGraphics();

	public static synchronized void writeDeckImage(Iterable<Card> cards, int deckSize, File filepath) {
		try {
			Logger.info("Writing new deck image with {} cards.", deckSize);

			int pageNum = 0;

			//Write front faces.
			Logger.info("Writing front face images.");
			int slot = 0;
			File currentFile = filepath;
			AffineTransform pos = new AffineTransform(1f, 0f, 0f, 1f, 0, 0);
			for (Card card : cards) {
				Image cardImage = getImageFromScryfall(card.frontImageUrl != null ? card.frontImageUrl : card.backImageUrl);
				int currCardCount = card.copiesInDeck();
				for (int i = 0; i < currCardCount; i++) {
					pos.setToTranslation((slot % CARDS_X) * CARD_SIZE_X, (slot / CARDS_X) * CARD_SIZE_Y);
					canvas.drawImage(cardImage, pos, null);
					if (slot < CARDS_X * CARDS_Y) {
						slot++;
					} else {
						slot = 0;
						ImageIO.write(deckImage, "png", currentFile);
						canvas.clearRect(0, 0, IMAGE_SIZE_X, IMAGE_SIZE_Y);
						currentFile = iterateFilepath(filepath, ++pageNum);
					}
				}
				Logger.info("Wrote card {} front.", card.getName());
			}
			ImageIO.write(deckImage, "png", currentFile);
			canvas.clearRect(0, 0, IMAGE_SIZE_X, IMAGE_SIZE_Y);

			//Write backfaces.
			Logger.info("Writing back face images.");
			slot = 0;
			currentFile = iterateFilepath(filepath, ++pageNum);
			Image genericBackImage = UIConstants.DEFAULT_CARD_IMAGE.getImage();
			for (Card card : cards) {
				Image cardImage = card.backImageUrl == null ? genericBackImage : getImageFromScryfall(card.backImageUrl);
				int currCardCount = card.copiesInDeck();
				for (int i = 0; i < currCardCount; i++) {
					pos.setToTranslation((slot % CARDS_X) * CARD_SIZE_X, (slot / CARDS_X) * CARD_SIZE_Y);
					canvas.drawImage(cardImage, pos, null);
					if (slot < CARDS_X * CARDS_Y) {
						slot++;
					} else {
						slot = 0;
						ImageIO.write(deckImage, "png", currentFile);
						canvas.clearRect(0, 0, IMAGE_SIZE_X, IMAGE_SIZE_Y);
						currentFile = iterateFilepath(filepath, ++pageNum);
					}
				}
				Logger.info("Wrote card {} back.", card.getName());
			}
			ImageIO.write(deckImage, "png", currentFile);
			canvas.clearRect(0, 0, IMAGE_SIZE_X, IMAGE_SIZE_Y);
		} catch (IOException e) {
			Logger.error(e, "Unable to write image.");
		} catch (InterruptedException e) {
			Logger.error("Image loading interrupted.");
		}
	}

	private static File iterateFilepath(File sourceFile, int suffix) {
		String parentPath = sourceFile.getParent() + File.separator;
		String oldName = sourceFile.getName();
		String newName = oldName.substring(0, oldName.lastIndexOf('.'));
		String ext = oldName.substring(oldName.lastIndexOf('.'));
		String newPath = parentPath + newName + suffix + ext;
		Logger.debug("Iterating file {} to {}", sourceFile.getAbsolutePath(), newPath);
		return new File(newPath);
	}

	/**
	 * Hacky class that overrides the height/width of images when drawn to allow them to look
	 * sharp on high DPI displays, and to allow for easy scaling of size.
	 */
	private static class ScaleableImageIcon extends ImageIcon {
		int width;
		int height;

		private static final Map<RenderingHints.Key, Object> renderingHintsMap;

		static {
			Map<RenderingHints.Key, Object> rh = new HashMap<>(2);
			rh.put(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
			rh.put(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			renderingHintsMap = Collections.unmodifiableMap(rh);
		}

		public ScaleableImageIcon(URL url) {
			super(url);
			width = super.getIconWidth();
			height = super.getIconHeight();
		}

		public ScaleableImageIcon(Image image) {
			super(image);
			width = super.getIconWidth();
			height = super.getIconHeight();
		}

		@Override
		public int getIconWidth() {
			return width;
		}

		public void setIconHeight(int height) {
			this.height = height;
		}

		public void setIconWidth(int width) {
			this.width = width;
		}

		@Override
		public int getIconHeight() {
			return height;
		}

		@Override
		public synchronized void paintIcon(Component c, Graphics g, int x, int y) {
			Graphics2D g2 = (Graphics2D) g;
			g2.setRenderingHints(renderingHintsMap);
			g2.drawImage(getImage(), x, y, width, height, null);
		}
	}
}