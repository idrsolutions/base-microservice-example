package org.jpedal.external;

import java.rmi.Remote;
import java.rmi.RemoteException;


/**
 * The RemoteTracker interface allows monitoring of page decode progress from another application using java RMI.
 */
@SuppressWarnings("ALL")
public interface RemoteTracker extends Remote {

    void finishedPageDecoding(final String uuid, final int rawPage) throws RemoteException;

}
