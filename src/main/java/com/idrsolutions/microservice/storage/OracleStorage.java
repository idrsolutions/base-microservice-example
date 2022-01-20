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
import java.util.Properties;

/**
 * An implementation of {@link IStorage} that uses Oracle Buckets to store files
 */
public class OracleStorage extends BaseStorage {
    private final ObjectStorageClient client;

    private final String namespace;
    private final String bucketName;
    private final String basePath;

    /**
     * Authenticates using the
     * @param region The Oracle Region
     * @param configFilePath The path to the OCI config file containing the authentication profiles, use null for the default file at "~/.oci/config"
     * @param profile The profile inside the config file, use null for the default profile
     * @param namespace The namespace of the bucket
     * @param bucketName the name of the bucket that the converted files should be uploaded to
     * @throws IOException if the credentials file is inaccessible
     */
    public OracleStorage(final Region region, final String configFilePath, final @Nullable String profile, final String namespace, final String bucketName, final String basePath) throws IOException {
        final ConfigFileReader.ConfigFile configFile = ConfigFileReader.parse(configFilePath, profile);

        final AuthenticationDetailsProvider provider = new ConfigFileAuthenticationDetailsProvider(configFile);

        client = new ObjectStorageClient(provider);
        client.setRegion(region);

        this.namespace = namespace;
        this.bucketName = bucketName;
        this.basePath = basePath;
    }

    /**
     * Authenticates using the provided implementation of {@link com.oracle.bmc.auth.BasicAuthenticationDetailsProvider}
     * @param region The Oracle Region
     * @param auth The user credentials for Oracle Cloud
     * @param namespace The namespace of the bucket
     * @param bucketName the name of the bucket that the converted files should be uploaded to
     */
    public OracleStorage(final Region region, final BasicAuthenticationDetailsProvider auth, final String namespace, final String bucketName, final String basePath) {
        client = new ObjectStorageClient(auth);
        client.setRegion(region);

        this.namespace = namespace;
        this.bucketName = bucketName;
        this.basePath = basePath;
    }

    public OracleStorage(Properties properties) throws IOException {
        // storageprovider.oracle.region
        // storageprovider.oracle.ociconfigfilepath
        // storageprovider.oracle.profile (nullable)
        // storageprovider.oracle.namespace
        // storageprovider.oracle.bucketname
        // storageprovider.oracle.basepath

        this(Region.fromRegionId(properties.getProperty("storageprovider.oracle.region")),
                properties.getProperty("storageprovider.oracle.ociconfigfilepath"),
                !properties.getProperty("storageprovider.oracle.profile").trim().isEmpty() ? properties.getProperty("storageprovider.oracle.profile") : null,
                properties.getProperty("storageprovider.oracle.namespace"),
                properties.getProperty("storageprovider.oracle.bucketname"),
                properties.getProperty("storageprovider.oracle.basepath"));
    }

    /**
     * @inheritDoc
     */
    @Override
    public String put(final byte[] fileToUpload, final String fileName, final String uuid) {
        try (InputStream fileStream = new ByteArrayInputStream(fileToUpload)) {
            return put(fileStream, fileToUpload.length, fileName, uuid);
        } catch (IOException e) {
            LOG.severe(e.getMessage());
        }
        return null;
    }

    @Override
    public String put(InputStream fileToUpload, long fileSize, String fileName, String uuid) {
        final String dest = basePath + "/" + uuid + "/" + fileName;

        final PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucketName(bucketName)
                .namespaceName(namespace)
                .objectName(dest)
                .contentLength(fileSize)
                .putObjectBody(fileToUpload)
                .build();

        client.putObject(objectRequest);

        final CreatePreauthenticatedRequestDetails preauthenticatedDetails = CreatePreauthenticatedRequestDetails.builder()
                .name("Converted PDF " + dest + " Download")
                .objectName(dest)
                .accessType(CreatePreauthenticatedRequestDetails.AccessType.ObjectRead)
                .timeExpires(new Date(System.currentTimeMillis() + 60 * 30 * 1000))
                .bucketListingAction(PreauthenticatedRequest.BucketListingAction.Deny)
                .build();

        final CreatePreauthenticatedRequestRequest preauthenticatedRequest = CreatePreauthenticatedRequestRequest.builder()
                .bucketName(bucketName)
                .namespaceName(namespace)
                .createPreauthenticatedRequestDetails(preauthenticatedDetails)
                .build();

        final CreatePreauthenticatedRequestResponse response = client.createPreauthenticatedRequest(preauthenticatedRequest);

        return client.getEndpoint() + response.getPreauthenticatedRequest().getAccessUri();
    }
}
