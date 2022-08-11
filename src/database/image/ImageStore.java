package database.image;

import application.LogTags;
import ar.com.hjg.pngj.*;
import database.Card;
import gui.UIConstants;
import org.tinylog.Logger;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

public class ImageStore {
	private ImageStore() {
	}

	private static final long MIN_REQUEST_DELAY = 100;
	private static final int MAX_CACHED_IMAGES = 60;
	private static BufferedImage defaultCardBack;
	private static final Map<String, BufferedImage> remoteImageCache = new WeakHashMap<>(MAX_CACHED_IMAGES);
	private static final Set<String> cachedImageList = Collections.newSetFromMap(new LinkedHashMap<String, Boolean>() {
		@Override
		protected boolean removeEldestEntry(Map.Entry eldest) {
			return size() > MAX_CACHED_IMAGES;
		}
	});
	private static long lastRequestTime = 0;

	/**
	 * Returns an image icon from the jar file based on the provided path.
	 *
	 * @param path The resource path to load/get.
	 * @return An ImageIcon corresponding to the image from the path.
	 */
	public static ImageIcon getLocalIcon(String path) {
		URL resourceId = ImageStore.class.getClassLoader().getResource(path);
		if (resourceId != null) {
			//This string is interned. .equals can suck it.
			//noinspection StringEquality
			if (path == "images/defaultcardback.jpg") {
				try {
					defaultCardBack = ImageIO.read(resourceId);
					return new ScaleableImageIcon(defaultCardBack);
				} catch (IOException e) {
					//If this fires, then the path that was provided was incorrect or something is missing from the jar.
					throw new RuntimeException(e);
				}
			}

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
	public static ImageIcon getLocalIcon(String path, Dimension size) {
		ScaleableImageIcon icon = (ScaleableImageIcon) getLocalIcon(path);
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
		return icon;
	}

	static synchronized BufferedImage getImageFromScryfall(URL imageUrl) throws InterruptedException {
		String urlString = imageUrl.toString();

		//Check if we already have the image, and can reuse it first!
		BufferedImage image = remoteImageCache.get(urlString);
		if (image != null) {
			Logger.tag(LogTags.IMAGE_LOAD.tag).debug("Loaded image from cache '{}'.", urlString);
			return image;
		}

		//Avoid sending too many requests to Scryfall at once and getting blocked.
		long currentTime = System.currentTimeMillis();
		if (currentTime < lastRequestTime + MIN_REQUEST_DELAY) {
			long delay = lastRequestTime + MIN_REQUEST_DELAY - currentTime;
			Logger.tag(LogTags.IMAGE_LOAD.tag).debug("Delaying request by {}. Last request {}, current request {}.", delay, currentTime, lastRequestTime);
			Thread.sleep(delay);
		} else {
			Logger.tag(LogTags.IMAGE_LOAD.tag).debug("No delay required. Last request {}, current request {}.", currentTime, lastRequestTime);
		}

		try {
			image = ImageIO.read(imageUrl);
			Logger.tag(LogTags.IMAGE_LOAD.tag).info("Loaded image from Scryfall '{}'.", urlString);
		} catch (IOException e) {
			Logger.tag(LogTags.IMAGE_LOAD.tag).warn("Unable to load image '{}'", imageUrl);
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
	public static synchronized void writeDeckImage(Iterable<Card> cards, int deckSize, File filepath) {
		PngWriter currDeckImage = null;
		try {
			Logger.info("Writing new deck image with {} cards.", deckSize);

			final ImageInfo imageInfo = new ImageInfo(IMAGE_SIZE_X, IMAGE_SIZE_Y, 8, false);
			final ImageLineInt writerLine = new ImageLineInt(imageInfo);
			final DataBuffer[] activeBuffers = new DataBuffer[CARDS_X];

			Iterator<Card> cardIterator = cards.iterator();
			Card card = cardIterator.next();
			int currCardCopy = 0;
			boolean writeFlipped = false;

			int pageNum = 0;
			final String parentPath = filepath.getParent() + File.separator;
			final String baseFilename = filepath.getName().substring(0, filepath.getName().lastIndexOf('.'));
			final String ext = filepath.getName().substring(filepath.getName().lastIndexOf('.'));

			while (cardIterator.hasNext()) {
				//Each loop iteration writes a png.
				{
					File currFile = new File(parentPath + baseFilename + (writeFlipped ? "_back_" : "_") + pageNum++ + ext);
					Logger.tag(LogTags.DECK_IMAGE.tag).info("Writing to {}", filepath.getName());
					currDeckImage = new PngWriter(currFile, imageInfo);
					currDeckImage.setFilterType(FilterType.FILTER_NONE);
				}

				//Whenever we reach a row the current cards don't extend into, get the new active buffers.
				for (int y = 0; y < IMAGE_SIZE_Y; y++) {
					if (y % CARD_SIZE_Y == 0) {
						Logger.tag(LogTags.DECK_IMAGE.tag).debug("Getting image buffers.");
						for (int index = 0; index < CARDS_X; index++) {
							if (!(y == IMAGE_SIZE_Y - CARD_SIZE_Y && index + 1 == CARDS_X)) {
								if (card != null) {
									Logger.tag(LogTags.DECK_IMAGE.tag).debug("Getting buffer for card {}", card.getName());

									BufferedImage image = null;
									URL imageUrl = writeFlipped ? card.backImageUrl : card.frontImageUrl;
									if (imageUrl != null) {
										image = getImageFromScryfall(writeFlipped ? card.backImageUrl : card.frontImageUrl);
									}
									if (image == null) {
										image = defaultCardBack;
									}

									activeBuffers[index] = image.getRaster().getDataBuffer();
									currCardCopy++;
									if (currCardCopy >= card.copiesInDeck()) {
										if (cardIterator.hasNext()) {
											card = cardIterator.next();
											currCardCopy = 0;
										} else {
											card = null;
										}
									}
								} else {
									Logger.tag(LogTags.DECK_IMAGE.tag).debug("Getting null buffer.");
									activeBuffers[index] = null;
								}
							} else {
								Logger.tag(LogTags.DECK_IMAGE.tag).debug("Getting buffer for default card back.");
								activeBuffers[index] = defaultCardBack.getRaster().getDataBuffer();
							}
						}
					}

					//Write the current row in the image.
					for (int cardX = 0; cardX < CARDS_X; cardX++) {
						DataBuffer cardBuffer = activeBuffers[cardX];
						if (cardBuffer != null) {
							for (int x = 0; x < CARD_SIZE_X; x++) {
								int outIndex = 3 * (x + cardX * CARD_SIZE_X);
								int cardIndex = 3 * (x + ((y % CARD_SIZE_Y) * CARD_SIZE_X));
								writerLine.getScanline()[outIndex] = cardBuffer.getElem(cardIndex + 2);     //R
								writerLine.getScanline()[outIndex + 1] = cardBuffer.getElem(cardIndex + 1); //G
								writerLine.getScanline()[outIndex + 2] = cardBuffer.getElem(cardIndex);        //B
							}
						} else {
							for (int x = 0; x < CARD_SIZE_X * 3; x++) {
								writerLine.getScanline()[x + cardX * CARD_SIZE_X] = 0;
							}
						}
					}
					currDeckImage.writeRow(writerLine);
				}

				Logger.tag(LogTags.DECK_IMAGE.tag).info("Deck image written.");
				currDeckImage.end();
				if (!cardIterator.hasNext() && !writeFlipped) {
					Logger.tag(LogTags.DECK_IMAGE.tag).info("Flipping cards.");
					writeFlipped = true;
					cardIterator = cards.iterator();
					card = cardIterator.next();
					currCardCopy = 0;
				}
			}
			currDeckImage = null;
		} catch (PngjException e) {
			Logger.tag(LogTags.DECK_IMAGE.tag).error(e, "Unable to write image.");
		} catch (InterruptedException e) {
			Logger.tag(LogTags.DECK_IMAGE.tag).error("Image loading interrupted.");
		} finally {
			if (currDeckImage != null) {
				currDeckImage.close();
			}
		}
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