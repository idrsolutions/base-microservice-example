package com.idrsolutions.microservice.storage;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.model.CreatePreauthenticatedRequestDetails;
import com.oracle.bmc.objectstorage.model.PreauthenticatedRequest;
import com.oracle.bmc.objectstorage.requests.CreatePreauthenticatedRequestRequest;
import com.oracle.bmc.objectstorage.requests.GetPreauthenticatedRequestRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.CreatePreauthenticatedRequestResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

public class OracleStorage extends BaseStorage {
    ObjectStorageClient client;

    String bucketName = "";
    String namespace = "";

    // This assuming there is a default OCI config file
    // "~/.oci/config", and a profile in that config with the name "DEFAULT".
    public OracleStorage(Region region) throws IOException {
        final ConfigFileReader.ConfigFile configFile = ConfigFileReader.parseDefault();

        final AuthenticationDetailsProvider provider = new ConfigFileAuthenticationDetailsProvider(configFile);

        client = new ObjectStorageClient(provider);
        client.setRegion(region);
    }

    public OracleStorage(Region region, String profile) throws IOException {
        final ConfigFileReader.ConfigFile configFile = ConfigFileReader.parseDefault(profile);

        final AuthenticationDetailsProvider provider = new ConfigFileAuthenticationDetailsProvider(configFile);

        client = new ObjectStorageClient(provider);
        client.setRegion(region);
    }

    public OracleStorage(Region region, BasicAuthenticationDetailsProvider auth) {
        client = new ObjectStorageClient(auth);
        client.setRegion(region);
    }

    @Override
    public String put(byte[] fileToUpload, String fileName, String uuid) {
        try (InputStream fileStream = new ByteArrayInputStream(fileToUpload)) {

            final String dest = uuid + "/" + fileName;

            PutObjectRequest objectRequest = PutObjectRequest.builder()
                    .bucketName(bucketName)
                    .namespaceName(namespace)
                    .objectName(dest)
                    .contentLength((long) fileToUpload.length)
                    .putObjectBody(fileStream)
                    .build();

            client.putObject(objectRequest);

            CreatePreauthenticatedRequestDetails preauthenticatedDetails = CreatePreauthenticatedRequestDetails.builder()
                    .name("Converted PDF " + dest + " Download")
                    .objectName(dest)
                    .accessType(CreatePreauthenticatedRequestDetails.AccessType.ObjectRead)
                    .timeExpires(new Date(System.currentTimeMillis() + 60 * 30 * 1000))
                    .bucketListingAction(PreauthenticatedRequest.BucketListingAction.Deny)
                    .build();

            CreatePreauthenticatedRequestRequest preauthenticatedRequest = CreatePreauthenticatedRequestRequest.builder()
                    .bucketName(bucketName)
                    .namespaceName(namespace)
                    .createPreauthenticatedRequestDetails(preauthenticatedDetails)
                    .build();

            CreatePreauthenticatedRequestResponse response = client.createPreauthenticatedRequest(preauthenticatedRequest);

            return client.getEndpoint() + response.getPreauthenticatedRequest().getAccessUri();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
