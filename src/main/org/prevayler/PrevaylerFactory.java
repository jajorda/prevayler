package org.prevayler;

import java.io.IOException;

import org.prevayler.implementation.CentralPublisher;
import org.prevayler.implementation.LiberalTransactionCensor;
import org.prevayler.implementation.PrevaylerImpl;
import org.prevayler.implementation.SnapshotManager;
import org.prevayler.implementation.StrictTransactionCensor;
import org.prevayler.implementation.TransactionCensor;
import org.prevayler.implementation.TransactionPublisher;
import org.prevayler.implementation.clock.MachineClock;
import org.prevayler.implementation.log.PersistentLogger;
import org.prevayler.implementation.log.TransactionLogger;
import org.prevayler.implementation.log.TransientLogger;
import org.prevayler.implementation.replica.PublishingServer;
import org.prevayler.implementation.replica.RemotePublisher;

/** Provides easy access to all Prevayler configurations and implementations available in this distribution.
 * Static methods are also provided as short-cuts for the most common configurations. 
 * <br>By default, the Prevayler instances created by this class will write their Transactions to .transactionLog files before executing them. The FileDescriptor.sync() method is called to make sure the Java file write-buffers have been written to the operating system. Many operating systems, including most recent versions of Linux and Windows, allow the hard-drive's write-cache to be disabled. This guarantees no executed Transaction will be lost in the event of a power shortage, for example.
 * <br>Also by default, the Prevayler instances created by this class will filter out all Transactions that would throw a RuntimeException or Error if executed on the Prevalent System. This requires enough RAM to hold another copy of the prevalent system. 
 * @see Prevayler 
 */
public class PrevaylerFactory {

	private Object _prevalentSystem;
	private Clock _clock;

	private boolean _transactionFiltering = true;

	private boolean _transientMode;
	private String _prevalenceBase;
	private SnapshotManager _snapshotManager;

	private int _serverPort = -1;
	private String _remoteServerIpAddress;
	private int _remoteServerPort;
	public static final int DEFAULT_REPLICATION_PORT = 8756;


	/** Creates a Prevayler that will use a directory called "PrevalenceBase" under the current directory to read and write its .snapshot and .transactionLog files.
 	 * @param newPrevalentSystem The newly started, "empty" prevalent system that will be used as a starting point for every system startup, until the first snapshot is taken.
	 */
	public static Prevayler createPrevayler(Object newPrevalentSystem) throws IOException, ClassNotFoundException {
		return createPrevayler(newPrevalentSystem, "PrevalenceBase");
	}


	/** Creates a Prevayler that will use the given prevalenceBase directory to read and write its .snapshot and .transactionLog files.
	 * @param newPrevalentSystem The newly started, "empty" prevalent system that will be used as a starting point for every system startup, until the first snapshot is taken.
	 * @param prevalenceBase The directory where the .snapshot files and .transactionLog files will be read and written.
	 */
	public static Prevayler createPrevayler(Object prevalentSystem, String prevalenceBase) throws IOException, ClassNotFoundException {
		PrevaylerFactory factory = new PrevaylerFactory();
		factory.configurePrevalentSystem(prevalentSystem);
		factory.configurePrevalenceBase(prevalenceBase);
		return factory.create();
	}


	/** Creates a Prevayler that will execute Transactions WITHOUT writing them to disk. It will use a directory called "PrevalenceBase" to read and write its .snapshot files. This is useful for stand-alone applications which have a "Save" button, for example, or for running automated tests MUCH faster than with a regular Prevayler.
	 * @param newPrevalentSystem The newly started, "empty" prevalent system that will be used as a starting point for every system startup, until the first snapshot is taken.
	 */
	public static Prevayler createTransientPrevayler(Object newPrevalentSystem) {
		return createTransientPrevayler(newPrevalentSystem, "PrevalenceBase");
	}


	/** Creates a Prevayler that will execute Transactions WITHOUT writing them to disk. It will use the given prevalenceBase directory to read and write its .snapshot files. This is useful for stand-alone applications which have a "Save" button, for example, or for running automated tests MUCH faster than with a persistent Prevayler.
	 * @param newPrevalentSystem The newly started, "empty" prevalent system that will be used as a starting point for every system startup, until the first snapshot is taken.
	 * @param prevalenceBase The directory where the .snapshot files will be read and written.
	 */
	public static Prevayler createTransientPrevayler(Object prevalentSystem, String snapshotDirectory) {
		PrevaylerFactory factory = new PrevaylerFactory();
		factory.configurePrevalentSystem(prevalentSystem);
		factory.configurePrevalenceBase(snapshotDirectory);
		factory.configureTransientMode(true);
		try {
			return factory.create();
		} catch (Exception e) {
			e.printStackTrace(); //Transient Prevayler creation should not fail.
			return null;
		}
	}


	private Clock clock() {
		return _clock != null ? _clock : new MachineClock();
	}


	/** Determines whether the Prevayler created by this factory should be transient (transientMode = true) or persistent (transientMode = false). A transient Prevayler will execute its Transactions WITHOUT writing them to disk. This is useful for stand-alone applications which have a "Save" button, for example, or for running automated tests MUCH faster than with a persistent Prevayler.
	 */
	public void configureTransientMode(boolean transientMode) {
		_transientMode = transientMode;		
	}


	/** Configures the Clock that will be used by the created Prevayler. The Clock interface can be implemented by the application if it requires Prevayler to use a special time source other than the machine clock (default).
	 */
	public void configureClock(Clock clock) {
		_clock = clock;
	}


	/** Configures the directory where the created Prevayler will read and write its .transactionLog and .snapshot files. The default is a directory called "PrevalenceBase" under the current directory.
	 */
	public void configurePrevalenceBase(String prevalenceBase) {
		_prevalenceBase = prevalenceBase;
	}


	/** Configures the prevalent system that will be used by the Prevayler created by this factory.
	 * @param prevalentSystem Will be ignored if a SnapshotManager is configured too. A SnapshotManager already has a prevalent system.
	 * @see configureSnapshotManager()
	 */
	public void configurePrevalentSystem(Object prevalentSystem) {
		_prevalentSystem = prevalentSystem;
	}


	/** Reserved for implementation in Prevayler release 2.1.
	 */
	public void configureReplicationClient(String remoteServerIpAddress, int remoteServerPort) {
		_remoteServerIpAddress = remoteServerIpAddress;
		_remoteServerPort = remoteServerPort;
	}


	/** Reserved for implementation in Prevayler release 2.1.
	 */
	public void configureReplicationServer(int port) {
		_serverPort = port;
	}


	/** Configures the SnapshotManager to be used by the Prevayler created by this factory. The default is a SnapshotManager which uses plain Java serialization to create its .snapshot files. Another option is the XmlSnapshotManager.
	 * @see org.prevayler.implementation.XmlSnapshotManager
	 */
	public void configureSnapshotManager(SnapshotManager snapshotManager) {
		_snapshotManager = snapshotManager;
	}


	/** Determines whether the Prevayler created by this factory should filter out all Transactions that would throw a RuntimeException or Error if executed on the Prevalent System (default is true). This requires enough RAM to hold another copy of the prevalent system.
	 */
	public void configureTransactionFiltering(boolean transactionFiltering) {
		_transactionFiltering = transactionFiltering;
	}


	/** Returns a Prevayler created according to what was defined by calls to the configuration methods above.
	 * @throws IOException If there is trouble creating the Prevalence Base directory or reading a .transactionLog or .snapshot file.
	 * @throws ClassNotFoundException If a class of a serialized Object is not found when reading a .transactionLog or .snapshot file.
	 */
	public Prevayler create() throws IOException, ClassNotFoundException {
		SnapshotManager snapshotManager = snapshotManager();
		TransactionPublisher publisher = publisher(snapshotManager);
		if (_serverPort != -1) new PublishingServer(publisher, _serverPort);
		return new PrevaylerImpl(snapshotManager, publisher);
	}


	private String prevalenceBase() {
		return _prevalenceBase != null ? _prevalenceBase : "PrevalenceBase";
	}


	private Object prevalentSystem() {
		if (_prevalentSystem == null) throw new IllegalStateException("The prevalent system must be configured.");
		return _prevalentSystem;
	}


	private TransactionPublisher publisher(SnapshotManager snapshotManager) throws IOException, ClassNotFoundException {
		if (_remoteServerIpAddress != null) return new RemotePublisher(_remoteServerIpAddress, _remoteServerPort);
		return new CentralPublisher(clock(), censor(snapshotManager), logger()); 
	}


	private TransactionCensor censor(SnapshotManager snapshotManager) {
		return _transactionFiltering
			? (TransactionCensor) new StrictTransactionCensor(snapshotManager)
			: new LiberalTransactionCensor(); 
	}


	private TransactionLogger logger() throws IOException, ClassNotFoundException {
		return _transientMode
			? (TransactionLogger)new TransientLogger()
			: new PersistentLogger(prevalenceBase());		
	}


	private SnapshotManager snapshotManager() throws ClassNotFoundException, IOException {
		return _snapshotManager != null
			? _snapshotManager
			: new SnapshotManager(prevalentSystem(), prevalenceBase());
	}

}
