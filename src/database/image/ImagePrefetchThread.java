package database.image;

import application.LogTags;
import database.Card;
import org.tinylog.Logger;

import java.util.Collection;
import java.util.Deque;
import java.util.concurrent.LinkedBlockingDeque;

public class ImagePrefetchThread {
	private static final Deque<Card> guiPrefetchQueue = new LinkedBlockingDeque<>();
	private static final Object imagePrefetchActive = new Object();
	@SuppressWarnings({"FieldMayBeFinal", "FieldCanBeLocal"})
	//No it cannot, since disabling it will result in unreachable code errors.
	private static boolean ENABLE_PREFETCHING = true;
	private static final Thread prefetchThread = new Thread(() -> {
		while (ENABLE_PREFETCHING) {
			try {
				Logger.tag(LogTags.PREFETCH.tag).info("Prefetching cards.");
				while (!guiPrefetchQueue.isEmpty()) {
					Card currentCard = guiPrefetchQueue.pop();
					Logger.tag(LogTags.PREFETCH.tag).trace("Prefetching card {}.", currentCard.getName());
					if (currentCard.frontImageUrl != null) {
						ImageStore.getImageFromScryfall(currentCard.frontImageUrl);
					}
					if (currentCard.backImageUrl != null) {
						ImageStore.getImageFromScryfall(currentCard.backImageUrl);
					}
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
	}

	public static void setPrefetchList(Collection<Card> cards) {
		guiPrefetchQueue.clear();
		guiPrefetchQueue.addAll(cards);
		prefetchThread.interrupt();
	}
}
