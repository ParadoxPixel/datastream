# DataStream

### What?
DataStream is a simple piece of code to access paged data and interface it as if it's a single "list". It only keeps track of queued entries which is at most page size + minimum entries.

### How does it work?
The class uses an ArrayDeque to queue entries and a SynchronisedQueue to hand of entries when reading thread is waiting for data. It makes use of the IExecutor interface to generalise asynchronous execution of the load function. I could have gone with an ExecutorService for this, but that wouldn't be able to use any custom scheduler when desired.

### Usage
First we have to implement IExecutor which is going to handle our async task scheduling.
```java
import nl.iobyte.datastream.interfaces.IExecutor;

public class MyExecutor implements IExecutor {
    
    public void async(Runnable r) {
        //TODO schedule task
    }
    
}
```
We also need a DataProvider which is going to get the data from the desired source for a given page with size and turn it into an ordered list of objects.

```java
import nl.iobyte.datastream.interfaces.DataProvider;

import java.util.List;

public class MyDataProvider implements DataProvider<MyClass> {
    
    public List<MyClass> page(int i, int size) {
        //page indexes start with 0 to max page - 1
        //size is how many items per page, if we don't want to use it just ignore it
        
        //TODO Retrieve and parse page
        
        return list; //Return list of parsed data here
    }
    
}
```
Next up we can finally initialize our DataStream for type MyClass.
```java
MyExecutor executor = ....;
MyDataProvider provider = ...;

DataStream<MyClass> stream = new DataStream(
        provider,
        executor,
        10, //Page size, must be bigger than minimum entries
        3 //Entries in queue at which to start loading the next page
);

//Take a single vallue
MyClass entry = stream.next(5, TimeUnit.SECONDS); //Specify after how long to timeout if no values loaded

Iterator<MyClass> iterator = stream.iterator(5, TimeUnit.SECONDS); //Specify after how long to timeout if no values loaded

for(MyClass entry : stream.iterate(5, TimeUnit.SECONDS))//Specify after how long to timeout if no values loaded
    //TODO do something with my entry
```