package nl.iobyte.datastream.interfaces;

import java.util.List;

public interface DataStreamProvider<T> {

    /**
     * Get page index with size
     * @param i Integer
     * @param size Integer
     * @return List<T>
     */
    List<T> page(int i, int size);

}
