package com.idrsolutions.microservice.utils;

import com.idrsolutions.microservice.db.DBHandler;
import org.jpedal.external.RemoteTracker;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class ProgressTracker extends UnicastRemoteObject implements RemoteTracker {

    public ProgressTracker(final int port) throws RemoteException {
        super(port);
    }

    @Override
    public void finishedPageDecoding(final String uuid, final int rawPage) {
        DBHandler.getInstance().setCustomValue(uuid, "pagesConverted", String.valueOf(rawPage));
    }
}
