package com.idrsolutions.microservice.storage;

import com.azure.core.credential.AzureSasCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.BlobUrlParts;
import com.azure.storage.blob.models.BlobErrorCode;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.blob.specialized.BlockBlobClient;
import com.azure.storage.common.StorageSharedKeyCredential;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.OffsetDateTime;
import java.util.Properties;

/**
 * An implementation of {@link IStorage} that uses Azure Blob Storage to store files
 */
public class AzureStorage extends BaseStorage {
    final BlobServiceClient client;

    private final String accountName;
    private final String containerName;
    private final String basePath;

    /**
     * Authenticates using Shared Key
     * @param auth The authentication for Azure
     * @param accountName The storage account name
     * @param containerName The name of the container within the storage account that the converted files should be uploaded to
     */
    public AzureStorage(final StorageSharedKeyCredential auth, final String accountName, final String containerName, String basePath) {
        this.client = new BlobServiceClientBuilder().credential(auth).endpoint("https://" + accountName + ".blob.core.windows.net").buildClient();
        this.accountName = accountName;
        this.containerName = containerName;
        this.basePath = basePath;
    }

    /**
     * Authenticates using SAS
     * @param auth The authentication for Azure
     * @param accountName The storage account name
     * @param containerName The name of the container within the storage account
     */
    public AzureStorage(final AzureSasCredential auth, final String accountName, final String containerName, String basePath) {
        this.client = new BlobServiceClientBuilder().credential(auth).endpoint("https://" + accountName + ".blob.core.windows.net").buildClient();
        this.accountName = accountName;
        this.containerName = containerName;
        this.basePath = basePath;
    }

    /**
     * Authenticates using a Token
     * @param auth The authentication for Azure
     * @param accountName The storage account name
     * @param containerName The name of the container within the storage account
     */
    public AzureStorage(final TokenCredential auth, final String accountName, final String containerName, String basePath) {
        this.client = new BlobServiceClientBuilder().credential(auth).endpoint("https://" + accountName + ".blob.core.windows.net").buildClient();
        this.accountName = accountName;
        this.containerName = containerName;
        this.basePath = basePath;
    }

    public AzureStorage(Properties properties) {
        // storageprovider.azure.accountname
        // storageprovider.azure.accountkey
        // storageprovider.azure.containername
        // storageprovider.azure.basepath

        this(new StorageSharedKeyCredential(properties.getProperty("storageprovider.azure.accountname"),
                        properties.getProperty("storageprovider.azure.accountkey")),
                properties.getProperty("storageprovider.azure.accountname"),
                properties.getProperty("storageprovider.azure.containername"),
                properties.getProperty("storageprovider.azure.basepath", ""));
    }

    /**
     * @inheritDoc
     */
    @Override
    public String put(final byte[] fileToUpload, final String fileName, final String uuid) {
        try (InputStream fileStream = new ByteArrayInputStream(fileToUpload)){
            return put(fileStream, fileToUpload.length, fileName, uuid);
        } catch (IOException e) {
            LOG.severe(e.getMessage());
            return null;
        }
    }

    @Override
    public String put(InputStream fileToUpload, long fileSize, String fileName, String uuid) {
        BlobContainerClient containerClient;
        try {
            containerClient = client.createBlobContainer(containerName);
        } catch (BlobStorageException e) {
            // The container may already exist, so don't throw an error
            if (!e.getErrorCode().equals(BlobErrorCode.CONTAINER_ALREADY_EXISTS)) {
                LOG.severe(e.getMessage());
                return null;
            }
            containerClient = client.getBlobContainerClient(containerName);
        }


        String basePath = !this.basePath.isEmpty() ? this.basePath + "/" : "";
        final String dest = basePath + uuid + "/" + fileName;

        final BlockBlobClient blobClient = containerClient.getBlobClient(dest).getBlockBlobClient();

        blobClient.upload(fileToUpload, fileSize);

        // Set the filename using the content disposition HTTP Header to avoid the downloaded file also containing the UUID
        blobClient.setHttpHeaders(new BlobHttpHeaders().setContentDisposition("attachment; filename=" + fileName));

        final BlobServiceSasSignatureValues sas = new BlobServiceSasSignatureValues(OffsetDateTime.now().plusMinutes(30), new BlobSasPermission().setReadPermission(true));

        final String test = blobClient.generateSas(sas);

        final URL blobURL = new BlobUrlParts()
                .setScheme("https://")
                .setHost(accountName + ".blob.core.windows.net")
                .setContainerName(containerName)
                .setBlobName(dest)
                .parseSasQueryParameters(test)
                .toUrl();

        return blobURL.toString();
    }
}
