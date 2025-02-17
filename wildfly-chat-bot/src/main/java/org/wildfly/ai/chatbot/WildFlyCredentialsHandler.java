/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.ai.chatbot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 *
 * @author jdenise
 */
public class WildFlyCredentialsHandler {

    public static class UserPassword {
        public String host;
        public String port;
        public String name;
        public String password;
    }
    private Map<String, List<UserPassword>> users = new HashMap<>();

    public List<UserPassword> getUser(String host, String port) {
        String key = (host == null ? "" : host) + (port == null ? "" : port);
        return users.get(key);
    }

    public void addUser(UserPassword user) {
        String key = (user.host == null ? "" : user.host) + (user.port == null ? "" : user.port);
        List<UserPassword> lst = users.computeIfAbsent(key, (k -> new ArrayList<>()));
        lst.add(user);
    }
    
    public boolean removeUser(UserPassword user) {
        String key = (user.host == null ? "" : user.host) + (user.port == null ? "" : user.port);
        boolean removed = false;
        List<UserPassword> lst = users.get(key);
        if (lst != null) {
            Iterator<UserPassword> it = lst.iterator();
            while (it.hasNext()) {
                UserPassword up = it.next();
                if (up.name.equals(user.name)) {
                    it.remove();
                    removed = true;
                    break;
                }
            }
        }
        return removed;
    }

    public Collection<UserPassword> getUsers() {
        List<UserPassword> lst = new ArrayList<>();
        for(List<UserPassword> users : users.values()) {
            lst.addAll(users);
        }
        return lst;
    }
}
