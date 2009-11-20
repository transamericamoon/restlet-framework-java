/**
 * Copyright 2005-2009 Noelios Technologies.
 * 
 * The contents of this file are subject to the terms of one of the following
 * open source licenses: LGPL 3.0 or LGPL 2.1 or CDDL 1.0 or EPL 1.0 (the
 * "Licenses"). You can select the license that you prefer but you may not use
 * this file except in compliance with one of these Licenses.
 * 
 * You can obtain a copy of the LGPL 3.0 license at
 * http://www.opensource.org/licenses/lgpl-3.0.html
 * 
 * You can obtain a copy of the LGPL 2.1 license at
 * http://www.opensource.org/licenses/lgpl-2.1.php
 * 
 * You can obtain a copy of the CDDL 1.0 license at
 * http://www.opensource.org/licenses/cddl1.php
 * 
 * You can obtain a copy of the EPL 1.0 license at
 * http://www.opensource.org/licenses/eclipse-1.0.php
 * 
 * See the Licenses for the specific language governing permissions and
 * limitations under the Licenses.
 * 
 * Alternatively, you can obtain a royalty free commercial license with less
 * limitations, transferable or non-transferable, directly at
 * http://www.noelios.com/products/restlet-engine
 * 
 * Restlet is a registered trademark of Noelios Technologies.
 */

package org.restlet.ext.crypto.internal;

import java.io.IOException;
import java.util.logging.Level;

import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.ChallengeRequest;
import org.restlet.data.ChallengeResponse;
import org.restlet.data.ChallengeScheme;
import org.restlet.data.Digest;
import org.restlet.data.Parameter;
import org.restlet.data.Reference;
import org.restlet.engine.http.HeaderBuilder;
import org.restlet.engine.http.HeaderReader;
import org.restlet.engine.http.HttpUtils;
import org.restlet.engine.security.AuthenticatorHelper;
import org.restlet.engine.security.AuthenticatorUtils;
import org.restlet.engine.util.Base64;
import org.restlet.ext.crypto.DigestUtils;
import org.restlet.security.Guard;
import org.restlet.util.Series;

/**
 * Implements the HTTP DIGEST authentication.
 * 
 * @author Jerome Louvel
 */
@SuppressWarnings("deprecation")
public class HttpDigestHelper extends AuthenticatorHelper {

    /**
     * Return the hashed secret.
     * 
     * @param identifier
     *            The user identifier to hash.
     * @param guard
     *            The associated guard to callback.
     * 
     * @return A hash of the user name, realm, and password, specified as A1 in
     *         section 3.2.2.2 of RFC2617, or null if the identifier has no
     *         corresponding secret.
     */
    @Deprecated
    private static String getHashedSecret(String identifier, Guard guard) {
        char[] secret = guard.getSecretResolver().resolve(identifier);

        if (secret != null) {
            return DigestUtils.toHttpDigest(identifier, secret, guard
                    .getRealm());
        }

        // The given identifier is not known
        return null;
    }

    /**
     * Checks whether the specified nonce is valid with respect to the specified
     * secretKey, and further confirms that the nonce was generated less than
     * lifespanMillis milliseconds ago
     * 
     * @param nonce
     *            The nonce value.
     * @param secretKey
     *            The same secret value that was inserted into the nonce when it
     *            was generated
     * @param lifespan
     *            The nonce lifespan in milliseconds.
     * @return True if the nonce was generated less than lifespan milliseconds
     *         ago, false otherwise.
     * @throws Exception
     *             If the nonce does not match the specified secretKey, or if it
     *             can't be parsed
     */
    public static boolean isNonceValid(String nonce, String secretKey,
            long lifespan) throws Exception {
        try {
            String decodedNonce = new String(Base64.decode(nonce));
            long nonceTimeMS = Long.parseLong(decodedNonce.substring(0,
                    decodedNonce.indexOf(':')));

            if (decodedNonce.equals(nonceTimeMS + ":"
                    + DigestUtils.toMd5(nonceTimeMS + ":" + secretKey))) {
                // Valid with regard to the secretKey, now check lifespan
                return lifespan > (System.currentTimeMillis() - nonceTimeMS);
            }
        } catch (Exception e) {
            throw new Exception("Error detected parsing nonce: " + e);
        }

        throw new Exception("The nonce does not match secretKey");
    }

    /**
     * Constructor.
     */
    public HttpDigestHelper() {
        super(ChallengeScheme.HTTP_DIGEST, true, true);
    }

    @Deprecated
    @Override
    public int authenticate(ChallengeResponse cr, Request request, Guard guard) {
        Series<Parameter> parameters = cr.getParameters();
        String username = cr.getIdentifier();
        String response = new String(cr.getSecret());
        String nonce = parameters.getFirstValue("nonce");
        String uri = parameters.getFirstValue("uri");
        String qop = parameters.getFirstValue("qop");
        String nc = parameters.getFirstValue("nc");
        String cnonce = parameters.getFirstValue("cnonce");

        try {
            if (!isNonceValid(nonce, guard.getServerKey(), guard
                    .getNonceLifespan())) {
                // Nonce expired, send challenge request with
                // stale=true
                return Guard.AUTHENTICATION_STALE;
            }
        } catch (Exception ce) {
            // Invalid nonce, probably doesn't match serverKey
            return Guard.AUTHENTICATION_INVALID;
        }

        if (!AuthenticatorUtils.anyNull(username, nonce, response, uri)) {
            Reference resourceRef = request.getResourceRef();
            String requestUri = resourceRef.getPath();

            if ((resourceRef.getQuery() != null) && (uri.indexOf('?') > -1)) {
                // IE neglects to include the query string, so
                // the workaround is to leave it off
                // unless both the calculated URI and the
                // specified URI contain a query string
                requestUri += "?" + resourceRef.getQuery();
            }

            if (uri.equals(requestUri)) {
                String a1 = getHashedSecret(username, guard);

                if (a1 != null) {
                    String a2 = DigestUtils.toMd5(request.getMethod() + ":"
                            + requestUri);
                    StringBuffer expectedResponse = new StringBuffer(a1)
                            .append(':').append(nonce);

                    if (!AuthenticatorUtils.anyNull(qop, cnonce, nc)) {
                        expectedResponse.append(':').append(nc).append(':')
                                .append(cnonce).append(':').append(qop);
                    }

                    expectedResponse.append(':').append(a2);

                    if (response.equals(DigestUtils.toMd5(expectedResponse
                            .toString()))) {
                        return Guard.AUTHENTICATION_VALID;
                    }
                }
            }

            return Guard.AUTHENTICATION_INVALID;
        }

        return Guard.AUTHENTICATION_MISSING;
    }

    @Deprecated
    @Override
    public void challenge(Response response, boolean stale, Guard guard) {
        super.challenge(response, stale, guard);

        // This is temporary, pending Guard re-factoring. We still assume
        // there is only one challenge scheme, that of the Guard.
        ChallengeRequest mainChallengeRequest = null;

        for (ChallengeRequest challengeRequest : response
                .getChallengeRequests()) {
            if (challengeRequest.getScheme().equals(guard.getScheme())) {
                mainChallengeRequest = challengeRequest;
                break;
            }
        }

        if (mainChallengeRequest != null) {
            mainChallengeRequest.setDomainUris(guard.getDomainUris());
            mainChallengeRequest.setStale(stale);
            mainChallengeRequest.setServerNonce(CryptoUtils.makeNonce(guard
                    .getServerKey()));
        }
    }

    @Override
    public void formatRawRequest(HeaderBuilder hb, ChallengeRequest challenge,
            Response response, Series<Parameter> httpHeaders)
            throws IOException {

        if (challenge.getRealm() != null) {
            hb.appendQuotedParameter("realm", challenge.getRealm());
        }

        if (!challenge.getDomainRefs().isEmpty()) {
            hb.append(", domain=\"");

            for (int i = 0; i < challenge.getDomainRefs().size(); i++) {
                if (i > 0) {
                    hb.append(' ');
                }

                hb.append(challenge.getDomainRefs().get(i).toString());
            }

            hb.append('"');
        }

        if (challenge.getServerNonce() != null) {
            hb.appendQuotedParameter("nonce", challenge.getServerNonce());
        }

        if (challenge.getOpaque() != null) {
            hb.appendQuotedParameter("opaque", challenge.getOpaque());
        }

        if (challenge.isStale()) {
            hb.appendParameter("stale", "true");
        }

        if (challenge.getDigestAlgorithm() != null) {
            hb.appendParameter("algorithm", challenge.getDigestAlgorithm());
        }

        if (!challenge.getQualityOptions().isEmpty()) {
            hb.append(", qop=\"");

            for (int i = 0; i < challenge.getQualityOptions().size(); i++) {
                if (i > 0) {
                    hb.append(',');
                }

                hb.appendToken(challenge.getQualityOptions().get(i).toString());
            }

            hb.append('"');
        }

        for (Parameter param : challenge.getParameters()) {
            if (HttpUtils.isToken(param.getValue())) {
                hb.appendParameter(param);
            } else {
                hb.appendQuotedParameter(param);
            }
        }
    }

    @Override
    public void formatRawResponse(HeaderBuilder hb,
            ChallengeResponse challenge, Request request,
            Series<Parameter> httpHeaders) throws IOException {

        if (challenge.getIdentifier() != null) {
            hb.appendQuotedParameter("username", challenge.getIdentifier());
        }

        if (challenge.getRealm() != null) {
            hb.appendQuotedParameter("realm", challenge.getRealm());
        }

        if (challenge.getServerNonce() != null) {
            hb.appendQuotedParameter("nonce", challenge.getServerNonce());
        }

        if (challenge.getDigestRef() != null) {
            hb
                    .appendQuotedParameter("uri", challenge.getDigestRef()
                            .toString());
        }

        if (challenge.getSecret() != null) {
            if (challenge.getSecret() != null) {
                String a1 = new String(challenge.getSecret());
                String a2 = DigestUtils.toMd5(request.getMethod() + ":"
                        + challenge.getDigestRef().toString());
                StringBuilder response = new StringBuilder().append(a1).append(
                        ':').append(challenge.getServerNonce());

                if (!AuthenticatorUtils.anyNull(challenge.getQuality(),
                        challenge.getClientNonce(), challenge
                                .getServerNounceCount())) {
                    response.append(':').append(
                            AuthenticatorUtils.formatNonceCount(challenge
                                    .getServerNounceCount())).append(':')
                            .append(challenge.getClientNonce()).append(':')
                            .append(challenge.getQuality());
                }

                response.append(':').append(a2);

                hb.appendQuotedParameter("response", DigestUtils.toMd5(response
                        .toString()));
            }
        }

        if ((challenge.getDigestAlgorithm() != null)
                && !Digest.ALGORITHM_MD5.equals(challenge.getDigestAlgorithm())) {
            hb.appendParameter("algorithm", challenge.getDigestAlgorithm());
        }

        if (challenge.getClientNonce() != null) {
            hb.appendQuotedParameter("cnonce", challenge.getClientNonce());
        }

        if (challenge.getOpaque() != null) {
            hb.appendQuotedParameter("opaque", challenge.getOpaque());
        }

        if (challenge.getQuality() != null) {
            hb.appendParameter("qop", challenge.getQuality());
        }

        if ((challenge.getQuality() != null)
                && (challenge.getServerNounceCount() > 0)) {
            hb.appendParameter("nc", challenge.getServerNounceCountAsHex());
        }

        for (Parameter param : challenge.getParameters()) {
            if (HttpUtils.isToken(param.getValue())) {
                hb.appendParameter(param);
            } else {
                hb.appendQuotedParameter(param);
            }
        }
    }

    @Override
    public char[] formatSecret(ChallengeResponse challengeResponse,
            String password) {
        if (!AuthenticatorUtils.anyNull(challengeResponse.getIdentifier(),
                password, challengeResponse.getRealm())) {
            return DigestUtils.toHttpDigest(challengeResponse.getIdentifier(),
                    password.toCharArray(), challengeResponse.getRealm())
                    .toCharArray();
        }

        return null;
    }

    @Override
    public void parseRequest(ChallengeRequest challenge, Response response,
            Series<Parameter> httpHeaders) {
        if (challenge.getRawValue() != null) {
            HeaderReader hr = new HeaderReader(challenge.getRawValue());

            try {
                Parameter param = hr.readParameter();

                while (param != null) {
                    try {
                        if ("realm".equals(param.getName())) {
                            challenge.setRealm(param.getValue());
                        } else if ("domain".equals(param.getName())) {
                            challenge.getDomainRefs().add(
                                    new Reference(param.getValue()));
                        } else if ("nonce".equals(param.getName())) {
                            challenge.setServerNonce(param.getValue());
                        } else if ("opaque".equals(param.getName())) {
                            challenge.setOpaque(param.getValue());
                        } else if ("stale".equals(param.getName())) {
                            challenge.setStale(Boolean
                                    .valueOf(param.getValue()));
                        } else if ("algorithm".equals(param.getName())) {
                            challenge.setDigestAlgorithm(param.getValue());
                        } else if ("qop".equals(param.getName())) {
                            // challenge.setDigestAlgorithm(param.getValue());
                        } else {
                            challenge.getParameters().add(param);
                        }

                        param = hr.readParameter();
                    } catch (Exception e) {
                        Context
                                .getCurrentLogger()
                                .log(
                                        Level.WARNING,
                                        "Unable to parse the challenge request header parameter",
                                        e);
                    }
                }
            } catch (Exception e) {
                Context
                        .getCurrentLogger()
                        .log(
                                Level.WARNING,
                                "Unable to parse the challenge request header parameter",
                                e);
            }
        }
    }

    @Override
    public void parseResponse(ChallengeResponse challenge, Request request,
            Series<Parameter> httpHeaders) {
        if (challenge.getCredentials() != null) {
            HeaderReader hr = new HeaderReader(challenge.getCredentials());

            try {
                Parameter param = hr.readParameter();

                while (param != null) {
                    try {
                        if ("username".equals(param.getName())) {
                            challenge.setIdentifier(param.getValue());
                        } else if ("realm".equals(param.getName())) {
                            challenge.setRealm(param.getValue());
                        } else if ("nonce".equals(param.getName())) {
                            challenge.setServerNonce(param.getValue());
                        } else if ("uri".equals(param.getName())) {
                            challenge.setDigestRef(new Reference(param
                                    .getValue()));
                        } else if ("response".equals(param.getName())) {
                            challenge.setSecret(param.getValue());
                        } else if ("algorithm".equals(param.getName())) {
                            challenge.setDigestAlgorithm(param.getValue());
                        } else if ("cnonce".equals(param.getName())) {
                            challenge.setClientNonce(param.getValue());
                        } else if ("opaque".equals(param.getName())) {
                            challenge.setOpaque(param.getValue());
                        } else if ("qop".equals(param.getName())) {
                            challenge.setQuality(param.getValue());
                        } else if ("nc".equals(param.getName())) {
                            challenge.setServerNounceCount(Integer
                                    .valueOf(param.getValue()));
                        } else {
                            challenge.getParameters().add(param);
                        }

                        param = hr.readParameter();
                    } catch (Exception e) {
                        Context
                                .getCurrentLogger()
                                .log(
                                        Level.WARNING,
                                        "Unable to parse the challenge request header parameter",
                                        e);
                    }
                }
            } catch (Exception e) {
                Context
                        .getCurrentLogger()
                        .log(
                                Level.WARNING,
                                "Unable to parse the challenge request header parameter",
                                e);
            }
        }
    }

}
