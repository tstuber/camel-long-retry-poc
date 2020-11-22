/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.acme.http;

import javax.enterprise.context.ApplicationScoped;

import org.apache.camel.*;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;

@ApplicationScoped
public class CamelRoute extends EndpointRouteBuilder {

    @Override
    public void configure() throws Exception {

        /*
        // Error handler with Redelivery (without using DeadLetterChannel)
        errorHandler(defaultErrorHandler()
                .maximumRedeliveries(2)
                .redeliveryDelay(1000)
                .retryAttemptedLogLevel(LoggingLevel.WARN)
                .logExhaustedMessageHistory(false));
        
         */

        // Error handler using a dead letter channel with automatic recovery (see recovery route)
        errorHandler(
                // deadLetterChannel("log:dead?level=ERROR")
                deadLetterChannel(
                        aws2Sqs("{{aws.sqs.arn}}").accessKey("{{aws.access-key}}")
                                .secretKey("{{aws.secret-key}}").region("{{aws.region}}").getUri())
                                        .logExhaustedMessageHistory(true)
                                        // Set some additional message headers
                                        .onPrepareFailure(new Processor() {
                                            @java.lang.Override
                                            public void process(Exchange exchange) throws Exception {
                                                Exception e = exchange.getProperty(Exchange.EXCEPTION_CAUGHT,
                                                        Exception.class);
                                                String failure = "The message failed because " + e.getMessage();
                                                exchange.getIn().setHeader("FailureMessage", failure);

                                            }
                                        }));

        // Rest Trigger to simulate customer call
        from(platformHttp("/camel/hello")).routeId("tst-failing-route")
                .to(direct("processing"));

        // Default processing route
        from(direct("processing")).routeId("tst-processing-route")
                .setBody().simple("Camel runs on ${hostname}")
                .to(log("processing").showExchangePattern(false).showBodyType(false))
                .to(http("does.not.exist.com"));

        // Recovery route
        from(aws2Sqs("{{aws.sqs.arn}}").accessKey("{{aws.access-key}}")
                .secretKey("{{aws.secret-key}}").region("{{aws.region}}").delay("{{aws.sqs.delay}}").greedy(true)
                .maxMessagesPerPoll(1))
                        .to(log("Retry").showExchangePattern(false).showBodyType(false));
        // Uncomment to simulate "endless" loop
        //.to(direct("processing"));

    }
}
