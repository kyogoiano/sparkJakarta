/*
 * Copyright 2015 - Per Wendel
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package spark.ssl;

import java.net.URI;

/**
 * SSL Stores
 */
public record SslStores(URI keyStoreFile, String keyStorePassword, String keyStoreType, String certAlias, URI trustStoreFile,
                        String trustStorePassword,  String trustStoreType, boolean needsClientCert) {

    /**
     * Creates a Stores instance.
     *
     * @param keystoreFile       the keyStoreFile
     * @param keystorePassword   the keyStorePassword
     * @param truststoreFile     the trustStoreFile
     * @param truststorePassword the trustStorePassword
     * @return the SslStores instance.
     */
    public static SslStores create(URI keystoreFile,
                                   String keystorePassword,
                                   URI truststoreFile,
                                   String truststorePassword) {

        return new SslStores(keystoreFile, keystorePassword, "JKS", null, truststoreFile, truststorePassword, "JKS", false);
    }

    public static SslStores create(URI keystoreFile,
                                   String keystorePassword,
                                   String certAlias,
                                   URI truststoreFile,
                                   String truststorePassword) {

        return new SslStores(keystoreFile, keystorePassword, "JKS", certAlias, truststoreFile, truststorePassword,"JKS", false);
    }

    public static SslStores create(URI keystoreFile,
                                   String keystorePassword,
                                   URI truststoreFile,
                                   String truststorePassword,
                                   boolean needsClientCert) {

        return new SslStores(keystoreFile, keystorePassword, "JKS",null, truststoreFile, truststorePassword, "JKS",needsClientCert);
    }

    public static SslStores create(URI keystoreFile,
                                   String keystorePassword,
                                   String keyStoreType,
                                   String certAlias,
                                   URI truststoreFile,
                                   String truststorePassword,
                                   String trustStoreType,
                                   boolean needsClientCert) {

        return new SslStores(keystoreFile, keystorePassword, keyStoreType, certAlias, truststoreFile, truststorePassword,trustStoreType, needsClientCert);
    }
}
