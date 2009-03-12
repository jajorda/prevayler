//Prevayler(TM) - The Free-Software Prevalence Layer.
//Copyright (C) 2001-2003 Klaus Wuestefeld
//This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

package org.prevayler.implementation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import junit.framework.AssertionFailedError;

import org.prevayler.Prevayler;
import org.prevayler.PrevaylerFactory;
import org.prevayler.foundation.FileIOTest;
import org.prevayler.foundation.FileManager;
import org.prevayler.foundation.TurnAbortedException;

public class PersistenceTest extends FileIOTest {

	private Prevayler _prevayler;
	private String _prevalenceBase;
    
    public void tearDown() throws Exception {
        if (_prevayler != null) {
            _prevayler.close();
        }
        super.tearDown();
    }

	public void testPersistence() throws Exception {

		newPrevalenceBase();

		crashRecover(); //There is nothing to recover at first. A new system will be created.
		crashRecover();
		append("a","a");
		append("b","ab");
		verify("ab");

		crashRecover();
		verify("ab");

		append("c","abc");
		append("d","abcd");
		snapshot("0000000000000000004.snapshot");
		snapshot("0000000000000000004.snapshot");
		verify("abcd");

		crashRecover();
		snapshot("0000000000000000004.snapshot");
		append("e","abcde");
		snapshot("0000000000000000005.snapshot");
		append("f","abcdef");
		append("g","abcdefg");
		verify("abcdefg");

		crashRecover();
		append("h","abcdefgh");
		verify("abcdefgh");

		snapshot("0000000000000000008.snapshot");
		_prevayler.close();
		File lastSnapshot =   new File(_prevalenceBase, "0000000000000000008.snapshot");
		File lastTransactionLog =   new File(_prevalenceBase, "0000000000000000008.journal");
		newPrevalenceBase();
		FileManager.produceDirectory(_prevalenceBase);
		lastSnapshot.renameTo(new File(_prevalenceBase, "0000000000000000008.snapshot"));  //Moving the file.
		lastTransactionLog.renameTo(new File(_prevalenceBase, "0000000000000000008.journal"));

		crashRecover();
		append("i","abcdefghi");
		append("j","abcdefghij");
		crashRecover();
		append("k","abcdefghijk");
		append("l","abcdefghijkl");
		crashRecover();
		append("m","abcdefghijklm");
		append("n","abcdefghijklmn");
		crashRecover();
		verify("abcdefghijklmn");
	}

    public void testSnapshotVersion0() throws Exception {
        newPrevalenceBase();

        crashRecover(); //There is nothing to recover at first. A new system will be created.
        append("a", "a");
        append("b", "ab");
        append("c", "abc");
        verify("abc");
        File snapshot = snapshot("0000000000000000003.snapshot");

        _prevayler.close();
        assertTrue(snapshot.renameTo(new File(prevalenceBase(), "0000000000000000000.snapshot")));
        assertTrue(new File(_prevalenceBase, "0000000000000000001.journal").delete());

        crashRecover();
        verify("abc");
        append("d", "abcd");
        snapshot("0000000000000000001.snapshot");
    }

    public void testNondeterminsticError() throws Exception {
        newPrevalenceBase();
        crashRecover(); //There is nothing to recover at first. A new system will be created.

        append("a", "a");
        append("b", "ab");
        verify("ab");

        NondeterministicErrorTransaction.armBomb(1);
        try {
            _prevayler.execute(new NondeterministicErrorTransaction("c"));
            fail();
        } catch (AssertionFailedError failed) {
            throw failed;
        } catch (Error expected) {
            assertEquals(Error.class, expected.getClass());
            assertEquals("BOOM!", expected.getMessage());
        }

        try {
            _prevayler.execute(new Appendix("x"));
            fail();
        } catch (AssertionFailedError failed) {
            throw failed;
        } catch (Error expected) {
            assertEquals(Error.class, expected.getClass());
            assertEquals("Prevayler is no longer processing transactions due to an Error thrown from an earlier transaction.", expected.getMessage());
        }

        try {
            _prevayler.execute(new NullQuery());
            fail();
        } catch (AssertionFailedError failed) {
            throw failed;
        } catch (Error expected) {
            assertEquals(Error.class, expected.getClass());
            assertEquals("Prevayler is no longer processing queries due to an Error thrown from an earlier transaction.", expected.getMessage());
        }

        try {
            _prevayler.prevalentSystem();
            fail();
        } catch (AssertionFailedError failed) {
            throw failed;
        } catch (Error expected) {
            assertEquals(Error.class, expected.getClass());
            assertEquals("Prevayler is no longer allowing access to the prevalent system due to an Error thrown from an earlier transaction.", expected.getMessage());
        }

        try {
            _prevayler.takeSnapshot();
            fail();
        } catch (AssertionFailedError failed) {
            throw failed;
        } catch (Error expected) {
            assertEquals(Error.class, expected.getClass());
            assertEquals("Prevayler is no longer allowing snapshots due to an Error thrown from an earlier transaction.", expected.getMessage());
        }

        crashRecover();
        
        // Note that both the transaction that threw the Error and the
        // subsequent transaction *were* journaled, so they get applied
        // successfully on recovery.
        verify("abcx");
    }

    public void testJournalPanic() throws Exception {
        newPrevalenceBase();

        crashRecover();
        append("a", "a");
        append("b", "ab");

        sneakilyCloseUnderlyingJournalStream();

        try {
            _prevayler.execute(new Appendix("x"));
            fail();
        } catch (TurnAbortedException aborted) {
            assertEquals("All transaction processing is now aborted. An IOException was thrown while writing to a .journal file.", aborted.getMessage());
            assertNotNull(aborted.getCause());
        }

        try {
            _prevayler.execute(new Appendix("y"));
            fail();
        } catch (TurnAbortedException aborted) {
            assertNull(aborted.getMessage());
            assertNull(aborted.getCause());
        }

        crashRecover();
        verify("ab");
        append("c", "abc");
    }

    private void sneakilyCloseUnderlyingJournalStream() throws Exception {
        FileOutputStream journalStream = (FileOutputStream) Sneaky.get(_prevayler, "_publisher._journal._outputJournal._fileOutputStream");
        journalStream.close();
    }

    public void testFileCleanupHelper() throws Exception {
        newPrevalenceBase();

        PrevaylerDirectory directory = new PrevaylerDirectory(prevalenceBase());
        directory.produceDirectory();

        checkNecessaryFiles(directory, new String[] {});

        crashRecover(); //There is nothing to recover at first. A new system will be created.
        append("a","a");
        append("b","ab");
        
        checkNecessaryFiles(directory, new String[] {"0000000000000000001.journal"});

        crashRecover();
        append("c","abc");
        append("d","abcd");
        
        checkNecessaryFiles(directory, new String[] {"0000000000000000001.journal", "0000000000000000003.journal"});

        snapshot("0000000000000000004.snapshot");
        
        checkNecessaryFiles(directory, new String[] {"0000000000000000004.snapshot", "0000000000000000003.journal"});
        
        crashRecover();

        append("e","abcde");
        append("f","abcdef");
        
        checkNecessaryFiles(directory, new String[] {"0000000000000000004.snapshot", "0000000000000000005.journal"});

        crashRecover();

        append("g","abcdefg");

        checkNecessaryFiles(directory, new String[] {"0000000000000000004.snapshot", "0000000000000000005.journal", "0000000000000000007.journal"});

        snapshot("0000000000000000007.snapshot");
        
        _prevayler.close();

        checkNecessaryFiles(directory, new String[] {"0000000000000000007.snapshot", "0000000000000000007.journal"});
    }
    
    private void checkNecessaryFiles(PrevaylerDirectory directory, String[] filenames) throws IOException {
        assertEquals(setOfFiles(filenames), directory.necessaryFiles());
    }
    
    private Set setOfFiles(String[] filenames) {
        Set set = new HashSet();
        for (int i = 0; i < filenames.length; i++) {
            set.add(new File(prevalenceBase(), filenames[i]));
        }
        return set;
    }

    private void crashRecover() throws Exception {
        out("CrashRecovery.");

        if (_prevayler != null) _prevayler.close();

        PrevaylerFactory factory = new PrevaylerFactory();
        factory.configurePrevalentSystem(new AppendingSystem());
        factory.configurePrevalenceDirectory(prevalenceBase());
        factory.configureTransactionFiltering(false);
        _prevayler = factory.create();
    }

	private File snapshot(String expectedSnapshotFilename) throws IOException {
		out("Snapshot.");
		File snapshotFile = _prevayler.takeSnapshot();
		assertEquals(new File(prevalenceBase(), expectedSnapshotFilename), snapshotFile);
		return snapshotFile;
	}


	private void append(String appendix, String expectedResult) throws Exception {
		out("Appending " + appendix);
		_prevayler.execute(new Appendix(appendix));
		verify(expectedResult);
	}


	private void verify(String expectedResult) {
		out("Expecting result: " + expectedResult);
		assertEquals(expectedResult, system().value());
	}


	private AppendingSystem system() {
		return (AppendingSystem)_prevayler.prevalentSystem();
	}


	private String prevalenceBase() {
		return _prevalenceBase;
	}


	private void newPrevalenceBase() throws Exception {
		_prevalenceBase = _testDirectory + File.separator + System.currentTimeMillis();
	}


	private static void out(Object obj) {
		if (false) System.out.println(obj);   //Change this line to see what the test is doing.
	}

}
