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
package io.fabric8.jube.model;

import java.io.IOException;
import io.fabric8.jube.local.EntityListener;
import io.fabric8.jube.local.EntityListenerList;
import io.fabric8.kubernetes.api.KubernetesHelper;
import io.fabric8.utils.Objects;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.TreeCache;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;
import org.apache.curator.framework.recipes.cache.TreeCacheListener;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mirrors ZooKeeper data into a local in memory model and all updates to the model are written directly to ZooKeeper
 */
public class ZkCacheModel<T> {
    private static final transient Logger LOG = LoggerFactory.getLogger(ZkCacheModel.class);

    private final CuratorFramework curator;

    private final TreeCacheListener treeListener = new TreeCacheListener() {
        @Override
        public void childEvent(CuratorFramework curatorFramework, TreeCacheEvent event) throws Exception {
            treeCacheEvent(event);
        }
    };

    private final TreeCache treeCache;
    private final String zkPath;
    private final EntityModel<T> entityModel;

    private final EntityListenerList<T> entityListeners = new EntityListenerList<>();
    private CreateMode createMode = CreateMode.PERSISTENT;

    public ZkCacheModel(CuratorFramework curator, String zkPath, EntityModel<T> entityModel) throws Exception {
        this.curator = curator;
        this.zkPath = zkPath;
        this.entityModel = entityModel;
        if (curator.checkExists().forPath(zkPath) == null) {
            curator.create().creatingParentsIfNeeded().forPath(zkPath, new byte[]{});
        }
        this.treeCache = new TreeCache(curator, zkPath);
        this.treeCache.start();
        this.treeCache.getListenable().addListener(treeListener);
    }

    public void addEntityListener(EntityListener<T> listener) {
        entityListeners.addListener(listener);
    }

    public void removeEntityListener(EntityListener<T> listener) {
        entityListeners.removeListener(listener);
    }


    public void write(T entity) {
        String id = getMandatoryId(entity);
        String path = zkPathForEntity(id);
        doWriteEntity(path, entity);
    }

    protected String getMandatoryId(T entity) {
        String id = entityModel.getId(entity);
        Objects.notNull(id, "id for entity " + entity);
        return id;
    }

    public void deleteById(String id) {
        String path = zkPathForEntity(id);
        doDeleteEntity(path);
    }

    public void delete(T entity) {
        String id = getMandatoryId(entity);
        deleteById(id);
    }


    // Implementation methods
    //-------------------------------------------------------------------------

    protected CreateMode getCreateMode() {
        return createMode;
    }

    protected void setCreateMode(CreateMode createMode) {
        this.createMode = createMode;
    }

    protected EntityModel<T> getEntityModel() {
        return entityModel;
    }

    protected String zkPathForEntity(String id) {
        return zkPath + "/" + id;
    }

    protected void doWriteEntity(String path, Object entity) {
        try {
            String json = KubernetesHelper.toJson(entity);
            System.out.println("Writing to path: " + path + " createMode: " + createMode + " json: " + json);
            if (curator.checkExists().forPath(path) == null) {
                curator.create().withMode(createMode).forPath(path, json.getBytes());
            } else {
                curator.setData().forPath(path, json.getBytes());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to update object at path: " + path + ". " + e, e);
        }
    }

    protected void doDeleteEntity(String path) {
        try {
            Stat stat = curator.checkExists().forPath(path);
            if (stat != null) {
                curator.delete().deletingChildrenIfNeeded().forPath(path);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to delete object at path: " + path + ". " + e, e);
        }
    }

    protected void treeCacheEvent(TreeCacheEvent event) {
        ChildData childData = event.getData();
        if (childData == null) {
            return;
        }
        String path = childData.getPath();
        TreeCacheEvent.Type type = event.getType();
        byte[] data = childData.getData();
        if (data == null || data.length == 0 || path == null) {
            return;
        }
        if (path.startsWith(zkPath)) {
            path = path.substring(zkPath.length());
        }
        String id = path;
        if (id == null || id.isEmpty()) {
         //we just want to ignore events on the 'root' node"
         return;
        }
        boolean remove = false;
        switch (type) {
        case NODE_ADDED:
        case NODE_UPDATED:
            break;
        case NODE_REMOVED:
            remove = true;
            break;
        default:
            return;
        }
        if (remove) {
            T entity = entityModel.deleteEntity(id);
            entityListeners.entityDeleted(id, entity);
        } else {
            try {
                T entity = entityModel.updateEntity(id, data);
                entityListeners.entityChanged(id, entity);
            } catch (IOException e) {
                LOG.warn("Failed to unmarshall entity " + id + " and update the model! " + e, e);
            }
        }
    }
}
