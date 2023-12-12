/*-
 * ========================LICENSE_START=================================
 * user-signup
 * %%
 * Copyright (C) 2014 - 2023 SoftInstigate
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */

package org.restheart.signup;

import java.util.Map;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.restheart.exchange.MongoRequest;
import org.restheart.exchange.MongoResponse;
import org.restheart.plugins.Inject;
import org.restheart.plugins.InterceptPoint;
import org.restheart.plugins.MongoInterceptor;
import org.restheart.plugins.OnInit;
import org.restheart.plugins.RegisterPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Andrea Di Cesare <andrea@softinstigate.com>
 */
@RegisterPlugin(name = "emailVerificationSender", description = "sends the email to verify email address of registered user", interceptPoint = InterceptPoint.RESPONSE_ASYNC)
public class EmailVerificationSender implements MongoInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailVerificationSender.class);

    String from;
    String fromName;
    String smptUsername;
    String smtpPassword;
    String host;
    int port;
    String verifierSrvUrl;

    @Inject("config")
    private Map<String, Object> config;

    @OnInit
    public void init() {
        from = arg(config, "from");
        fromName = arg(config, "from-name");
        smptUsername = arg(config, "smtp-username");
        smtpPassword = arg(config, "smtp-password");
        host = arg(config, "host");
        port = arg(config, "port");
        verifierSrvUrl = arg(config, "verifier-srv-url");
    }

    @Override
    public void handle(MongoRequest request, MongoResponse response) throws Exception {
        var to = response.getDbOperationResult().getNewId().asString().getValue();

        var vcode = response.getDbOperationResult().getNewData().get("code").asString().getValue();

        send(to, vcode);
    }

    @Override
    public boolean resolve(MongoRequest request, MongoResponse response) {
        return response.getDbOperationResult() != null && response.getDbOperationResult().getNewId() != null
                && response.getDbOperationResult().getNewId().isString()
                && response.getDbOperationResult().getNewData() != null
                && response.getDbOperationResult().getNewData().isDocument()
                && response.getDbOperationResult().getNewData().asDocument().containsKey("code")
                && response.getDbOperationResult().getNewData().asDocument().get("code").isString();
    }

    static final String SUBJECT = "RESTHeart Email Verification";

    static final String BODY_TEMPLATE = String.join(System.getProperty("line.separator"),
            "<h1>Verify your email address</h1>", "<p>Hello from RESTHeart</p>",
            "<p>To complete the email verification process, click the following link: ",
            "<a href='${LINK}'>VERIFY</a>");

    private void send(String to, String vcode) throws Exception {

        // Create a Properties object to contain connection configuration information.
        Properties props = System.getProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.socketFactory.port", port);
        props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");

        // Create a Session object to represent a mail session with the specified
        // properties.
        var session = Session.getDefaultInstance(props, new javax.mail.Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smptUsername, smtpPassword);
            }
        });

        var body = BODY_TEMPLATE.replace("${LINK}", verifierSrvUrl.concat("?code=").concat(vcode).concat("&username=").concat(to));

        // Create a message with the specified information.
        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(from, fromName));
        msg.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
        msg.setSubject(SUBJECT);
        msg.setContent(body, "text/html");

        try (var transport = session.getTransport()) {
            LOGGER.debug("Sending verification email to {}", to);
            transport.connect(host, smptUsername, smtpPassword);
            transport.sendMessage(msg, msg.getAllRecipients());
            LOGGER.debug("Verification email sent to {}", to);
        } catch (Exception ex) {
            LOGGER.warn("Error sending verification email {}", to, ex);
        }
    }
}
