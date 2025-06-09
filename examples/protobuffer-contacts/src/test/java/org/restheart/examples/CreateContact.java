/*-
 * ========================LICENSE_START=================================
 * protobuffer-contacts
 * %%
 * Copyright (C) 2014 - 2025 SoftInstigate
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

package org.restheart.examples;

import java.io.IOException;
import com.google.protobuf.InvalidProtocolBufferException;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;

public class CreateContact {
    public static void main(String[] args) throws Exception  {
        if (args.length < 3) {
            System.out.print("""
                Needs 3 arguments: <name> <email> <phone>
                             """);
            System.exit(-1);
        }

        createContact(args[0], args[1], args[2]);
    }

    public static void createContact(String name, String email, String phone) throws UnirestException, InvalidProtocolBufferException, IOException  {
        var body = ContactPostRequest.newBuilder()
            .setName(name)
            .setEmail(email)
            .setPhone(phone)
            .build();

        var resp = Unirest.post("http://localhost:8080/proto")
                .header("Content-Type", "application/protobuf")
                .body(body.toByteArray())
                .asBytes();

        System.out.println("response status: " + resp.getStatus());

        try {
            var reply = ContactPostReply.parseFrom(resp.getBody());
            System.out.println("id of new contact: " + reply.getId());
        } catch(InvalidProtocolBufferException e) {
            System.out.println("error parsing response: " + e.getMessage());
        }
    }
}
