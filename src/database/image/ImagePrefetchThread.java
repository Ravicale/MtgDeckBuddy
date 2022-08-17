package database.image;

import application.LogTags;
import database.Card;
import org.tinylog.Logger;

import java.util.Collection;
import java.util.Deque;
import java.util.concurrent.LinkedBlockingDeque;

public class ImagePrefetchThread {
	private static final Deque<Card> guiPrefetchQueue = new LinkedBlockingDeque<>();
	private static final Deque<Card> deckPrefetchQueue = new LinkedBlockingDeque<>();
	private static final Object imagePrefetchActive = new Object();
	@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
	//No it cannot, since disabling it will result in unreachable code errors.
	private static boolean ENABLE_PREFETCHING = true;
	private static final Thread prefetchThread = new Thread(() -> {
		while (ENABLE_PREFETCHING) {
			try {
				Logger.tag(LogTags.PREFETCH.tag).info("Prefetching cards.");
				//No point trying to prefetch more images than can be cached.
				int prefetchPool = ImageStore.MAX_CACHED_IMAGES;
				Card currentCard = guiPrefetchQueue.poll();
				while (currentCard != null && prefetchPool > 0) {
					Logger.tag(LogTags.PREFETCH.tag).trace("Prefetching card for gui {}.", currentCard.getName());
					if (currentCard.frontImageUrl != null) {
						ImageStore.getImageFromScryfall(currentCard.frontImageUrl);
						prefetchPool--;
					}
					if (currentCard.backImageUrl != null) {
						ImageStore.getImageFromScryfall(currentCard.backImageUrl);
						prefetchPool--;
					}
					currentCard = guiPrefetchQueue.poll();
				}

				currentCard = deckPrefetchQueue.poll();
				while (currentCard != null && prefetchPool > 0) {
					Logger.tag(LogTags.PREFETCH.tag).trace("Prefetching card for deck {}.", currentCard.getName());
					if (currentCard.frontImageUrl != null) {
						ImageStore.getImageFromScryfall(currentCard.frontImageUrl);
						prefetchPool--;
					}
					if (currentCard.backImageUrl != null) {
						ImageStore.getImageFromScryfall(currentCard.backImageUrl);
						prefetchPool--;
					}
					currentCard = deckPrefetchQueue.poll();
				}

				Logger.tag(LogTags.PREFETCH.tag).info("Prefetching complete.");
				synchronized (imagePrefetchActive) {
					//Just wait indefinitely.
					//Leaves via interruptedException since we want to interrupt any in-progress image loads when the queues change.
					imagePrefetchActive.wait();
				}
			} catch (InterruptedException e) {
				Logger.tag(LogTags.PREFETCH.tag).debug("Prefetch lists updated, restarting prefetch process.");
			}
		}
	});

	static {
		prefetchThread.setName("Image Prefetcher");
		prefetchThread.start();
		prefetchThread.setPriority(1);
	}

	public static void setPrefetchList(Collection<Card> cards) {
		guiPrefetchQueue.clear();
		guiPrefetchQueue.addAll(cards);
		prefetchThread.interrupt();
	}

	public static void setDeckPrefetchList(Collection<Card> cards) {
		deckPrefetchQueue.clear();
		deckPrefetchQueue.addAll(cards);
		prefetchThread.interrupt();
	}
}
