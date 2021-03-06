/*
 * Copyright 2015-2016 Jeeva Kandasamy (jkandasa@gmail.com)
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mycontroller.standalone.group;

import java.util.List;

import org.mycontroller.standalone.AppProperties.RESOURCE_TYPE;
import org.mycontroller.standalone.AppProperties.STATE;
import org.mycontroller.standalone.McObjectManager;
import org.mycontroller.standalone.db.DaoUtils;
import org.mycontroller.standalone.db.ResourceOperation;
import org.mycontroller.standalone.db.tables.ResourcesGroup;
import org.mycontroller.standalone.db.tables.ResourcesGroupMap;
import org.mycontroller.standalone.gateway.GatewayUtils;
import org.mycontroller.standalone.model.ResourceModel;
import org.mycontroller.standalone.rule.McRuleEngine;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Jeeva Kandasamy (jkandasa)
 * @since 0.0.2
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ResourcesGroupUtils {

    public static void turnONresourcesGroup(List<Integer> ids) {
        for (Integer id : ids) {
            changeStateResourcesGroup(id, STATE.ON);
        }
    }

    public static void turnOFFresourcesGroup(List<Integer> ids) {
        for (Integer id : ids) {
            changeStateResourcesGroup(id, STATE.OFF);
        }
    }

    public static void turnONresourcesGroup(Integer id) {
        changeStateResourcesGroup(id, STATE.ON);
    }

    public static void turnOFFresourcesGroup(Integer id) {
        changeStateResourcesGroup(id, STATE.OFF);
    }

    private static void changeStateResourcesGroup(Integer id, STATE state) {
        ResourcesGroup resourcesGroup = DaoUtils.getResourcesGroupDao().get(id);
        if (resourcesGroup.getState() == state) {
            //nothing to do just return from here
            return;
        }

        List<ResourcesGroupMap> resourcesGroupMaps = DaoUtils.getResourcesGroupMapDao().getAll(id);
        for (ResourcesGroupMap resourcesGroupMap : resourcesGroupMaps) {
            ResourceModel resourceModel = new ResourceModel(resourcesGroupMap.getResourceType(),
                    resourcesGroupMap.getResourceId());
            ResourceOperation operation = null;
            if (STATE.ON == state) {
                operation = new ResourceOperation(resourcesGroupMap.getPayloadOn());
            } else if (STATE.OFF == state) {
                operation = new ResourceOperation(resourcesGroupMap.getPayloadOff());
            } else {
                //return
                return;
            }

            if (resourceModel.getResourceType() == RESOURCE_TYPE.GATEWAY) {
                GatewayUtils.executeGatewayOperation(resourceModel, operation);
            } else {
                McObjectManager.getMcActionEngine().executeSendPayload(resourceModel, operation);
            }
        }

        //Update status in group table
        resourcesGroup.setState(state);
        resourcesGroup.setStateSince(System.currentTimeMillis());
        DaoUtils.getResourcesGroupDao().update(resourcesGroup);

        //Execute Rules for this resources group
        new Thread(new McRuleEngine(RESOURCE_TYPE.RESOURCES_GROUP, resourcesGroup.getId())).start();

        //TODO: add it in to log message
    }

    public static void executeResourceGroupsOperation(ResourceModel resourceModel, ResourceOperation operation) {
        switch (operation.getOperationType()) {
            case ON:
                turnONresourcesGroup(resourceModel.getResourceId());
                break;
            case OFF:
                turnOFFresourcesGroup(resourceModel.getResourceId());
                break;
            default:
                _logger.warn("ResourcesGroup not support for this operation!:[{}]",
                        operation.getOperationType().getText());
                break;
        }
    }

}
