/*******************************************************************************
 *  Copyright 2020 Exactpro (Exactpro Systems Limited)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 ******************************************************************************/

package com.exactpro.th2.simulator.util;

import com.exactpro.th2.infra.grpc.ConnectionID;
import com.exactpro.th2.simulator.ISimulator;
import com.exactpro.th2.simulator.grpc.RuleID;
import com.exactpro.th2.simulator.rule.IRule;

import io.grpc.stub.StreamObserver;

public class ServiceUtils {

    public static void addRule(IRule rule, ConnectionID connectionID, ISimulator simulator, StreamObserver<RuleID> responseObserver) {
        responseObserver.onNext(simulator.addRule(rule, connectionID));
        responseObserver.onCompleted();
    }

}
