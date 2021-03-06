/**
 *  Copyright 2005-2014 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package io.fabric8.jube.local;

import io.fabric8.kubernetes.api.model.Pod;

/**
 * Event listener for entity events.
 */
public interface EntityListener<T> {

    /**
     * Invoked when an entity has changed (either created or updated)
     */
    void entityChanged(String id, T entity);

    /**
     * Invoked when an entity is deleted
     */
    void entityDeleted(String id, T entity);
}
