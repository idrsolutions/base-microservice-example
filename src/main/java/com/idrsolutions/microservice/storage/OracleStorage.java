package com.idrsolutions.microservice.storage;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.model.CreatePreauthenticatedRequestDetails;
import com.oracle.bmc.objectstorage.model.PreauthenticatedRequest;
import com.oracle.bmc.objectstorage.requests.CreatePreauthenticatedRequestRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.CreatePreauthenticatedRequestResponse;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

/**
 * An implementation of {@link IStorage} that uses Oracle Buckets to store files
 */
public class OracleStorage extends BaseStorage {
    private final ObjectStorageClient client;

    private final String namespace;
    private final String bucketName;

    /**
     * Authenticates using the
     * @param region The Oracle Region
     * @param configFilePath The path to the OCI config file containing the authentication profiles, use null for the default file at "~/.oci/config"
     * @param profile The profile inside the config file, use null for the default profile
     * @param namespace The namespace of the bucket
     * @param bucketName the name of the bucket that the converted files should be uploaded to
     * @throws IOException if the credentials file is inaccessible
     */
    public OracleStorage(Region region, @Nullable String configFilePath, @Nullable String profile, String namespace, String bucketName) throws IOException {
        final ConfigFileReader.ConfigFile configFile= configFilePath != null ? ConfigFileReader.parse(configFilePath, profile) : ConfigFileReader.parseDefault(profile);

        final AuthenticationDetailsProvider provider = new ConfigFileAuthenticationDetailsProvider(configFile);

        client = new ObjectStorageClient(provider);
        client.setRegion(region);

        this.namespace = namespace;
        this.bucketName = bucketName;
    }

    /**
     * Authenticates using the provided implementation of {@link com.oracle.bmc.auth.BasicAuthenticationDetailsProvider}
     * @param region The Oracle Region
     * @param auth The user credentials for Oracle Cloud
     * @param namespace The namespace of the bucket
     * @param bucketName the name of the bucket that the converted files should be uploaded to
     */
    public OracleStorage(Region region, BasicAuthenticationDetailsProvider auth, String namespace, String bucketName) {
        client = new ObjectStorageClient(auth);
        client.setRegion(region);

        this.namespace = namespace;
        this.bucketName = bucketName;
    }

    /**
     * @inheritDoc
     */
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
