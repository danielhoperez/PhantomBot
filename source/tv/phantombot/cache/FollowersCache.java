/*
 * Copyright (C) 2016-2021 phantombot.github.io/PhantomBot
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package tv.phantombot.cache;

import com.gmt2001.TwitchAPIv5;
import com.gmt2001.datastore.DataStore;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import tv.phantombot.PhantomBot;
import tv.phantombot.event.EventBus;
import tv.phantombot.event.twitch.follower.TwitchFollowEvent;
import tv.phantombot.event.twitch.follower.TwitchFollowsInitializedEvent;

public class FollowersCache implements Runnable {

    private static final Map<String, FollowersCache> instances = new HashMap<String, FollowersCache>();
    private final Thread updateThread;
    private final String channelName;
    private Date timeoutExpire = new Date();
    private Date lastFail = new Date();
    private boolean firstUpdate = true;
    private boolean killed = false;
    private int numfail = 0;

    /*
     * @function instance
     *
     * @param  {String} channelName
     * @return {Object}
     */
    public static FollowersCache instance(String channelName) {
        FollowersCache instance = instances.get(channelName);

        if (instance == null) {
            instance = new FollowersCache(channelName);
            instances.put(channelName, instance);
        }
        return instance;
    }

    /*
     * @function FollowersCache
     *
     * @param {String} channelName
     */
    @SuppressWarnings("CallToThreadStartDuringObjectConstruction")
    private FollowersCache(String channelName) {
        this.updateThread = new Thread(this, "tv.phantombot.cache.FollowersCache");
        this.channelName = channelName;

        this.updateThread.setUncaughtExceptionHandler(com.gmt2001.UncaughtExceptionHandler.instance());
        Thread.setDefaultUncaughtExceptionHandler(com.gmt2001.UncaughtExceptionHandler.instance());

        this.updateThread.start();
    }

    @Override
    @SuppressWarnings("SleepWhileInLoop")
    public void run() {
        try {
            Thread.sleep(20 * 1000);
        } catch (InterruptedException ex) {
            com.gmt2001.Console.err.println("FollowersCache.run: Failed to initial sleep [InterruptedException]: " + ex.getMessage());
        }

        while (!killed) {
            try {
                if (new Date().after(timeoutExpire)) {
                    updateCache();
                }
            } catch (Exception ex) {
                checkLastFail();
                com.gmt2001.Console.err.printStackTrace(ex);
            }

            try {
                Thread.sleep(30 * 1000);
            } catch (InterruptedException ex) {
                com.gmt2001.Console.err.println("FollowersCache.run: Failed to sleep [InterruptedException]: " + ex.getMessage());
            }
        }
    }

    /*
     * @function updateCache
     */
    private void updateCache() throws Exception {
        com.gmt2001.Console.debug.println("FollowersCache::updateCache");
        DataStore datastore = PhantomBot.instance().getDataStore();

        JSONObject jsonObject = TwitchAPIv5.instance().GetChannelFollows(this.channelName, 100, null);

        if (jsonObject.getBoolean("_success")) {
            if (jsonObject.getInt("_http") == 200) {

                JSONArray jsonArray = jsonObject.getJSONArray("follows");

                for (int i = 0; i < jsonArray.length(); i++) {
                    String follower = jsonArray.getJSONObject(i).getJSONObject("user").getString("name").toLowerCase();
                    String followDate = jsonArray.getJSONObject(i).getString("created_at");

                    if (!datastore.exists("followed", follower)) {
                        EventBus.instance().post(new TwitchFollowEvent(follower, followDate));
                        datastore.set("followed", follower, "true");
                    }

                    if (!datastore.exists("followedDate", follower)) {
                        datastore.set("followedDate", follower, followDate);
                    }
                }
            } else {
                throw new Exception("[HTTPErrorException] HTTP " + jsonObject.getInt("_http") + " " + jsonObject.getString("error") + ". req="
                        + jsonObject.getString("_type") + " " + jsonObject.getString("_url") + " " + jsonObject.getString("_post") + "  "
                        + (jsonObject.has("message") && !jsonObject.isNull("message") ? "message=" + jsonObject.getString("message") : "content=" + jsonObject.getString("_content")));
            }
        } else if (!jsonObject.getString("_exception").isBlank()) {
            throw new Exception("[" + jsonObject.getString("_exception") + "] " + jsonObject.getString("_exceptionMessage"));
        }

        if (!killed && firstUpdate) {
            firstUpdate = false;
            EventBus.instance().post(new TwitchFollowsInitializedEvent());
        }
    }

    /*
     * @function checkLastFail
     */
    private void checkLastFail() {
        Calendar cal = Calendar.getInstance();
        numfail = (lastFail.after(new Date()) ? numfail + 1 : 1);

        cal.add(Calendar.MINUTE, 1);
        lastFail = cal.getTime();

        if (numfail > 5) {
            timeoutExpire = cal.getTime();
        }
    }

    /*
     * @function kill
     */
    public void kill() {
        this.killed = true;
    }

    /*
     * @function killall
     */
    public static void killall() {
        instances.values().forEach(instance -> {
            instance.kill();
        });
    }
}
