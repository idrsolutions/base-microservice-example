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

/**
 * An implementation of {@link IStorage} that uses Azure Blob Storage to store files
 */
public class AzureStorage extends BaseStorage {
    final BlobServiceClient client;

    private final String accountName;
    private final String containerName;

    /**
     * Authenticates using Shared Key
     * @param auth The authentication for Azure
     * @param accountName The storage account name
     * @param containerName The name of the container within the storage account that the converted files should be uploaded to
     */
    public AzureStorage(StorageSharedKeyCredential auth, String accountName, String containerName) {
        this.client = new BlobServiceClientBuilder().credential(auth).endpoint("https://" + accountName + ".blob.core.windows.net").buildClient();
        this.accountName = accountName;
        this.containerName = containerName;
    }

    /**
     * Authenticates using SAS
     * @param auth The authentication for Azure
     * @param accountName The storage account name
     * @param containerName The name of the container within the storage account
     */
    public AzureStorage(AzureSasCredential auth, String accountName, String containerName) {
        this.client = new BlobServiceClientBuilder().credential(auth).endpoint("https://" + accountName + ".blob.core.windows.net").buildClient();
        this.accountName = accountName;
        this.containerName = containerName;
    }

    /**
     * Authenticates using a Token
     * @param auth The authentication for Azure
     * @param accountName The storage account name
     * @param containerName The name of the container within the storage account
     */
    public AzureStorage(TokenCredential auth, String accountName, String containerName) {
        this.client = new BlobServiceClientBuilder().credential(auth).endpoint("https://" + accountName + ".blob.core.windows.net").buildClient();
        this.accountName = accountName;
        this.containerName = containerName;
    }

    /**
     * @inheritDoc
     */
    @Override
    public String put(byte[] fileToUpload, String fileName, String uuid) {
        BlobContainerClient containerClient;
        try {
            containerClient = client.createBlobContainer(containerName);
        } catch (BlobStorageException ex) {
            // The container may already exist, so don't throw an error
            if (!ex.getErrorCode().equals(BlobErrorCode.CONTAINER_ALREADY_EXISTS)) {
                throw ex;
            }
            containerClient = client.getBlobContainerClient(containerName);
        }

        final String dest = uuid + "/" + fileName;

        BlockBlobClient blobClient = containerClient.getBlobClient(dest).getBlockBlobClient();

        try (InputStream fileStream = new ByteArrayInputStream(fileToUpload)){
            blobClient.upload(fileStream, fileToUpload.length);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        // Set the filename using the content disposition HTTP Header to avoid the downloaded file also containing the UUID
        blobClient.setHttpHeaders(new BlobHttpHeaders().setContentDisposition("attachment; filename=" + fileName));

        BlobServiceSasSignatureValues sas = new BlobServiceSasSignatureValues(OffsetDateTime.now().plusMinutes(30), new BlobSasPermission().setReadPermission(true));

        String test = blobClient.generateSas(sas);

        URL blobURL = new BlobUrlParts()
                .setScheme("https://")
                .setHost(accountName + ".blob.core.windows.net")
                .setContainerName(containerName)
                .setBlobName(dest)
                .parseSasQueryParameters(test)
                .toUrl();

        return blobURL.toString();
    }
}
