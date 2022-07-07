package database;

public class CardField<T> implements Comparable<T> {
	public final Comparable<T> data; //Value to use for table sorting.
	public final String string; //String to display in the table.

	public CardField (Comparable<T> data) {
		this.data = data;
		string = data.toString();
	}

	public CardField (String string, Comparable<T> data) {
		this.data = data;
		this.string = string;
	}

	@Override
	public String toString() {
		return string;
	}

	@Override
	public int compareTo(Object o) {
		if (o instanceof CardField) {
			CardField<?> comparedField = (CardField) o;
			return data.compareTo((T) comparedField.data);
		}

		throw new ClassCastException("Invalid comparison!");
	}
}